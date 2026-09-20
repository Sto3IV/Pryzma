# Pryzma

Pryzma is a high-performance graphics optimization, native shader, and visual fidelity engine built specifically for modern Minecraft on the NeoForge platform (1.21.1).

Designed from the ground up as a comprehensive visual overhaul, Pryzma unites modern multicore meshing paradigms inspired by Sodium with complete backward and forward fidelity for the massive ecosystem of OptiFine and legacy MCPatcher resource packs.

---

## Key Features

### 1. Built-in Shader Pipeline (No Iris Required)
Pryzma contains a fully integrated, native GLSL shader engine embedded directly into the rendering pipeline:
* **Zero External Dependencies:** Third-party shader loaders (such as Iris or Oculus) are **not required**. Standard shaderpacks function out of the box.
* **Full GLSL Specification Support:** Runs modern and classic shaderpacks (including Complementary, BSL, AstraLex, SEUS, and Continuum).
* **Advanced Render Stages:** Native execution of deferred composite passes, shadow mapping, depth texture extraction, custom uniform buffers, and post-processing filters.
* **In-Game Shader Configuration:** Dedicated in-game GUI for tweaking individual shaderpack properties, profiles, color grades, and rendering passes with real-time reload.

### 2. Complete Resource Pack & Texture Fidelity (OptiFine & and old MCPatcher format)
Pryzma restores and guarantees 100% compatibility with both modern OptiFine resource packs and classic legacy MCPatcher formats:
* **Connected Textures (CTM):** Full support for standard 47-tile CTM, horizontal, vertical, top, repeat, fixed, compact, and overlay modes.
* **Legacy MCPatcher Aliasing:** Automatic fallback and aliasing for classic `assets/minecraft/mcpatcher/` layouts alongside modern `assets/minecraft/optifine/` directories.
* **Custom Item Textures (CIT):** Context-aware item sprites and 3D models based on NBT data, enchantments, stack counts, durability, and custom display names.
* **Custom Entity Models (CEM):** Complete replacement and structural extension of mob geometry, bone hierarchies, skeletal animations, and entity textures.
* **Custom Colors & Biome Colormaps:** Advanced smooth-grid colormap blending for grass, foliage, water, sky, fog, and underwater environments across vanilla and modded biomes.
* **Custom Sky & Celestial Bodies:** Multi-layered celestial rendering with day/night fading intervals, celestial depth, customized constellations, and astronomical horizons.
* **Random Entities & Variants:** Procedural and rule-based entity textures conditioned on biomes, spawn coordinates, health percentages, and name tags.
* **Better Grass & Better Snow:** Side-connective grass, mycelium, and podzol blocks, accompanied by full snowy side transitions on stairs, slabs, and fences.
* **Natural Textures:** Procedural rotational and mirrored tile variations to break visual repetition on terrain blocks.
* **Fast Paintings:** High-speed painting atlas meshing and decoupled batch rendering for lag-free art displays.

### 3. Dedicated Multithreaded Chunk Rendering Architecture
Pryzma eliminates the legacy chunk-rendering bottlenecks of vanilla Minecraft and historical patchers by incorporating a dedicated worker pool inspired by Sodium's parallel chunk builder:
* **Dedicated Worker Isolation:** Chunk meshing is completely decoupled from the shared, contention-heavy vanilla `Util.backgroundExecutor()`.
* **Dynamic Core Allocation:** Automatically analyzes host CPU topology and allocates an isolated, dynamically scaling pool of worker threads (`Pryzma-Chunk-Worker`).
* **Micro-Stutter Elimination:** Replaces blocking single-chunk frame delays and sleep-chokes with worker-budgeted dispatch capacity, maintaining rock-solid frame pacing even during high-speed flight and world loading.
* **Worker Thread Priority:** Background chunk meshing threads operate at reduced thread priority (`NORM_PRIORITY - 2`) to ensure that main-thread input polling and frame presentation are never starved of CPU cycles.

### 4. Deep Mod & Ecosystem Compatibility
* **NeoForge Model Data & Pipelines:** Preserves full compatibility with NeoForge `IModelData`, ambient occlusion matrix evaluations, fluid rendering hooks, and custom block entity renderers.
* **Modded Biomes & Dimensions:** Native color-blending and sky rendering support for modded world generation.

---

## Feature Comparison Matrix

> **Why Pryzma exists**: To recreate the full visual fidelity of classic and modern OptiFine resource packs on **NeoForge 1.21.1**, players without **Pryzma** are forced to assemble a fragile "mod salad" of 15 to 20 separate mods — many of which don't even support OptiFine formatting and force you to convert textures into proprietary JSON formats.
>
> **Pryzma** delivers everything out of the box in **ONE. SINGLE. MOD.** Not 15+ different mods where you have to set up configs for each one and constantly deal with mixin conflicts. Just one single JAR powered by clean NeoForge ASM transformers.

| Feature | Vanilla 1.21.1 | Sodium & Friends (NeoForge 1.21.1 Ecosystem) | Pryzma (NeoForge 1.21.1) |
| :--- | :---: | :--- | :---: |
| **Shaderpack Pipeline** (BSL, Complementary, SEUS) | ❌ No | ⚠️ Missing, requires `Iris` | ✅ **Yes (Native Integrated Pipeline)** |
| **Modern Chunk Meshing Engine** | ⚠️ Basic Sync | ✅ Yes (`Sodium`) | ✅ **Yes (Integrated Multi-Threaded Mesher)** |
| **Dynamic CPU Thread Scaling** (1–24+ cores) | ❌ Fixed Pool | ⚠️ Partial (`Sodium` internal pool; no render-to-tick pacing) | ✅ **Yes (Dynamic Auto-Scaled Worker Pool)** |
| **Entity Occlusion Culling** (Behind opaque blocks) | ❌ Frustum Only | ⚠️ Missing, requires `Entity Culling` | ✅ **Yes (Frustum + Smooth World Culling)** |
| **Smart Leaf Face Culling** (Forest FPS Boost) | ⚠️ Fast/Fancy Only | ⚠️ Missing, requires `Cull Leaves` | ✅ **Yes (Native Smart Leaves Optimization)** |
| **Smooth World & Tick-to-Render Pacing** | ❌ No (Microstutters) | ⚠️ Missing, requires `Lithium` *(tick logic only; lacks frame pacing)* | ✅ **Yes (Native Smooth World & Smooth FPS)** |
| **Fast Math / Trigonometric Lookups** | ❌ Standard Math | ⚠️ Partial (Internal helpers in `Sodium`/`Lithium`) | ✅ **Yes (Precomputed Lookup Cache)** |
| **OptiFine Format Support** (`optifine/` folder) | ❌ No | ⚠️ Fragmented across 8+ different mods | ✅ **Yes (100% Native Drop-in)** |
| **Legacy MCPatcher Support** (`mcpatcher/` folder) | ❌ No | ❌ **Unsupported** *(No mod supports legacy paths; requires manual renaming)* | ✅ **Yes (Full Runtime Aliasing & Deduplication)** |
| **Connected Textures (CTM)** (Glass, Sandstone, Bookshelves) | ❌ No | ⚠️ Missing, requires `Continuity (NeoForge)`<br>*(or `Fusion`, requires converting textures to Fusion JSON format)* | ✅ **Yes (Native CTM + MCPatcher Aliasing)** |
| **Custom Colors & Colormaps** (`color.properties`, `colormap/`) | ❌ Fixed Colormaps | ⚠️ Missing, requires `Polytone`<br>*(partial color.properties support; advanced features require Polytone format)* | ✅ **Yes (Native OptiFine/MCPatcher Colormaps)** |
| **Custom Lightmaps** (`lightmap/world0.png`) | ❌ Hardcoded Curve | ⚠️ Missing, requires `Polytone`<br>*(static only; animated lightmaps unsupported / require Polytone format)* | ✅ **Yes (Native Dynamic & Animated Lightmaps)** |
| **Custom Skies & Celestial Domes** (`sky/world0/*.properties`) | ❌ Single Sky Sphere | ⚠️ Missing, requires `NeoforgeSkyboxes`<br>*(requires converting to FSB JSON format; partial legacy support via FSB-Interop)* | ✅ **Yes (Native Multi-Layer Rotatable Skyboxes)** |
| **Animated Textures & Custom GUIs** (`anim/*.properties`) | ⚠️ `.mcmeta` Only | ⚠️ Missing, requires `Animatica (NeoForge)` | ✅ **Yes (Full support of OptiFine anim & Custom GUIs)** |
| **Custom Entity Models (CEM)** (`cem/*.jem`, `*.jpm`) | ❌ No | ⚠️ Missing, requires `EMF (Entity Model Features)` | ✅ **Yes (Native CEM Engine)** |
| **Random & Biome Entity Textures** (Random Mobs) | ❌ No | ⚠️ Missing, requires `ETF (Entity Texture Features)` | ✅ **Yes (Native Random Mobs by Biome/Name/Height)** |
| **Custom Item Textures (CIT)** (Anvil Renaming, NBT) | ❌ No | ⚠️ Missing, requires `CIT Resewn (NeoForge)` | ✅ **Yes (Native CIT with Full NBT Matching)** |
| **Optifine Capes support** | ❌ Mojang Only | ⚠️ Missing, requires `OptifineCapes` mod | ✅ **Yes (build-in, no conflicts with mojang capes)** |
| **Better Grass** (Full-block grass sides & snowy grass) | ❌ No | ⚠️ Missing, requires `BetterGrassify`<br>*(or `Fusion` with external converted resource pack)* | ✅ **Yes (Native Toggle: Off / Fast / Fancy)** |
| **Better Snow** (Snow under fences, flowers, stairs) | ❌ No | ⚠️ Missing, requires `Snow! Real Magic!` *(or `Better Snow`)* | ✅ **Yes (Native Integrated Better Snow)** |
| **Dynamic Lights** (Held & dropped glowing items) | ❌ No | ⚠️ Missing, requires `LambDynamicLights (NeoForge)` *(or `Sodium Dynamic Lights`)* | ✅ **Yes (Native Dynamic Lights: Off / Fast / Fancy)** |
| **Cinematic Smooth Zoom** | ⚠️ Spyglass Only | ⚠️ Missing, requires `Zoomify` *(or `Just Zoom`)* | ✅ **Yes ('C' key by default can be changed in settings)** |
| **Clear Water & Custom Fog Distance** | ❌ Murky Default | ⚠️ Missing, requires `Sodium Extra` | ✅ **Yes (Native Clear Water & Fog Controls)** |
| **Granular Video Settings** (Stars, Fog, Vignette, Clouds) | ❌ Minimal | ⚠️ Fragmented, requires `Sodium Extra` + `Reese's Sodium Options` | ✅ **Yes (High & Unified in Native Video Settings)** |
| **Required Mod JARs Count** | **0** | **15–20+ separate individual JARs** | **ONE. SINGLE. JAR.** |
| **Modloader Integration** | Baseline | Relies on dozens of brittle Mixins competing for bytecode hooks | **Surgical NeoForge ASM `ITransformer` Pipeline** |


---

## Comprehensive Settings Breakdown

Pryzma provides exhaustive, granular control over every aspect of the client graphics pipeline:

### Performance Settings
* **Chunk Updates:** Controls the worker-budgeted chunk rebuild capacity per frame (from conservative budgets up to uncapped worker throughput).
* **Fast Render:** Optimized rendering algorithms that reduce GPU draw-call overhead.
* **Smooth FPS:** Stabilizes frame delivery times by buffering graphics driver queue flushes.
* **Smooth World:** Distributes internal server thread workload evenly to prevent hitching on single-player worlds.
* **Fast Math:** Replaces trigonometric functions with optimized lookup tables for CPU cycle reduction.

### Quality Settings
* **Mipmap Levels & Type:** Configurable mipmap filtering levels with selection between Nearest, Bilinear, Trilinear, and Bicubic algorithms.
* **Connected Textures:** Toggles and modes for connected terrain textures (Off, Fast, Fancy).
* **Custom Fonts & Colors:** Toggles for resource pack custom typography, biome color palettes, and lightmaps.
* **Custom Skies & Stars:** Individual control over multi-layered skyboxes and custom constellations.
* **Better Grass & Snow:** Configures full-block texture transitions on terrain surfaces.

### Details & Animation Settings
* **Sky & Fog Control:** Independent toggles for Sun, Moon, Stars, Fog, and Clouds (Fast, Fancy, Off).
* **Individual Particle Control:** Granular switches for Water, Lava, Fire, Smoke, Explosion, and Portal animations.
* **Held Item Tooltips & Dynamic FOV:** Toggles for camera adjustments and HUD elements.

---

## VERY IMPORTANT: change your java garbage collector to ZGC

To enable this, ensure your launcher is set to use **Java 21** (or newer). In your launcher's JVM Arguments, add this exact line: `-XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch`. Because ZGC works alongside the game, it needs comfortable breathing room. We strongly recommend allocating between **6 GB and 8 GB of RAM** (or up to 10 GB if you have 32 GB total). However, *never* allocate all of your system's memory—always leave at least 3 to 4 GB free for Windows and your graphics driver to prevent system crashes.

Minecraft runs on Java, which constantly creates and deletes temporary memory objects while you play. Standard "Garbage Collectors" periodically freeze the entire game to clean up this memory, causing those notorious micro-stutters and sudden FPS drops. **ZGC (Z Garbage Collector)** is a next-generation solution that cleans up memory in the background without pausing the game, keeping your frametimes perfectly flat and eliminating lag spikes.

This is fundamentally critical for **Pryzma**. Unlike vanilla Minecraft, Pryzma's engine uses a dynamically scaling pool of dedicated CPU threads to aggressively compile chunks, shaders, and connected textures in parallel. This massive multi-threaded architecture generates an extreme amount of temporary memory objects. If you use standard garbage collectors, your game will violently stutter as it tries to halt all worker threads at once to clean up. ZGC processes this heavy memory traffic effortlessly on the fly, unlocking Pryzma's true performance and guaranteeing a stutter-free experience even during high-speed flight.

---

## Building from Source

### Prerequisites
* Java Development Kit (JDK) 21 or higher.
* Git.

### Compilation Steps

1. Clone the repository:
```bash
git clone https://github.com/Sto3IV/Pryzma
cd pryzma
```

2. Build the mod using the included Gradle wrapper:
* On Linux / macOS:
```bash
./gradlew build
```
* On Windows:
```cmd
gradlew.bat build
```

3. Locate the compiled artifact:
The finalized NeoForge mod JAR file will be available under:
```
build/libs/pryzma-1.0.6.jar
```

---

## Verification & Automated Test Suite

Pryzma includes a comprehensive test harness covering ASM transformations, bytecode patches, thread allocation formulas, and resource pack parsing.

To run the full suite:
```bash
./gradlew test --rerun-tasks
```

All bytecode injections, chunk worker schedulers, and NeoForge pipeline bridges are verified on every compilation run.
