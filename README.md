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

### 2. Complete Resource Pack & Texture Fidelity (OptiFine & MCPatcher)
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
* **Dynamic Core Allocation:** Automatically analyzes host CPU topology and allocates an isolated thread pool of up to 10 worker threads (`Pryzma-Chunk-Worker-1` through `10`).
* **Micro-Stutter Elimination:** Replaces blocking single-chunk frame delays and sleep-chokes with worker-budgeted dispatch capacity, maintaining rock-solid frame pacing even during high-speed flight and world loading.
* **Worker Thread Priority:** Background chunk meshing threads operate at reduced thread priority (`NORM_PRIORITY - 2`) to ensure that main-thread input polling and frame presentation are never starved of CPU cycles.

### 4. Deep Mod & Ecosystem Compatibility
* **Distant Horizons:** 100% compatible out of the box. Fully synchronizes with Distant Horizons LOD (Level of Detail) rendering passes, protects LOD passes during shadow rendering, and prevents buffer collisions.
* **NeoForge Model Data & Pipelines:** Preserves full compatibility with NeoForge `IModelData`, ambient occlusion matrix evaluations, fluid rendering hooks, and custom block entity renderers.
* **Modded Biomes & Dimensions:** Native color-blending and sky rendering support for modded world generation.

---

## Feature Comparison Matrix

| Feature | Vanilla | Legacy Patchers | Sodium / Iris | Pryzma |
| :--- | :---: | :---: | :---: | :---: |
| **Native Shaders (No Extra Mods)** | No | Yes (Outdated) | Requires Iris | **Yes (Built-in)** |
| **OptiFine CTM / CIT / CEM** | No | Yes | Requires Extra Mods | **Yes (Native)** |
| **Legacy MCPatcher Packs** | No | Partial | No | **Yes (Full Aliasing)** |
| **Dedicated 10-Thread Chunk Meshing** | No | No | Yes | **Yes (Integrated)** |
| **Distant Horizons Integration** | No | Unstable | Requires Patches | **Yes (Synchronized)** |
| **NeoForge 1.21.1 Native Engine** | Yes | No | Partial | **Yes (Engineered)** |
| **Granular In-Game Graphic Controls** | Minimal | High | Fragmented | **High & Unified** |

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

## Building from Source

### Prerequisites
* Java Development Kit (JDK) 21 or higher.
* Git.

### Compilation Steps

1. Clone the repository:
```bash
git clone <repository_url>
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
build/libs/pryzma-1.21.1-1.0.0.jar
```

---

## Verification & Automated Test Suite

Pryzma includes a comprehensive test harness covering ASM transformations, bytecode patches, thread allocation formulas, and resource pack parsing.

To run the full suite:
```bash
./gradlew test --rerun-tasks
```

All bytecode injections, chunk worker schedulers, and NeoForge pipeline bridges are verified on every compilation run.
