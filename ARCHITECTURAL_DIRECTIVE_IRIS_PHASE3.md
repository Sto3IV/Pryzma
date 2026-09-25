# ARCHITECTURAL DIRECTIVE: PRYZMA 2.0+ (IRIS TECHNOLOGIES EXPANSION - PHASE 3)
**Target Directory:** `S:\NEYRONKI\pryzma`  
**Reference Codebase:** `S:\NEYRONKI\Source-codes\Iris\`  
**Target Environment:** Minecraft 1.21.1 / NeoForge (Java 21 LTS)  
**Hardware Context:** AMD Radeon RX 6950 XT (OpenGL 4.6 Core Profile, RDNA2, no CUDA)  
**Lead Architect:** Ranni (Antigravity)  
**Executive Lead:** Claude (Kiyotaka Ayanokoji Persona)  

---

## 1. Executive Directive

The user has explicitly commanded:
> *"Поручи пожалуйста Claude добавить эксклюзивные технологии Iris. Я понимаю, что шейдерпаки сейчас в основном делают под Iris, поэтому не хочу лишать создателей тех эксклюзивных фич, к которым они привыкли. Проверь как Claude это реализовывает и корректируй если требуется."*

Pryzma 2.0 has successfully unified the core OptiFine shader pipeline, dynamic lights, and resource packs into a clean Sponge Mixin monolith. However, modern shaderpacks (Complementary, BSL, Nostalgia, Photon, AstraLex) rely on Iris-extended capabilities. 

Your objective in Phase 3 is to integrate the most vital Iris technologies into `S:\NEYRONKI\pryzma` while preserving the clean, single-JAR monolithic architecture and 100% test passing rate.

---

## 2. Priority Feature Roadmap

### Priority 1: Block, Item & Entity ID Mapping (`block.properties`, `item.properties`, `entity.properties` & `mc_Entity`)
**Impact:** CRITICAL. The #1 visual differentiator.
- **Problem:** Currently, in `PrGlslTransformer.java`, `mc_Entity` is stubbed to `0`. Without block IDs, shaders cannot determine which blocks are foliage or glowing materials. Waving plants (leaves, grass, crops swaying in wind) and custom block emission are completely disabled.
- **Solution:**
  1. Create `net.pryzma.shader.id.PrBlockIdMap` (or similar under `net.pryzma.shader`):
     - Parse `shaders/block.properties` (format: `block.<id>=<block_name_or_tag>[:state=val]`).
     - Parse `shaders/item.properties` and `shaders/entity.properties`.
     - Fast lookup table from `BlockState` -> integer `blockId` (e.g. `short[]` indexed by `Block.getId(state)`).
  2. Vertex Shader Injection:
     - When building chunk mesh quads in `SectionCompilerMixin` (or model rendering), pass the block ID into vertex data.
     - In `PrGlslTransformer`, bind `mc_Entity` to the vertex attribute carrying the block ID.
  3. Ensure `mc_midTexCoord` provides the sprite UV center coordinate for texture atlas sampling.

### Priority 2: Multi-Pass Shadows & Post-Shadow Comp (`shadowcomp1..99`)
**Impact:** HIGH. Eliminates shadow blotches on packs like Nostalgia.
- Support `shadowcomp` programs in `PrShaderPrograms` and `PrShaderPipeline`.
- Support ping-pong shadow color buffers (`shadowcolor0`, `shadowcolor1`).
- Execute shadowcomp passes after the primary `shadow` pass and before deferred/composite passes.

### Priority 3: Entities & Hand in Shadow Pass
**Impact:** HIGH.
- During the `shadow` pass, allow `EntityRenderDispatcher` and `ItemInHandRenderer` to render entity geometry into the shadow map framebuffer so players and mobs cast real dynamic shadows.

### Priority 4: Modern Uniforms & Temporal Vectors
**Impact:** MEDIUM-HIGH.
- Add previous-frame matrix uniforms for TAA (Temporal Anti-Aliasing) and motion blur:
  - `previousModelViewMatrix` / `gbufferPreviousModelView`
  - `previousProjectionMatrix` / `gbufferPreviousProjection`
- Add `iris_currentPass` uniform or stage indicators.
- High-precision camera offset uniforms (`cameraPosition`, `previousCameraPosition`).

### Priority 5: Advanced Buffer & Compute Foundations (SSBO, Custom Images, Compute Shaders)
**Impact:** ADVANCED. Unlocks next-gen ray-traced / path-traced packs.
- Support `iris.properties` directives.
- In `PrShaderProperties`: parse and recognize `CUSTOM_IMAGES`, `SSBO`, and compute shader stages (`.csh`).
- On our OpenGL 4.6 GPU (AMD Radeon RX 6950 XT), support GL compute shaders (`glDispatchCompute`) and Shader Storage Buffer Objects (`GL_SHADER_STORAGE_BUFFER`) when requested by packs.

---

## 3. Strict Architectural Rules

1. **Working Directory:** All code must be authored directly in `S:\NEYRONKI\pryzma`. Do not create separate clone directories.
2. **Namespace:** Everything belongs in `net.pryzma.*`. Do not import or replicate foreign package roots (`net.irisshaders.*`).
3. **Mixin Hygiene:** Use Sponge Mixin and MixinExtras (`@WrapOperation`, `@ModifyReturnValue`, `@Inject`). No raw bytecode patching.
4. **Verification Requirement:**
   - Every added feature must include automated unit tests under `src/test/java/net/pryzma/shader/`.
   - All tests must pass: `./gradlew test --rerun-tasks` must exit with `BUILD SUCCESSFUL`.
   - The final artifact must compile cleanly with `./gradlew jar`.
5. **Obsidian Diary:** Document your analysis and execution log in `Library/05 AI Diaries/Claude/2026-09-25-Pryzma-Phase3-Iris-Technologies.md`.
