# Pryzma ✨

> *A single crystal to disperse the shadows. The complete visual soul of modern Minecraft.*

[![Platform](https://img.shields.io/badge/Platform-NeoForge%201.21.1-orange.svg)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-LGPL--3.0%20%2F%20MIT-green.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Sto3IV/Pryzma?color=purple)](https://github.com/Sto3IV/Pryzma/releases)

**Pryzma** is an all-in-one graphics optimization, shader pack pipeline, and visual fidelity mod for **Minecraft 1.21.1 (NeoForge)**. 

It revives the rich heritage of classic and modern resource packs — connected textures, custom item models, dynamic skies, animated lightmaps, and real-time shaders — without forcing you to assemble a fragile puzzle of 15+ different mods.

**One single JAR. Zero extra loaders. Pure aesthetic harmony.**

---

## ✦ What's New in v2.0.2

* **🚫 "Remove Realms" Option & Title Screen Compaction:**
  Removes the commercial Minecraft Realms button and background notifications from the title screen. Intelligently promotes NeoForge's `Mods` button into the freed second row and restores the bottom navigation buttons (Language, Options, Quit, Accessibility) up by 22 px to native vanilla proportions. Completely blocks background `RealmsNotificationsScreen` initialization, stopping unwanted HTTP polling and offline "Couldn't connect to realms" log spam. Configurable in **Video Settings → Other → Remove Realms** (ON by default) with native translations across all 40 languages.
* **🛡️ Zero-Allocation GUI Lightmap Isolation:**
  Resolved a subtle rendering anomaly where custom night lightmaps from OptiFine/MCPatcher resource packs saturated the (15, 15) texel sampled by `rendertype_text.vsh`, tinting button labels and menu text peach/orange. Pryzma now seamlessly substitutes a 16×16 pure white texture during the GUI phase (`Lighting.setupFor3DItems()` to `GuiGraphics.flush()`) with zero runtime allocations and automatic world-boundary state persistence.
* **🌌 Built-in Iris 1.8.14 Shaderpack Engine (Embedded Fork):**
  The full power of Iris 1.8.14 (shadowcomp passes, compute shaders, PBR textures, custom uniforms) integrated directly as an optimized fork into Pryzma (`net.pryzma.iris`). Decoupled from external loaders, optimized with counting-sort shadow culling, GL state caching (`GlBindingCache`), and 52-byte chunk vertex emission.
* **⚡ Multi-Draw Render Regions (`VboRegion`):**
  Chunks clustered into 8×8 regions rendered via native `glMultiDrawElements` / `glMultiDrawElementsBaseVertex`, cutting draw calls by orders of magnitude for buttery-smooth 1000+ FPS in idle scenes.
* **🧵 Dynamic Worker Scaling:**
  Chunk compiler worker pool dynamically scales up to 22 dedicated threads on high-core processors (AMD Ryzen 9 5900X / Intel Core i9) while reserving threads for game tick and render loops.

---

## ✦ The Magic Within

### 🌌 Shaders, Built In
Drop your favorite shaderpacks (*Complementary, BSL, Nostalgia, Photon, AstraLex*) directly into `shaderpacks/`.
* **The Iris pipeline, inside Pryzma:** an optimized fork of [Iris](https://github.com/IrisShaders/Iris) 1.8.14 (package `net.pryzma.iris`): shadow maps, `shadowcomp`, composite and deferred passes, compute shaders, custom images and uniforms, PBR textures.
* **No Sodium, no Oculus:** terrain renders on Pryzma's own chunk path, with block ids and light emission for the shader pack and render-region multi-draw in both the main and the shadow pass.
* **Leaner than stock Iris on this path:** the shadow pass collects its sections directly, without rebuilding the occlusion graph every frame, and redundant framebuffer and program binds are skipped.
* **In-Game Customization:** Iris' shader pack screen and option menus (**Video Settings → Shaders**, or `O`); `R` reloads, `K` toggles.

### 🎨 Your Resource Packs, Exactly as Intended
Pryzma provides 100% native drop-in support for both modern OptiFine formats and classic legacy MCPatcher packs:
* **Connected Textures (CTM):** Seamless panoramic glass, sandstone, and bookshelves.
* **Custom Item Textures (CIT):** Weapons, armor, and tools transform based on anvil names, enchantments, and NBT tags.
* **Custom Entity Models (CEM) & Random Mobs:** Unique creature variants, skeletal animations, and biome-specific skins.
* **Celestial Domes & Colormaps:** Multi-layered rotating skyboxes, custom nebulas, and lush, smoothly blended biome palettes.
* **Custom Lightmaps:** Atmospheric, flicker-free night and cave lighting with GUI isolation.
* **Better Grass & Better Snow:** Luscious full-sided grass and snow gently settling under fences and stairs.

### 💡 Real-Time Dynamic Lighting
Delve into the deepest subterranean caverns. Torches, lanterns, campfires, and glowing items in your hands or on the ground illuminate your path smoothly and naturally in real time — with **zero chunk re-meshing lag** via a lock-free snapshot architecture.

### ⚡ Fluid Performance & Multi-Threaded Pacing
Soar across mountains and oceans without hitching or micro-stutters:
* Dedicated background thread pool dynamically scaled to your CPU cores.
* Even frame delivery and intelligent tick-to-render pacing (`Smooth World`).
* Elimination of Windows thread scheduler `Thread.yield()` penalties.
* Smart foliage culling to keep dense forests running at high framerates.

### 🔍 Quality of Life & Classic Ergonomics
* **Smooth Cinematic Zoom:** Bound to `C` by default (fully rebindable).
* **Enhanced F3 Debug Overlay:** Restored comprehensive statistics (min FPS, chunk updates, VRAM allocations, GPU usage).
* **Granular Control:** Fine-tune clouds, fog, stars, particles, and details in familiar, beautifully organized menus.

---

## ✦ Feature Comparison Matrix

Why juggle dozens of conflicting mods with fragmented configs?

| Feature | Vanilla 1.21.1 | The Fragmented Mod Salad (15+ Mods) | Pryzma (NeoForge 1.21.1) |
| :--- | :---: | :--- | :---: |
| **Shaderpack Pipeline** (BSL, Complementary, Nostalgia) | ❌ No | ⚠️ Requires `Iris` / `Oculus` | ✅ **Built-in (Optimized Fork of Iris)** |
| **OptiFine Format** (`optifine/` folder) | ❌ No | ⚠️ Fragmented across 8+ separate mods | ✅ **100% Native Drop-in** |
| **Legacy MCPatcher Format** (`mcpatcher/` folder) | ❌ No | ❌ **Unsupported** *(requires manual conversion)* | ✅ **Native Runtime Aliasing** |
| **Connected Textures (CTM)** | ❌ No | ⚠️ Requires `Continuity` or `Fusion` | ✅ **Native Full-Suite CTM** |
| **Custom Item Textures (CIT)** | ❌ No | ⚠️ Requires `CIT Resewn` | ✅ **Native Full NBT Matcher** |
| **Custom Entity Models & Textures (CEM/ETF)** | ❌ No | ⚠️ Requires `EMF` + `ETF` | ✅ **Native CEM & Random Mobs** |
| **Custom Skies & Celestial Domes** | ❌ Vanilla Sky | ⚠️ Requires `NeoforgeSkyboxes` | ✅ **Native Multi-Layer Skyboxes** |
| **Custom Colormaps & Lightmaps** | ❌ Static | ⚠️ Requires `Polytone` | ✅ **Native Smooth Lighting & Colors** |
| **Real-Time Dynamic Lights** | ❌ No | ⚠️ Requires `LambDynamicLights` | ✅ **Native (Zero Re-meshing Lag)** |
| **Better Grass & Better Snow** | ❌ No | ⚠️ Requires `BetterGrassify` + `Snow Real Magic` | ✅ **Native Integrated Toggles** |
| **Cinematic Smooth Zoom** | ⚠️ Spyglass Only | ⚠️ Requires `Zoomify` or `Just Zoom` | ✅ **Native ('C' Key)** |
| **Fast Paintings Optimization** | ❌ Multi-quad Lag | ⚠️ Requires `FastPaintings` | ✅ **Native Consolidated Meshes** |
| **Remove Realms & Compact Menu** | ❌ Commercial Clutter | ⚠️ Requires separate tweak mod | ✅ **Native Toggle (Settings → Other)** |
| **Multithreaded Chunk Meshing** | ⚠️ Basic Sync | ✅ `Sodium` | ✅ **Native Dedicated Worker Pool** |
| **Number of JAR Files Required** | **0** | **15–20+ separate individual mods** | **ONE. SINGLE. JAR.** |
| **Architecture & Compatibility** | Baseline | Complex web of competing mixins | **Clean Sponge Mixin Monolith** |

---

## ✦ Installation & Quick Start

1. Install **[NeoForge](https://neoforged.net/)** for Minecraft **1.21.1**.
2. Download the latest **`pryzma-2.0.2.jar`** from [Releases](https://github.com/Sto3IV/Pryzma/releases) and place it into your `.minecraft/mods/` folder.
3. Place your favorite shaderpacks into `.minecraft/shaderpacks/` and resource packs into `.minecraft/resourcepacks/`.
4. Launch the game, open **Options → Video Settings**, and customize your visual experience to your heart's content.

> [!TIP]
> **Recommended JVM Setup (Java 21):**
> For maximum smoothness during fast flight and heavy shader workloads, we recommend allocating **6 to 8 GB of RAM** with the modern generational Z garbage collector:
> ```text
> -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch
> ```

---

## ✦ Building from Source

Pryzma is built with standard Gradle.

```bash
git clone https://github.com/Sto3IV/Pryzma.git
cd Pryzma
./gradlew build
```
*(On Windows, use `gradlew.bat build`)*

The output JAR will be generated in `build/libs/`.

To run the automated verification test suite:
```bash
./gradlew test --rerun
```

---

## ✦ License & Attribution

Pryzma is distributed under a composite open-source license (see [LICENSE](LICENSE)):

* **Pryzma Core & Optimizations:** Licensed under the **MIT License**. Copyright (c) 2026 Sto3IV & Ranni.
* **Shaderpack Pipeline (`net.pryzma.iris`):** Embedded optimized fork of [Iris](https://github.com/IrisShaders/Iris) 1.8.14, licensed under the **GNU LGPL-3.0** (`META-INF/LICENSE-IRIS`, `META-INF/NOTICE-IRIS`). Copyright (c) 2020-2024 Iris Contributors.
* **glsl-transformer:** Bundled as a nested Jar-in-Jar under the **GNU AGPL-3.0**. Copyright (c) douira.
* **jcpp & ithaka-digraph:** Licensed under the **Apache License 2.0**.

In compliance with LGPL-3.0 and AGPL-3.0, full source code and modifications are publicly available in this repository.

---

<p align="center">
  <sub>Crafted with quiet devotion by <b>Sto3IV & Ranni</b></sub>
</p>
