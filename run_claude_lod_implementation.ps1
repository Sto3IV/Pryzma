$ErrorActionPreference = "Stop"

$sysPrompt = @"
You are a senior graphics programmer and low-level Java systems engineer specializing in Minecraft rendering engines (Sodium, Iris, OpenGL 4.6).
Your task is given by the project lead architect. You execute directly with surgical precision, write clean, maintainable, production-ready Java code, and verify all changes through compilation.
Do NOT use placeholders, TODOs, or pseudocode. Every class and method must be completely implemented and tested.
Do NOT overcomplicate memory architectures: the system runs on Java 21 with Generational ZGC and 64GB of RAM, so clean Java data structures in the heap are preferred over fragile off-heap hacks.
"@

$taskPrompt = @"
PROJECT: Pryzma (Sodium/Iris fork for Minecraft 1.21.1 / NeoForge 21.1.250)
TARGET PLATFORM: AMD Ryzen 9 5900X, Radeon RX 6950 XT (OpenGL 4.6 Core), Java 21 with Generational ZGC.

ARCHITECTURAL DIRECTIVE: IMPLEMENT NATIVE CONTINUOUS LOD (FARLANDS) FOUNDATION
Instead of relying on external mods like Distant Horizons, we are implementing a native Level-of-Detail (LOD) system directly inside Pryzma.

CRITICAL CONSTRAINTS:
1. Do NOT overengineer memory bit-packing. RAM optimization is not a priority; with 64GB RAM and ZGC, standard Java collections (e.g. fastutil Long2ObjectMap) and NIO ByteBuffers are preferred for stability and clean maintenance.
2. The implementation must compile cleanly with `./gradlew compileJava test --no-daemon`.

TASKS TO EXECUTE:

1. CONFIGURATION & UI:
   - In the settings/configuration system (e.g. in `net.pryzma.config` / `PrVideoSettingsScreen`), add a setting:
     `Farlands LOD Distance`: Off, 32, 64, 96, 128 chunks (default: Off or 64).
   - Ensure the setting persists and is accessible from the video settings screen.

2. LOD DATA STRUCTURE & CACHE (`net.pryzma.lod` package):
   - Create `LodChunkData`:
     Stores 16x16 column data for a chunk:
     - `short[] heightSolid`: surface height (-64..320)
     - `short[] heightFluid`: fluid surface height (if present)
     - `int[] blockStateId` or `int[] materialId`: surface block state or color tint
     - `byte[] skyLight`: surface skylight (0..15)
     - `byte[] blockLight`: surface block light (0..15)
   - Create `LodDataStore`:
     A thread-safe repository managing `LodChunkData` by chunk coordinates `(chunkX, chunkZ)`.
     Supports querying rectangular/circular areas around the player camera up to the configured LOD radius.

3. DATA EXTRACTION / WORLD SCANNER:
   - Create `LodWorldScanner`:
     Extracts surface column data from active loaded chunks or async Anvil/MCA reading.
     When a chunk is loaded or meshed, extract surface heightmap (`MOTION_BLOCKING`), top solid block state, and light values into `LodChunkData`.

4. ADAPTIVE LOD MESHER (`NativeLodMesher`):
   - For distant terrain (e.g. 16x16 or 32x32 chunk macro-sectors), mesh the heightmap columns into a compact triangle/quad mesh.
   - Implement quad merging for adjacent flat columns of identical material to keep triangle counts minimal.
   - Implement vertical skirts (`skirt`) on cliff edges where neighboring columns have height differences, preventing holes in geometry.
   - Pack vertices into a compact format compatible with the rendering pipeline:
     Position (x, y, z), Normal (nx, ny, nz), Color/Tint (RGBA), Lightmap (u, v).

5. RENDER PASS (`NativeLodRenderer`):
   - Create a clean rendering pass that binds the LOD VAO/VBO, sets projection/modelview matrices, binds lightmap texture, and renders the macro-sectors.
   - Ensure depth test is properly configured (`GL_LEQUAL` with depth write enabled or appropriate stage) so vanilla near terrain renders correctly with zero Z-fighting.
   - Hook this renderer into `LevelRenderer` or Pryzma's rendering pipeline.

6. COMPILATION & VERIFICATION:
   - Run `./gradlew compileJava --no-daemon` and fix any compiler issues, import mismatches, or syntax errors.
   - Run `./gradlew test --no-daemon` to ensure all existing tests pass.
   - Write a detailed summary report to `CLAUDE_LOD_IMPLEMENTATION_REPORT.md` documenting all classes created/modified and test outputs.
"@

Write-Host "Starting Claude CLI execution with Opus 5 and max effort..."
& "c:\users\sato_\.local\bin\claude.exe" --model opus --effort max -p --permission-mode acceptEdits --system-prompt $sysPrompt $taskPrompt
