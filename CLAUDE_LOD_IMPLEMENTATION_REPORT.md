# Farlands LOD — Native Continuous LOD Foundation: Implementation Report

Project: Pryzma 2.1.4 (Minecraft 1.21.1 / NeoForge 21.1.250, Java 21)
Session date: 2026-10-10
Scope: tasks 1–6 of the "Native Continuous LOD (Farlands) Foundation" directive.

---

## 0. Verification status — read this first

**`./gradlew compileJava --no-daemon` and `./gradlew test --no-daemon` were NOT executed in this session.**

The session ran non-interactively with a permission policy that auto-approves only read-only shell commands.
Every attempt to run Gradle, `java`, `unzip`, or `mkdir` was denied ("This command requires approval"), both from
PowerShell and from Bash. No compiler output and no test results exist for this change set. Nothing in this report
claims otherwise.

What was done instead:

* **Every external API call was checked against the NeoForge 21.1.250 sources** extracted inside the repository
  (`scratch/titlescreen/neosrc`, 6317 files): signatures, visibility, checked exceptions, return types, and the
  event-firing sites (see §6).
* **Every new and modified file was re-read line by line** for compile errors: imports, generics, lambda
  capture rules, definite assignment, switch exhaustiveness, overload resolution of every `assertEquals` call.
* **Every test expectation was derived by hand** and cross-checked against the production code (§7).

To verify, run from the repository root:

```
./gradlew compileJava --no-daemon
./gradlew test --no-daemon
```

To let a future agent run these, approve `Bash(./gradlew:*)` / `PowerShell(.\gradlew.bat *)` in
`.claude/settings.json`, or pass `--allowedTools` to the CLI invocation in `run_claude_lod_implementation.ps1`
(`--permission-mode acceptEdits` approves file edits only, not shell commands).

---

## 1. Starting state

`src/main/java/net/pryzma/lod/` already held 13 files, written 2026-10-09 23:40–23:50 by an earlier run that stopped
before integrating them. Nothing outside the package referenced it, and it could not compile:

| Gap | Effect |
| --- | --- |
| `PryzmaConfig.prFarlandsDistance` did not exist | compile error in `PryzmaLod` |
| `PrRenderHooks.isClearAir` was `private` | compile error in `PryzmaLod` |
| `pryzma:lod_terrain` core shader absent | `ShaderInstance` load failure → LOD never draws |
| `PryzmaLod.register` never called | no events, no LOD |
| No UI option, no lang keys, no fog hook, no F3 line, no reload hook | — |
| No tests | — |

The last successful compile (`build/classes`, 2026-10-09 18:07) predates the package.

This session reviewed the existing package in depth, kept its architecture, fixed four defects (§4), and built the
missing integration, shader, UI, localisation and tests.

---

## 2. Architecture

```
Chunk load (client)   ──►  PENDING_LOADS (40-tick delay, so the light has arrived)  ──┐
Chunk unload (client) ─────────────────────────────────────────────────────────────────┤
                                                                                       ▼
                                    LodWorldScanner.capture  (client thread: heightmap, blocks, light, biome)
Saved world (SP only) ──► LodStorageIntake ── ChunkMap.read (IO thread) ── decode (LOD worker)
                                                                                       ▼
                                    Finisher.finish  (LOD worker: block ids, biome tints)
                                                                                       ▼
                                    LodDataStore  (RW-locked, keyed by chunk, versioned per 16×16-chunk sector)
                                                                                       ▼
                     PryzmaLod.schedule  (per tick: sectors whose data / level / palette changed, nearest first)
                                                                                       ▼
                                    NativeLodMesher  (LOD worker: heap ByteBuffer of quads)
                                                                                       ▼
                     NativeLodRenderer  (render thread: one VAO+VBO per sector, drawn at AFTER_SKY)
```

* **Threads.** Client thread: chunk events, capture, scheduling, uploads, drawing. `Pryzma-LOD-Worker-N`
  (`min(4, cores/4)` daemon threads, priority NORM−2): tinting, NBT decoding, meshing. IO worker: saved-chunk reads.
  Workers never touch GL or live chunks; captured data is immutable once published.
* **Memory.** Plain heap structures as directed: `Long2ObjectOpenHashMap<LodChunkData>`, primitive arrays,
  heap `ByteBuffer` meshes, one reusable direct staging buffer for uploads. No off-heap pools.

---

## 3. Task-by-task

### Task 1 — Configuration & UI

* `PryzmaConfig.prFarlandsDistance` (default **0 = Off**), `FARLANDS_DISTANCE_VALUES = {0, 32, 64, 96, 128}`;
  read with the existing `oneOf` (unknown values → Off), written by `snapshot()` → persisted to `optionspr.txt`
  by the existing `PrScreen.removed()` save, reset by `resetToDefaults()`.
* `PrOptions.FARLANDS_DISTANCE` — cycle button "Farlands LOD: Off / 32 chunks / …" (value text from vanilla's
  `options.chunks`, so it localises everywhere); click steps forward, Shift steps back (new `PrOptions.stepValue`);
  calls `PryzmaLod.onSettingsChanged()` so turning it Off frees everything immediately.
* **Placement: Video Settings → Performance**, in the page's one free cell, next to Detail Distance (the decorator
  LOD). The main Video Settings grid is the 1.x layout with all 12 cells used, and its page buttons and Done
  button already reach the bottom of a 240-px-high GUI; a 7th row would push Done off-screen at the largest GUI
  scale.
* Tooltips (`pr.options.FARLANDS_DISTANCE` + 6 lines) in all 40 locale files that carry `DETAIL_DISTANCE`,
  following its precedent: translated for de/es (×6)/fr (×2)/ja/pl/ru/uk/zh (×3), English in the rest.

### Task 2 — LOD data structure & cache

* `LodChunkData`: 16×16 columns with `short[] heightSolid`, `short[] heightFluid`, `int[] blockStateId`,
  `int[] fluidStateId`, `int[] solidTint`, `int[] fluidTint`, `byte[] skyLight`, `byte[] blockLight`, plus a
  capture `sequence` so an older capture never overwrites a newer one (saved-world reads use 0).
* `LodDataStore`: `ReentrantReadWriteLock` over fastutil maps; `put/get/remove/contains`, `queryRect`,
  `queryRadius` (Euclidean), `evictOutside`, `snapshotSector` (sector + one-chunk border, read atomically), and a
  per-sector version that also bumps the neighbour sector when a chunk on the shared edge changes (that
  neighbour's skirts read it).

### Task 3 — World scanner

* Loaded chunks: `MOTION_BLOCKING` heightmap → top block; a fluid top is followed down (≤ 64 blocks) to its
  floor; snow layers colour the column; sky/block light read just above the surface; biome at the surface.
* Captured 40 ticks after `ChunkEvent.Load` (the event fires before the chunk's light is applied) and again on
  `ChunkEvent.Unload` (fired before the chunk and its light are dropped), so the LOD keeps the last state.
* Saved chunks (singleplayer): `LodStorageIntake` walks a nearest-first spiral, reads through the integrated
  server's `ChunkMap.read` (region files or its pending-write queue), and decodes Anvil NBT entry-by-entry from the
  packed arrays (`LodPalette`: PalettedContainer / SimpleBitStorage / Heightmap / DataLayer layouts).

### Task 4 — Adaptive mesher

* Sector = 16×16 chunks (256×256 blocks). Detail level by distance (0 < 256 b ≤ 1 < 512 ≤ 2 < 1024 ≤ 3) with
  32-block hysteresis; level L meshes cells of 2^L×2^L columns, each taking its highest column.
* **Quad merging**: greedy rectangles over cells of equal height, colour and light.
* **Skirts**: every cell edge with a lower neighbour gets a wall from its top down to the neighbour; runs of equal
  walls along an edge merge. Across a sector edge the wall goes down to the *lowest full-resolution* column of
  the neighbour, which no level of the neighbour can undercut (cells take maxima) — sectors at different levels
  cannot crack. Missing neighbours get a 16-block skirt.
* **Vertex layout** (24 B): `float x,y,z` | `ubyte r,g,b,a` | `short u,v` (vanilla UV2: block×16, sky×16) |
  `byte nx,ny,nz` + pad. Face shade (1.0 / 0.8 / 0.6) baked into colour, as vanilla terrain does.

### Task 5 — Render pass

* `NativeLodRenderer` draws at `RenderLevelStageEvent.Stage.AFTER_SKY` (inside `LevelRenderer.renderLevel`,
  right after the sky, before terrain fog setup and the terrain).
* Binds per-sector VAO/VBO, the shared sequential quad index buffer, `ModelViewMat` (camera rotation),
  `ProjMat`, `ChunkOffset` (sector origin − camera, computed in double), the lightmap on `Sampler2`.
* **Depth:** `GL_LEQUAL`, depth write on, back-face culling. The projection keeps vanilla's x/y rows
  (FOV, zoom, bobbing, nausea — pixel-exact alignment) with only its depth range moved to
  `[clipRadius/8, farPlane]` (`LodMath.withDepthRange`). After the pass the depth buffer is cleared: vanilla
  terrain and everything later draws over the LOD exactly as over the sky. LOD and vanilla depths never meet in
  one test, so z-fighting between them is impossible. Fragments horizontally inside the vanilla radius
  (`(renderDistance−1)×16`) are discarded by the shader.
* **Fog:** while LOD geometry exists beyond the vanilla radius, `PrRenderHooks.onRenderFog` moves vanilla terrain
  fog out to the LOD radius, and the LOD shader applies the same cylindrical linear fog per fragment.
* Skipped under a shader pack (the pack's G-buffers expect its own programs; data and meshes stay current),
  underwater/lava/powder snow, blindness/darkness, foggy biomes, boss fog, and when the LOD radius does not
  exceed the render distance.

### Task 6 — Compilation & verification

See §0. Not executed; replaced by source-level verification (§6) and hand-derived test expectations (§7).

---

## 4. Defects found in the pre-existing package and fixed

| # | Where | Defect | Fix |
| --- | --- | --- | --- |
| 1 | `LodStorageIntake` | Every *successfully read* chunk was marked "finished" for the session. After `evictOutside` dropped it from the store, it was never re-read — permanent LOD holes after travelling away and back. | Only chunks with nothing usable go into `missing`; stored chunks are skipped while the store holds them and re-read once evicted. |
| 2 | `LodStorageIntake` | Reads still in flight across `reset()` (dimension change) reported into the next session's bookkeeping. | Session counter; stale results are ignored. |
| 3 | `NativeLodRenderer.render` | Returned "active" whenever any mesh existed, even if all lay inside the vanilla radius → fog moved out with nothing beyond → hard vanilla edge against the sky right after joining. | Active only if some mesh lies beyond the clip radius. |
| 4 | `LodMath.insideClip` | CPU cull also required the sector to be vertically within the clip radius, unlike the shader's cylindrical discard (needless draws when looking down from height). | Purely horizontal, matching the shader. |

Refactors: `spiral` moved to `LodMath` (pure, testable, bounded radius); the GL vertex format moved to
`LodVertexFormat` (no GL calls, loadable in plain JUnit) so a test pins it to the mesher's byte layout.

---

## 5. Files

### Created (this session)

| File | Purpose |
| --- | --- |
| `src/main/java/net/pryzma/lod/LodVertexFormat.java` | GL vertex format (Position, Color, UV2, Normal, 1 B pad = 24 B). |
| `src/main/resources/assets/pryzma/shaders/core/lod_terrain.json` | Core shader definition: `Sampler2`; uniforms ModelViewMat, ProjMat, ChunkOffset, ColorModulator, FogStart, FogEnd, FogColor, ClipRadius. |
| `src/main/resources/assets/pryzma/shaders/core/lod_terrain.vsh` | GLSL 150: camera-relative position, lightmap × vertex colour. |
| `src/main/resources/assets/pryzma/shaders/core/lod_terrain.fsh` | GLSL 150: cylindrical clip discard, per-fragment cylindrical linear fog. |
| `src/test/java/net/pryzma/lod/NativeLodMesherTest.java` | 11 tests incl. a full geometric sealing checker (§7). |
| `src/test/java/net/pryzma/lod/LodDataStoreTest.java` | 10 tests incl. concurrent writers. |
| `src/test/java/net/pryzma/lod/LodMathTest.java` | 9 tests. |
| `src/test/java/net/pryzma/lod/LodPaletteTest.java` | 5 tests. |
| `src/test/java/net/pryzma/lod/LodChunkDataTest.java` | 5 tests. |
| `src/test/java/net/pryzma/lod/LodColorsTest.java` | 3 tests. |
| `src/test/java/net/pryzma/lod/LodVertexFormatTest.java` | 2 tests: format ↔ mesher layout, shader JSON ↔ GLSL ↔ format. |
| `CLAUDE_LOD_IMPLEMENTATION_REPORT.md` | This report. |

### Pre-existing LOD package (reviewed in full; modified where noted)

| File | Role | This session |
| --- | --- | --- |
| `PryzmaLod.java` | Wiring, scheduling, workers, F3 line, fog values | reviewed |
| `LodChunkData.java` | Column data of one chunk | reviewed |
| `LodDataStore.java` | Thread-safe store, sector versions, queries | reviewed |
| `LodWorldScanner.java` | Live capture, NBT decode, tinting | reviewed |
| `LodStorageIntake.java` | Saved-world reads | **fixed** (#1, #2), uses `LodMath.spiral` |
| `NativeLodMesher.java` | Mesher | reviewed |
| `NativeLodRenderer.java` | GL pass | **fixed** (#3), `LodVertexFormat`, horizontal clip |
| `LodMath.java` | Levels, planes, fog, distances | **fixed** (#4), `spiral` moved in, doc corrected |
| `LodBlockColors.java` | Texture-average palette, tint indices | reviewed |
| `LodPalette.java`, `LodColors.java`, `LodColorSource.java`, `LodMesh.java` | Helpers | reviewed |

### Modified outside the package

| File | Change |
| --- | --- |
| `PryzmaConfig.java` | `prFarlandsDistance`, `FARLANDS_DISTANCE_VALUES`; apply / snapshot / reset. |
| `PryzmaClient.java` | `PryzmaLod.register(modBus, gameBus)`. |
| `gui/PrOptions.java` | `FARLANDS_DISTANCE`, `stepValue`. |
| `gui/PrSettingsScreen.java` | Performance page cell 12 → `FARLANDS_DISTANCE`. |
| `gui/PrDebugOverlay.java` | F3 line under the particle line (`insertAfterCounts`). |
| `render/PrRenderHooks.java` | LOD fog branch; `isClearAir` made public. |
| `core/PrReloadListener.java` | `PryzmaLod.onResourcesReloaded()` (palette rebuild after resource reload). |
| `assets/pryzma/lang/*.json` (40 files) | 7 keys each. |
| `src/test/java/net/pryzma/PryzmaConfigTest.java` | +1 test. |
| `src/test/java/net/pryzma/gui/PrDebugOverlayTest.java` | +1 test. |

---

## 6. API verification against NeoForge 21.1.250 sources

Checked by reading `scratch/titlescreen/neosrc` (complete 21.1.250 source tree):

* `LevelRenderer.renderLevel`: order is sky → `AFTER_SKY` dispatch → `FogRenderer.setupFog(FOG_TERRAIN)` → terrain.
  Fog colour after `renderSky` is `levelFogColor`; depth mask restored to true. Pryzma's custom sky layers draw
  inside `renderSky`, i.e. before the LOD.
* `GameRenderer.renderLevel`: projection = zoom·perspective·bob·nausea; model-view = camera rotation; far plane =
  4 × render distance (why the LOD needs its own depth range).
* `ClientHooks.onFogRender`: near/far are applied only if the `ViewportEvent.RenderFog` is canceled.
* `ShaderInstance(ResourceProvider, ResourceLocation, VertexFormat)`: reads only `vertex`, `fragment`, `samplers`,
  `uniforms`; binds attributes by format name/index; program names `pryzma:lod_terrain` resolve to
  `pryzma:shaders/core/lod_terrain.{vsh,fsh}`; `setDefaultUniforms` copies `RenderSystem` shader textures into
  `SamplerN`. Pryzma's shader-pack mixins on `ShaderInstance` are inert without an active pack.
* `RenderSystem.AutoStorageIndexBuffer`: grows in place (same buffer name); index type must be read after `bind`
  — done, as Pryzma's `VboRegion` does.
* `VertexFormatElement`: UV2 = SHORT×2 via `glVertexAttribIPointer` → `ivec2 UV2` in GLSL.
* `ClientChunkCache`: `ChunkEvent.Load` after packet data, before light; `ChunkEvent.Unload` before the chunk is
  dropped. `ChunkSource.getChunk(int,int,boolean)` → `LevelChunk`.
* `ChunkAccess.getHeight(type,x,z)` = top block y; `getNoiseBiome` takes quart coords (x/z masked).
* `ServerChunkCache.chunkMap` public final; `ChunkStorage.read(ChunkPos)` public, IO-worker mailbox (thread-safe).
* Saved layout: `Status` `minecraft:full`; heightmap bits `ceillog2(height+1)`; block bits
  `max(4, ceillog2(n))`, biome bits `ceillog2(n)`; values never straddle longs; `DataLayer` low nibble first.
* `BlockAndTintGetter` + NeoForge extensions: only `getShade`, `getLightEngine`, `getBlockTint`,
  `getBlockEntity`, `getBlockState`, `getFluidState`, `getHeight`, `getMinBuildHeight` are abstract — all
  implemented by `LodWorldScanner.TintGetter`.
* `NativeImage.getPixelRGBA` returns ABGR; `SpriteContents.getOriginalImage()` is public.

---

## 7. Tests (written, not run)

47 new tests: 45 in `net.pryzma.lod` plus 1 each in `PryzmaConfigTest` and `PrDebugOverlayTest`. All target
pure classes or GL-free data (no game bootstrap needed).

* **`NativeLodMesherTest.assertSealedSurface`** derives the expected surface from column heights alone and
  checks the mesh face by face, at every level:
  every non-empty cell's top covered by exactly one top quad at its height and colour (merging never joins unequal
  cells, never overlaps); every cell edge with a lower neighbour carries exactly one wall from the cell's top
  exactly down to the neighbour (sector edges: lowest neighbouring column; void: 16 blocks), and no other walls
  exist; both triangles of every quad wind outward along the stored normal; positions are whole blocks; colour,
  light and normal are uniform per quad; mesh y-bounds equal the vertices'. Run on flat, stepped, plateau,
  material-border, single-peak, varying-border and seeded random terrain with missing chunks and void columns.
* Exact counts: flat → 1 quad; isolated sector → 5; step → 3; plateaus → 48; level-3 peak → 7.
* Vertex bytes at documented offsets (position, RGBA, UV2 = block×16/sky×16, normal, padding).
* `LodDataStore`: sequence ordering, border-aware version bumps (incl. negative coordinates, no diagonal
  bumps), sector counts, snapshot extent, rect/disc queries (13 chunks within radius 2), eviction, and four
  concurrent writers + a concurrent snapshot reader, ending with the exact newest sequence per chunk.
* `LodMath`: level hysteresis edges, planes, fog, distances, depth-range rebuild equal (≤1e-4 relative) to a JOML
  perspective with the new planes under zoom·bob·nausea, same screen x/y for a test point, orthographic
  passthrough, spiral order/coverage/uniqueness.
* `LodPalette`: bit widths (incl. 37 longs for a 384-high heightmap, 342 for 5-bit sections), SimpleBitStorage
  round trip for 1–31 bits, nibble order, index orders.
* `LodVertexFormatTest`: GL format offsets/stride/names equal the mesher's; every JSON uniform declared in GLSL with
  matching value counts; `Sampler2`, attributes and varyings agree.

Existing tests: none reference the modified classes except `PryzmaConfigTest` and `PrDebugOverlayTest`, which
only gained tests. `PrDebugOverlay` now references `PryzmaLod` only inside `left()`, which the tests do not call.

---

## 8. In-game check (when it can be run)

1. Video Settings → Performance → **Farlands LOD** → 64 chunks. Render distance 12.
2. F3: line `Farlands: 64 chunks, data N, sectors drawn/total, K quads, MB, jobs, reads` under the `P:` line.
3. Expect terrain beyond the render distance, fog at ~1024 blocks, no seam or z-fighting where vanilla begins.
4. Turn it Off: the F3 line disappears, GPU memory on the F3 `GPU:` line drops.

---

## 9. Known limitations

* **Multiplayer**: only chunks the client has loaded are captured; there is no server-side LOD stream.
* **Clouds/translucents vs LOD**: the depth clear means clouds and particles after the pass do not test against
  LOD depth; a cloud behind a distant LOD peak draws over it. Rare (clouds sit at y≈192, ≤ ~400 blocks out).
* **Water** in the LOD is opaque, coloured by the water tint.
* **Custom colormaps** (OptiFine `colormap`) tint far, unloaded columns using the live level's biome lookup,
  which answers "plains" for unloaded chunks; vanilla tints use the captured biome and are correct.
* **Shader packs**: the pass is skipped; distant terrain is not part of pack G-buffers.
* **Unload capture** is synchronous; a teleport dropping hundreds of chunks costs one tick of ~tens of ms.

---

## 10. Reflection

**What am I least confident about right now?**

1. Whether it compiles. I have not seen a compiler run. Every API was read from source, but a human can misread
   an overload or a generic bound. The pre-existing package (≈2.2 k lines) was reviewed, never compiled by anyone.
2. Whether every test passes as written. Each expectation was derived by hand. The largest risk is
   `NativeLodMesherTest`'s checker diverging from the mesher in some tie-breaking rule I believed identical.
3. Visual behaviour on the RX 6950 XT: depth-range swap, fog hand-over at the vanilla edge, lightmap brightness in
   the LOD, cull winding on real hardware. None of it has been seen on screen.
4. Whether `LodVertexFormatTest` loads `VertexFormatElement` cleanly in plain JUnit (it implements a NeoForge
   `IExtensibleEnum`; should be inert outside FML, but unproven here).
5. Pryzma's own render hooks interacting with the LOD's GL state (Render Regions, dynamic lights, custom
   lightmaps). I traced the obvious ones (index buffer sharing, `BufferUploader`, GPU memory tracking); not all
   of them.

**What is the biggest thing being missed?**

* **The verification loop is broken by configuration, not by code.** `run_claude_lod_implementation.ps1` runs
  Claude with `--permission-mode acceptEdits`, which does not approve shell commands, so no agent driven by that
  script can ever execute `gradlew` — the directive's task 6 is unreachable by construction. The earlier run that
  wrote this package most likely stopped at the same wall. One allowlist entry fixes it.
* **This is a foundation, not Distant Horizons.** Heightfield LOD cannot show overhangs, caves, floating islands
  or arches; there is no multiplayer data path and no disk cache (the store is rebuilt every session from live
  chunks and the save). A persistent per-world LOD cache, and a server-side sync for multiplayer, are the next
  architectural decisions, and the depth-clear strategy will need revisiting if shader-pack support is wanted.
