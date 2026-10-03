<p align="center">
  <img src="https://raw.githubusercontent.com/Sto3IV/archive-of-images/main/pryzma_8bit.png" width="280" alt="Pryzma Logo">
<br>
  <a href="https://github.com/Sto3IV/Pryzma"><img src="https://img.shields.io/badge/Minecraft-1.21.1-10b981?style=flat&logo=minecraft&logoColor=ffffff&labelColor=16181c" alt="Minecraft 1.21.1"></a>
  <a href="https://neoforged.net/"><img src="https://img.shields.io/badge/NeoForge-21.1.250+-f59e0b?style=flat&logo=neoforge&logoColor=ffffff&labelColor=16181c" alt="NeoForge"></a>
  <a href="https://modrinth.com/mod/pryzma"><img src="https://img.shields.io/badge/Modrinth-Available-00af5c?style=flat&logo=modrinth&logoColor=ffffff&labelColor=16181c" alt="Modrinth"></a>
  <a href="https://curseforge.com/minecraft/mc-mods/pryzma"><img src="https://img.shields.io/badge/CurseForge-Available-f16436?style=flat&logo=curseforge&logoColor=ffffff&labelColor=16181c" alt="CurseForge"></a>
  <a href="https://github.com/Sto3IV/Pryzma"><img src="https://img.shields.io/badge/Tests-194%20Passing-6366f1?style=flat&logo=githubactions&logoColor=ffffff&labelColor=16181c" alt="194 Passing Tests"></a>
</p>
<!-- I apologize for the HTML but it looks so pretty :) -->

About
=====

**Pryzma** is an all-in-one visual engine, autonomous GLSL shader pipeline, and graphics optimization mod for [NeoForge 1.21.1](https://neoforged.net/).

It was born out of a simple, personal desire: my partner and I wanted to play on a private server while keeping the breathtaking visual fidelity of our treasured 10-year-old texture packs (from the classic OptiFine and MCPatcher era) without wrestling with broken configs or endless format conversions.

Pryzma restores the full beauty of classic resource packs — connected textures, custom item models, dynamic skies, animated lightmaps, and real-time shaders — in **one single, elegant JAR**. No mod salad. No conflicting loaders. Just drop it in and play.

Installation
============

1. Install [NeoForge 1.21.1](https://neoforged.net/) (build 21.1.250 or newer recommended).
2. Download the latest release of **Pryzma** from our [Modrinth](https://modrinth.com/mod/pryzma) or [CurseForge](https://curseforge.com/minecraft/mc-mods/pryzma) page.
3. Place `pryzma-2.1.2.jar` into your `.minecraft/mods/` directory.
4. Drop your favorite resource packs and shaderpacks into their respective folders, and enjoy!

Screenshots
===========

![Breathtaking Shaders with Celestial Skies](https://raw.githubusercontent.com/Sto3IV/archive-of-images/main/2026-09-19_10.18.35.png)

![Seamless Connected Textures and Warm Evening Atmosphere](https://raw.githubusercontent.com/Sto3IV/archive-of-images/main/2026-09-19_10.18.39.png)

![Real-Time Dynamic Handheld Lighting and Classic HUD Ergonomics](https://raw.githubusercontent.com/Sto3IV/archive-of-images/main/2026-10-02_22.06.43.png)

Feature Matrix
==============

| Feature | Vanilla | Fragmented Setup | Pryzma |
| :--- | :---: | :---: | :---: |
| **Shaders (GLSL)** | ❌ None | ⚠️ Extra loaders | ✅ Built-in Native |
| **OptiFine Packs** | ❌ None | ⚠️ 8+ separate mods | ✅ 100% Native |
| **MCPatcher Format** | ❌ None | ❌ Unsupported | ✅ Auto-Aliasing |
| **Connected Textures (CTM)** | ❌ None | ⚠️ Continuity / Fusion | ✅ Native Full CTM |
| **Custom Items (CIT)** | ❌ None | ⚠️ CIT Resewn | ✅ Native Full CIT |
| **Custom Entity Models (CEM)**| ❌ None | ⚠️ EMF | ✅ Native CEM |
| **Random & Biome Mobs** | ❌ None | ⚠️ ETF | ✅ Native Random Mobs |
| **Custom Skies & Domes** | ❌ Vanilla | ⚠️ Skyboxes Mod | ✅ Native Multi-Layer |
| **Custom Biome Colormaps** | ❌ Static | ⚠️ Polytone | ✅ Native Colormaps |
| **Custom Lightmaps** | ❌ Static | ⚠️ Polytone (partial) | ✅ Native Dynamic |
| **Handheld Dynamic Lights** | ❌ None | ⚠️ LambDynamicLights | ✅ Native (Zero Lag) |
| **Cinematic Smooth Zoom** | ⚠️ Spyglass | ⚠️ Zoomify | ✅ Native ('C' Key) |
| **Better Grass & Snow** | ❌ None | ⚠️ 2 separate mods | ✅ Native Options |
| **Fast Paintings** | ❌ Laggy | ⚠️ FastPaintings | ✅ Native Batched |
| **Clean Menus & No Realms** | ❌ Cluttered | ⚠️ Separate tweaks | ✅ Native Setting |
| **Multithread Meshing** | ⚠️ Basic | ✅ External renderer | ✅ Native Worker Pool |
| **Zero-Stall VBO Compaction** | ❌ Freezes | ⚠️ Stutter-prone | ✅ Native On-Demand |
| **Hardware F3 RenderCache** | ❌ FPS Drop | ❌ CPU Overhead | ✅ 1800+ FPS Cache |
| **Total JARs Needed** | **0** | **15–20+ JARs** | **1 Single JAR** |
| **Architecture** | Baseline | Competing mixins | **Clean Monolith** |

Building from Source
====================

Pryzma builds with Gradle and requires Java 21 LTS:

```bash
# Clone the repository
git clone https://github.com/Sto3IV/Pryzma.git
cd Pryzma

# Run the full 194-test verification suite
./gradlew test

# Compile and package release JAR
./gradlew build
```

The compiled mod JAR will be located at `build/libs/pryzma-2.1.2.jar`.

Support & Philosophy
====================

Think of Pryzma as a **warm, quiet campfire**. We are not a commercial live-service studio, and **we will not be taking feature requests for the foreseeable future.** Our priority is strictly on long-term stability and keeping what is already here rock-solid.

If you encounter a genuine bug or a broken texture, please feel free to open a ticket on our [GitHub Issue Tracker](https://github.com/Sto3IV/Pryzma/issues). Please keep in mind that we both work real full-time jobs, but we quietly review issues during our free weekends.

Sponsorship & Hardware Testbed
===============================

Pryzma is 100% free, offline, and telemetry-free. There are no paywalled features, locked builds, or artificial tiers.

However, continuous shader profiling, compute pass benchmarking, and zero-stall VBO optimization across modern graphics architectures require dedicated hardware testing infrastructure. Voluntary community sponsorships directly support project maintenance and our hardware development fund — specifically acquiring high-throughput next-generation GPU testing equipment (targeting the GeForce RTX 5090 testbed) to profile and validate heavy shaderpacks, ray-tracing workloads, and extreme frame pacing for everyone.

If Pryzma brought back your beloved worlds and you would like to support our quiet campfire:
* **GitHub Sponsors:** [Sponsor @Sto3IV](https://github.com/sponsors/Sto3IV)
* **Boosty:** [Support on Boosty](https://boosty.to/sto3iv)
* **Ko-fi:** [Support on Ko-fi](https://ko-fi.com/sto3iv)

Modpacks
========

Pryzma can be used freely in any public or private modpack without requiring special permission. 100% offline, privacy-first, zero telemetry.

Licensing
=========

Pryzma is released under an open-source composite license:
* **Pryzma Core, Emulation & Optimizations:** [MIT License](LICENSE)
* **Native GLSL Shader Pipeline (`net.pryzma.shader`):** GNU LGPL-3.0-only

---
<p align="center">
  <sub>Crafted with devotion by a couple who just missed their old Minecraft worlds. ♥</sub>
</p>
