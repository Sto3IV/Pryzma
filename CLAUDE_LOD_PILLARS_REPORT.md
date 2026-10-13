# Farlands LOD — Pillar 1 (tree canopy geometry) and Pillar 2 (shader-pack integration)

Project: Pryzma 2.1.4 (Minecraft 1.21.1 / NeoForge 21.1.250, Java 21), `S:\NEYRONKI\pryzma-Claude`.
Session: 2026-10-10, 21:12–22:10. Baseline for every diff below: `S:\NEYRONKI\pryzma` (the tree this one was copied
from; it differs from this tree only by the changes listed in §7).

---

## 0. The directive, and what was done with it

The directive arrived truncated after `### PILLAR 1: Tree Canopy Geometry (Зеленые` — twice: an earlier session
(16:21–17:13, transcript `2d7c8ae9`) received the same text, wrote most of the code below, and stopped at the usage
limit before any test ran (main sources compiled; the test sources did not). No full copy of the directive exists on
disk.

Interpretation:

* **Pillar 1 — tree canopy geometry ("зелёные кроны").** Forests as green crowns floating over the forest floor,
  instead of a heightfield that either ignores leaves or turns every tree into a green pillar.
* **Pillar 2 — shader-pack integration.** Not in the received text. Inferred from the session constraints ("when
  Farlands is off: zero overhead and zero changes to vanilla/**shader pipeline** execution") and from the open
  limitation of the foundation ("Shader packs: the pass is skipped"). Implemented as the Distant Horizons contract
  that Iris packs are written against, which the Iris port in `net.pryzma.shader` already half-carries.

This session audited the earlier session's work line by line, fixed what was wrong (§5), finished the tests, and
verified everything by execution (§1).

---

## 1. Verification — all of it executed

| Check | Result |
| --- | --- |
| `./gradlew compileJava test` | **BUILD SUCCESSFUL.** 43 suites, **280 tests, 0 failures, 0 errors, 0 skipped** (baseline: 41 suites, 248 tests). |
| Mutation pass (`scratch/pillars/mutate.sh`) | **7 of 7 mutants killed**, each by an assertion (0 compile errors), every source restored with its SHA-256 checked. |
| GLSL, native pass | `lod_terrain.vsh` / `.fsh` compile and link under glslangValidator 14.0 (GLSL 1.50). |
| GLSL, injected clip | The `pryzma_LodClip` cut, printed by glsl-transformer into a 330-core fragment stage, compiles under glslang. |
| In-game (`runSelftest`, RX 6950 XT, Adrenalin 26.8.1) | Native pass, Complementary (`dh_terrain`), Nostalgia (fallback) and Off all ran; no GL or LOD errors. Native LOD pass ≈ 0.12 ms/frame at 821K quads. See §1.1. |

Mutants (each must fail its test class):

| Mutant | Killed by |
| --- | --- |
| M1 canopy walls never closed below a higher neighbour | `staggeredCrownsLeaveNoSlit`, `noTwoFacesShareAPlaneAndADirection`, `denseFlatCanopy…`, `canopySlabsFollowTheCrowns…`, `forestGroundStaysFlat…` |
| M2 floorless stand-in floor tagged as water | `floorlessWaterGetsADarkStandInFloor` |
| M3 a trunk ends the descent under a crown | `treeCrownsFloatAboveTheForestFloor`, `aTreeInWaterKeeps…`, `undergrowthBelowATrunk…` |
| M4 the injected clip never discards | `DHTerrainTransformTest.theCutIsDeclaredAndRunsFirstInMain` |
| M5 shader water id differs from the mesher's | `LodVertexFormatTest.shaderMaterialConstantsAreTheMeshersMaterials` |
| M6 tree depth off by one | `foliageWithoutGroundInReachIsItselfTheGround` |
| M7 canopy wall ignores the neighbouring slab | 6 mesher tests |

M1 also failed tests the earlier session wrote for its own forests: the see-through slit it fixes was real (§5.1).

### 1.1 In-game self-test

Scripts: `run/selftest/farlands.txt` (pregeneration, all three paths, Off) and `run/selftest/farlands_perf.txt`
(timings on the generated world). Run either by copying it to `run/selftest.txt`, then `./gradlew runSelftest`.
Fresh normal world `run/saves/pryzma-farlands` (seed 20260924, 52 MB, safe to delete), render distance 12, Farlands
32 chunks, FULL pregeneration of 36 chunks around (0, 0), camera at y = 170. Screenshots:
`run/screenshots/selftest/farlands_*.png`.

| Configuration | Farlands F3 line (logged by the new `farlands` harness step) | Result |
| --- | --- | --- |
| Native pass | `data 2549, sectors 8/16, 710.5K quads, 43 MB` (pregen at 62 %) | Core shader compiled on the RX 6950 XT; forests continue past the vanilla radius as crowns, no seam visible at the hand-over. |
| Complementary Reimagined r5.8.1 | `858.2K quads, 52 MB, shader pack: dh programs` | Log: "drawn into the shader pack's G-buffer with its Distant Horizons programs". The pack lights and fogs the LOD itself; its fog follows `dhRenderDistance`. |
| Nostalgia v5.1 (no DH programs) | `943.5K quads, 57 MB, shader pack: gbuffers fallback` | "…with its terrain and water programs"; horizon silhouettes inside the pack's own fog, as designed. |
| Off | `null` | LOD stopped, F3 line gone, pack rebuilt without the contract. |

Frame times on the generated world (`farlands_perf.txt`, vsync off, same view, 400 frames each, in this order):

| Off | Native on | Off again | Complementary, option off | Complementary, option on |
| --- | --- | --- | --- | --- |
| 1.149 ms | 0.977 ms | 0.855 ms | 2.279 ms | 2.168 ms |

Warm-up dominates the order (the first Off run is the slowest of the three vanilla runs). Against the warmed Off run
the native LOD pass costs about **0.12 ms per frame** at 821K quads; under Complementary the difference is inside the
noise. The first run's frame times (~27 fps everywhere, Off included) were bound by the concurrent pregeneration
and say nothing about the LOD.

No GL errors, no shader compile or link failures, no exceptions from the LOD. Complementary's `Unknown variable:
BIOME_PALE_GARDEN / endFlashIntensity` and Nostalgia's "never assigned … compatibility-patched" warnings are the packs'
own and appear identically for their vanilla programs and in the Sep-25 runs.

**Pre-existing defect found (not fixed, outside both pillars):** quitting while `/pryzma pregen` runs freezes the
client at "Saving worlds" (reproduced: 5+ minutes, server thread at 100 % CPU, all chunk workers idle; Farlands was
already Off). Thread dump `scratch/pillars/hang_jstack.txt`: vanilla `ChunkMap.scheduleUnload` re-schedules itself
forever (`!isReadyForSaving()`, ChunkMap.java:535) for holders whose generation refs are held by generation tasks that
`PrPregenTask` started directly through `ChunkHolder.scheduleChunkGenerationTask`; at shutdown
`DistanceManager.removeTicketsOnClosing()` strips the `pryzma_pregen` tickets, the orphaned tasks never finish, and
`stopServer`'s `while (hasWork())` never ends. `ServerStoppingEvent → PrPregenTask.stopAll()` only stops new batches.
The second run, without a pregen, quit cleanly. The fix belongs to the pregen task (drain or cancel in-flight
generation before the server stops) and needs its own verification.

---

## 2. Pillar 1 — tree canopy geometry

**Data.** `LodChunkData` gained a canopy layer per column: `canopyTop`, `canopyBottom`, `canopyStateId`, `canopyTint`,
`canopyLight` (light above the crown), plus `biomeId` (tints are recomputed from it after a resource reload). The
ground below a crown is the real forest floor, with its own shaded light.

**Scan (`LodColumnScan`, pure, 14 tests).** From the `MOTION_BLOCKING` top: a foliage top is a tree — the scan descends
through foliage, trunks, air and plants (≤ 48 blocks) to the ground and reports the foliage between as the canopy
(highest block, lowest block above the ground). Water under a crown is followed to its floor (≤ 64 blocks); lava is
opaque ground; a log on top is ground (stumps, log houses); foliage without ground in reach is itself the surface.
Live capture (`LodWorldScanner.capture`), saved-world decode (`decode`) and pregeneration (`injectChunk`) share it.

**Mesh (`NativeLodMesher`).** Three layers per cell — ground, water, canopy:

* Level 0: each column's own crown, a slab of its real thickness clamped to 1..4 blocks from the top.
* Levels 1–3: a cell is canopy when foliage covers at least half of its columns; it takes one of its crowns (picked
  by a hash of the world position) and moves it by up to ±max(1, cell/4) blocks (another hash). A forest keeps its
  height spread and roughness instead of becoming a flat table at its tallest tree; isolated trees no longer raise
  coarse cells into green pillars.
* Slab sides are closed wherever the neighbouring slab does not cover them — above a lower neighbour **and below a
  higher one** — but never down to the forest floor, so a forest edge reads as crowns over open ground. Floating
  slabs get undersides; slabs never end below their cell's ground or water, so no two faces share a plane and a
  direction (tested by a rasterized coverage check over every unit square).
* Water is its own translucent pass (alpha `0xB8`) over a floor lit by its depth.

**Look (native pass).** Vertex colours are unshaded albedo × biome tint (shader packs light them themselves). The
native shader applies vanilla's face shade, vanilla's cylindrical fog per fragment, and a per-block brightness hash:
±14 % on foliage (crowns read as crowns, not painted slabs), ±3.5 % elsewhere, none on water.

**Vertex.** The Distant Horizons vertex ABI, 16 bytes: `ushort x, y+2048, z, light` | `ubyte r, g, b, a` |
`ubyte material, normal index, 0, 0`. One VBO per sector feeds both the native shader and a pack's `dh_*` programs.

---

## 3. Pillar 2 — shader packs through the Distant Horizons contract

Built only when the option is on at pack load (`LodShaderSupport.createFor` returns null otherwise). Switching the
option reloads the pack, from the option button and from "Reset Video Settings".

* `DISTANT_HORIZONS` is defined; `dhNearPlane`, `dhFarPlane`, `dhRenderDistance` describe the LOD pass;
  `dhProjection` (+ `Inverse`, `Previous`) is the game's projection with the LOD's depth range.
* **Packs with `dh_terrain`** (Complementary, Soft-Memory, …): `dh_terrain` / `dh_water` are transformed by the Iris
  port's `DHTerrainTransformer` for the LOD vertex and drawn into the pack's G-buffer — opaque after the sky, water
  after the translucent terrain — with a depth buffer of their own, exposed as `dhDepthTex0`; `dhDepthTex1` is that
  depth before the water. The main depth is never touched; vanilla terrain overwrites the LOD wherever it exists.
* **Packs without** (Nostalgia, …): `gbuffers_terrain` / `gbuffers_water` (or their fallbacks) draw the LOD into the
  G-buffer with the main depth, a white albedo, neutral normal/specular maps and a polygon offset; such packs end
  their fog at the render distance, so the LOD lends the horizon its silhouettes.
* In both, a cut injected at the top of the fragment stage (`pryzma_LodClip`) discards fragments horizontally inside
  the vanilla radius, reconstructed from `gl_FragCoord` and the inverse matrices, so it holds for any stage layout.

---

## 4. Off means off (constraint 4)

With `prFarlandsDistance == 0`:

| Hook | What runs |
| --- | --- |
| `ClientTickEvent.Post` | `tick++` and one field compare; `stop()` only on the transition. The only listener registered. |
| Chunk / level / frame / render-stage listeners | **Not registered** (added on start, removed on stop). |
| Native core shader `pryzma:lod_terrain` | **Never compiled**; built on first use, closed on stop and on resource reload. |
| `ShadedWorldRenderingPipeline` | `farlands == null`: one null check per frame, none per draw. |
| `StandardMacros`, `CommonUniforms`, `MatrixUniforms`, `ShaderSamplers` | Decided once at pack load; with the option off they register exactly the baseline macros, uniforms (same order, same `ONCE`/`PER_FRAME`) and samplers (same constant `0` suppliers). |
| `DHTerrainTransformer` | Only reached through `LodShaderSupport`; never invoked. |
| `TransformPatcher` | Byte-identical to the baseline. |
| F3 / fog / pregen hooks | `debugLine()` returns null; the fog branch reads one false boolean; `unmarkMissing` returns at once. |

---

## 5. Defects found and fixed this session

1. **See-through slit under staggered crowns** (`NativeLodMesher.emitCanopyWalls`): a slab's side was closed only
   above a lower neighbour, never below a higher one; neighbouring crowns of different heights left an open band
   one to three blocks high. Walls are now the exact complement of the neighbour's coverage.
2. **Opaque stand-in floor tagged `DH_BLOCK_WATER`** (water deeper than the scan): packs shade that material as water.
   Now `UNKNOWN`.
3. **A test that never finished**: the earlier `noTwoFacesShareAPlaneAndADirection` compared every pair of quads —
   ~10¹¹ comparisons at level 0; the suite hung. Replaced by a rasterized check, linear in face area.
4. **Stale `LodVertexFormatTest`** referenced the pre-DH layout and blocked test compilation. Rewritten for the DH ABI.
5. **Wrong expectation** in `sparseTreesDoNotRaiseCoarseCells`: a 3×3 crown covers 9/16 of a level-2 cell, so it must
   survive there. Fixture changed to 2×2 crowns; levels 0–1 must keep them, 2–3 must not.
6. **Off-path sampler change** (`ShaderSamplers`): with the option off, `dhDepthTex*` went through a new lambda.
   Now the baseline registration exactly.
7. **`TransformPatcher` change reverted**: the earlier session's null-guard could not make it testable
   (`PryzmaShaders.<clinit>` needs FML); the injected clip is tested directly against glsl-transformer instead.
8. **"Reset Video Settings" bypassed `onSettingsChanged()`**: it turned the option off without reloading a pack built
   with the DH contract.
9. **Data race in pregeneration**: `PrPregenTask` called `unmarkMissing` from the server thread into a fastutil set the
   client thread iterates (corruption, possible endless probe loop). Now a concurrent queue folded in on the client
   tick, and nothing is queued while the LOD is not running.
10. **Native core shader compiled on every resource reload with the option off**: now lazy (above).
11. GLSL: unsigned array indices cast to `int` (valid either way under glslang; kinder to strict drivers).
12. Line endings: four shader-port files had been rewritten from CRLF to LF; restored, `DHParameters` byte-identical.

---

## 6. Tests added or rewritten

| Suite | Tests | Covers |
| --- | --- | --- |
| `LodColumnScanTest` (new) | 14 | Crowns over floors, undergrowth, bushes, trees in water and over lava, depth limits, reads bounded to `[minY, top]`, rejection and reset. |
| `NativeLodMesherTest` | 23 | Ground sealing oracle (every level), **canopy sealing oracle** (tops, undersides, wall coverage = complement of the neighbour slab, from the mesher's own `Grid`), staggered crowns, slab rules at level 0, sparse vs dense forests, jitter, water, floorless water, rasterized no-coplanar-overlap over a full level-0 forest/lake/terraces scene, DH vertex bytes. |
| `LodVertexFormatTest` | 4 | GL format = mesher layout = DH ABI names; integer attributes; JSON ↔ GLSL uniforms and flat varyings; shader material ids = mesher ids. |
| `LodChunkDataTest` | 7 | + canopy layer, bottom clamp, light packing, deep copy. |
| `DHTerrainTransformTest` (new) | 2 | The injected clip parses, is declared once, runs first in `main`, unprojects with fragment depth. |

---

## 7. Files (against `S:\NEYRONKI\pryzma`)

Added: `lod/LodColumnScan.java`, `lod/LodMaterial.java`, `lod/LodShaderSupport.java`, `test/lod/LodColumnScanTest.java`,
`test/shader/pipeline/transform/DHTerrainTransformTest.java`, `run/selftest/farlands.txt`,
`run/selftest/farlands_perf.txt`, `scratch/pillars/mutate.sh` (the mutation harness), this report.

Modified — LOD: `LodChunkData`, `LodColorSource`, `LodMesh`, `LodVertexFormat`, `LodWorldScanner`, `LodBlockColors`,
`LodDataStore` (`all()`), `LodStorageIntake`, `NativeLodMesher`, `NativeLodRenderer`, `PryzmaLod`, and the
`lod_terrain` core shader (`.json`, `.vsh`, `.fsh`).

Modified — outside the LOD package: `ShadedWorldRenderingPipeline` (support field, frame hook, accessors),
`StandardMacros`, `CommonUniforms`, `MatrixUniforms`, `ShaderSamplers`, `DHTerrainTransformer` (clip injection; a
per-compile debug `warn` removed), `PrSettingsScreen` (reset path), `dev/PrSelfTest` (`farlands` step, dev source set
only, never packaged).

---

## 8. Known limitations

* **No LOD shadows under packs**: `dh_shadow` is not drawn; distant terrain casts no shadow-map shadows.
* **Packs without DH programs** see the LOD only up to the game's far plane (render distance × 64 blocks).
* **Core-profile `dh_terrain` programs** (`#version ≥ 150` without `compatibility`) hit the port's
  `Unknown patch type: DH_TERRAIN` in `TransformPatcher`'s core branch (upstream behaviour). The support catches it and
  logs that the LOD is not drawn under that pack; the pack itself is unaffected.
* **Memory**: the canopy layer roughly doubles a stored chunk (≈10 KB). At 128 chunks with eviction at 2× radius,
  up to ~2 GB — inside the stated budget (64 GB, ZGC), but not small.
* Heightfield limits remain: no overhangs, caves, floating islands; no multiplayer data path beyond what the client
  loads; no disk cache.
* **Quitting during `/pryzma pregen` hangs the integrated server** (pre-existing, §1.1). Until the pregen task drains
  its in-flight generation on shutdown, the likely workaround — not verified — is `/pryzma pregen stop`, then a few
  seconds before quitting, so the in-flight chunks finish and release their tickets while the server still ticks.
