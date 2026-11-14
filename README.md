<p align="center">
  <img src="https://raw.githubusercontent.com/Sto3IV/archive-of-images/main/pryzma_8bit.png" width="280" alt="Pryzma Logo">
<br>
  <a href="https://curseforge.com/minecraft/mc-mods/pryzma"><img src="https://img.shields.io/badge/CurseForge-Available-f16436?style=flat&logo=curseforge&logoColor=ffffff&labelColor=16181c" alt="CurseForge"></a>

<!-- I apologize for the HTML but it looks so pretty :) -->

About
=====

**Pryzma** is an all-in-one visual engine, autonomous GLSL shader pipeline, and graphics optimization mod for [NeoForge 1.21.1](https://neoforged.net/).

It was born out of a simple, personal desire: my partner and I wanted to play on a private server while keeping the breathtaking visual fidelity of our treasured 10-year-old texture packs (from the classic OptiFine and MCPatcher era) without wrestling with broken configs or endless format conversions.

Pryzma restores the full beauty of classic resource packs — connected textures, custom item models, dynamic skies, animated lightmaps, and real-time shaders — in **one single, elegant JAR**. No mod salad. No conflicting loaders. Just drop it in and play.

Installation
============

1. Install [NeoForge](https://neoforged.net/).
2. Download the latest release of **Pryzma** from our [CurseForge](https://curseforge.com/minecraft/mc-mods/pryzma) or [GitHub Releases](https://github.com/Sto3IV/Pryzma/releases) page.
3. Place `pryzma-x.x.X.jar` into your `.minecraft/mods/` directory.
4. Drop your favorite resource packs and shaderpacks into their respective folders, and enjoy!

Screenshots
===========

![Hardware F3 Telemetry Cache](https://raw.githubusercontent.com/Sto3IV/archive-of-images/main/pryzma_f3_panel.png)
*Lightweight runtime overhead delivering uncapped multi-hundred framerate ceilings and sub-7ms tick times under native NeoForge 1.21.1.*

![Shader Compatibility](https://github.com/user-attachments/assets/9c8a03e2-0a82-4359-86e7-317d9d826baa)
*Full compatibility with modern GLSL shaderpacks, featuring volumetric atmospheres, dynamic sun shafts, and soft real-time shadows.*

![Seamless BetterGrass Turf Connectivity](https://raw.githubusercontent.com/Sto3IV/archive-of-images/main/pryzma_better_grass.png)
*Native BetterGrass rendering seamlessly wrapping grass turf across sloped terrain elevations without unsightly dirt cuts.*

Feature Matrix
==============

| Feature | Vanilla | Fragmented Setup | Pryzma |
| :--- | :---: | :---: | :---: |
| **Shaders (GLSL)** | ❌ None | ⚠️ Extra loaders | ✅ Built-in Native |
| **OptiFine Textures format** | ❌ None | ⚠️ 8+ separate mods | ✅ 100% Native |
| **MCPatcher Format** | ❌ None | ❌ Unsupported | ✅ Supported |
| **Connected Textures (CTM)** | ❌ None | ⚠️ Continuity / Fusion | ✅ Native Full CTM |
| **Custom Items (CIT)** | ❌ None | ⚠️ CIT Resewn | ✅ Native Full CIT |
| **Custom Entity Models (CEM)**| ❌ None | ⚠️ EMF | ✅ Native CEM |
| **Random & Biome Mobs** | ❌ None | ⚠️ ETF | ✅ Native Random Mobs |
| **Custom Skies & Domes** | ❌ Vanilla | ⚠️ Skyboxes Mod | ✅ Native Multi-Layer |
| **Custom Biome Colormaps** | ❌ Static | ⚠️ Polytone | ✅ Native Colormaps |
| **Custom Lightmaps** | ❌ Static | ⚠️ Polytone (partial) | ✅ Native Dynamic |
| **Handheld Dynamic Lights** | ❌ None | ⚠️ LambDynamicLights | ✅ Included |
| **Cinematic Smooth Zoom** | ⚠️ Spyglass | ⚠️ Zoomify | ✅ Native ('C' Key) |
| **Better Grass & Snow** | ❌ None | ⚠️ 2 separate mods | ✅ Native Options |
| **No Realms button** | ❌ Cluttered | ⚠️ Separate tweaks | ✅ Included |
| **Multithread Meshing** | ⚠️ Basic | ✅ External renderer | ✅ Native Worker Pool |
| **Total JARs Needed** | **0** | **15–20+ JARs** | **1 Single JAR** |
| **Architecture** | Baseline | Competing mixins | **Clean Monolith** |

Support & Philosophy
====================

Think of Pryzma as a **warm, quiet campfire**. We are not a commercial live-service studio, and **we will not be taking feature requests for the foreseeable future.** Our priority is strictly on long-term stability and keeping what is already here rock-solid.

If you encounter a genuine bug or a broken texture, please feel free to open a ticket on our [GitHub Issue Tracker](https://github.com/Sto3IV/Pryzma/issues). Please keep in mind that we both work real full-time jobs, but we quietly review issues during our free weekends.

A Warm Cup of Tea
=================

Pryzma is 100% free, offline, and telemetry-free. There are no paywalled features, locked builds, or artificial tiers.

Both of us have regular full-time jobs, and we never expect anything in return. But if Pryzma brought back a spark of that warm, timeless magic to your worlds and you'd simply like to say thank you or treat us to a hot cup of tea for our quiet weekend evenings, you are more than welcome to leave a small tip:
* **Boosty:** [Tip on Boosty](https://boosty.to/sto3iv)
* **Ko-fi:** [Buy a Tea on Ko-fi](https://ko-fi.com/sto3iv)

Modpacks
========

Pryzma can be used freely in any public or private modpack without requiring special permission. But paywall modpacks are highly prohibited!

Platform Distribution Notice
============================

Pryzma is officially published and maintained exclusively on [CurseForge](https://curseforge.com/minecraft/mc-mods/pryzma) and [GitHub Releases](https://github.com/Sto3IV/Pryzma/releases).

> [!CAUTION]
> **Strict Prohibition on Re-uploading & Third-Party Platforms:**
> Redistribution, re-hosting, mirroring, or publishing Pryzma (including binary JAR releases, the project name "Pryzma", icons, and branding) to third-party distribution platforms — **specifically including Modrinth** — is strictly forbidden without our prior written consent.
> 
> We have not authorized, and do not endorse, any "unofficial" ports, mirrors, or re-uploads on Modrinth or other sites. Any unauthorized uploads will be treated as copyright infringement and will receive an immediate DMCA takedown notice.

Licensing & Brand Policy
========================

Pryzma is released under an open-source composite license:
* **Pryzma Core, Emulation & Optimizations:** [MIT License](LICENSE)
* **Native GLSL Shader Pipeline (`net.pryzma.shader`):** GNU LGPL-3.0-only
* **Project Name, Branding, Logos & Binary Distribution:** All Rights Reserved. See [Section 6 of LICENSE](LICENSE) for our platform distribution and re-upload restrictions.

---
<p align="center">
  <sub>Crafted with devotion by a couple who just missed their old Minecraft worlds. ♥</sub>
</p>
