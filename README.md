# Pryzma ✨

> *A single crystal to disperse the shadows. The complete visual soul of modern Minecraft.*

[![Platform](https://img.shields.io/badge/Platform-NeoForge%201.21.1-orange.svg)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Sto3IV/Pryzma?color=purple)](https://github.com/Sto3IV/Pryzma/releases)

**Pryzma** is an all-in-one graphics optimization, native shader engine, and visual fidelity mod for **Minecraft 1.21.1 (NeoForge)**. 

It revives the rich heritage of classic and modern resource packs — connected textures, custom item models, dynamic skies, animated lightmaps, and real-time shaders — without forcing you to assemble a fragile puzzle of 15+ different mods.

**One single JAR. Zero extra loaders. Pure aesthetic harmony.**

---

## ✦ The Magic Within

### 🌌 Shaders, Natively Unleashed
No extra shader engines needed. Drop your favorite shaderpacks (*Complementary, BSL, Nostalgia, Photon, AstraLex*) directly into `shaderpacks/`.
* **Zero Dependencies:** Runs out of the box without Iris or Oculus.
* **Complete GLSL Pipeline:** Shadow mapping, multi-pass composite stages, deferred lighting, custom uniforms, and bloom.
* **Advanced Multi-Pass Filtering:** Native support for `shadowcomp` ping-pong shadow filtering and dynamic entity shadows.
* **In-Game Customization:** Dedicated, real-time shader settings menu with hot-reloading.

### 🎨 Your Resource Packs, Exactly as Intended
Pryzma provides 100% native drop-in support for both modern OptiFine formats and classic legacy MCPatcher packs:
* **Connected Textures (CTM):** Seamless panoramic glass, sandstone, and bookshelves.
* **Custom Item Textures (CIT):** Weapons, armor, and tools transform based on anvil names, enchantments, and NBT tags.
* **Custom Entity Models (CEM) & Random Mobs:** Unique creature variants, skeletal animations, and biome-specific skins.
* **Celestial Domes & Colormaps:** Multi-layered rotating skyboxes, custom nebulas, and lush, smoothly blended biome palettes.
* **Custom Lightmaps:** Atmospheric, flicker-free night and cave lighting.
* **Better Grass & Better Snow:** Luscious full-sided grass and snow gently settling under fences and stairs.

### 💡 Real-Time Dynamic Lighting
Delve into the deepest subterranean caverns. Torches, lanterns, campfires, and glowing items in your hands or on the ground illuminate your path smoothly and naturally in real time — with **zero chunk re-meshing lag**.

### ⚡ Fluid Performance & Multi-Threaded Pacing
Soar across mountains and oceans without hitching or micro-stutters:
* Dedicated background thread pool dynamically scaled to your CPU cores.
* Even frame delivery and intelligent tick-to-render pacing (`Smooth World`).
* Smart foliage culling to keep dense forests running at high framerates.

### 🔍 Quality of Life & Classic Ergonomics
* **Smooth Cinematic Zoom:** Bound to `C` by default (fully rebindable).
* **Fast Paintings:** High-speed consolidated painting rendering for grand art galleries.
* **Granular Control:** Fine-tune clouds, fog, stars, particles, and details in familiar, beautifully organized menus.

---

## ✦ Feature Comparison Matrix

Why juggle dozens of conflicting mods with fragmented configs?

| Feature | Vanilla 1.21.1 | The Fragmented Mod Salad (15+ Mods) | Pryzma (NeoForge 1.21.1) |
| :--- | :---: | :--- | :---: |
| **Shaderpack Pipeline** (BSL, Complementary, Nostalgia) | ❌ No | ⚠️ Requires `Iris` / `Oculus` | ✅ **Built-in Native Engine** |
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
| **Multithreaded Chunk Meshing** | ⚠️ Basic Sync | ✅ `Sodium` | ✅ **Native Dedicated Worker Pool** |
| **Number of JAR Files Required** | **0** | **15–20+ separate individual mods** | **ONE. SINGLE. JAR.** |
| **Architecture & Compatibility** | Baseline | Complex web of competing mixins | **Clean Sponge Mixin Monolith** |

---

## ✦ Installation & Quick Start

1. Install **[NeoForge](https://neoforged.net/)** for Minecraft **1.21.1**.
2. Download the latest **`pryzma-2.0.0.jar`** from [Releases](https://github.com/Sto3IV/Pryzma/releases) and place it into your `.minecraft/mods/` folder.
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

<p align="center">
  <sub>Crafted with quiet devotion by <b>Sto3IV & Ranni</b> • Licensed under MIT</sub>
</p>
