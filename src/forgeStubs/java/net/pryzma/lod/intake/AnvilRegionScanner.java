package net.pryzma.lod.intake;

import net.pryzma.lod.config.LodConfig;
import net.pryzma.lod.data.LodChunk;
import net.pryzma.lod.data.LodColumn;
import net.pryzma.lod.data.LodMaterial;
import net.pryzma.lod.data.LodWorldStorage;
import net.pryzma.lod.exec.PryzmaLodExecutor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.storage.LevelResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Inflater;

/**
 * Ultra-fast, non-blocking Anvil region (.mca) scanner.
 * Reads packed WORLD_SURFACE and OCEAN_FLOOR heightmaps directly from region files
 * on disk in a background daemon worker, instantly discovering all distant terrain
 * previously generated or explored in the world.
 */
public final class AnvilRegionScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final byte[] TAG_WORLD_SURFACE = "WORLD_SURFACE".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TAG_OCEAN_FLOOR = "OCEAN_FLOOR".getBytes(StandardCharsets.US_ASCII);
    private static final AtomicBoolean SCANNING = new AtomicBoolean(false);

    private static final ThreadLocal<byte[]> INFLATE_BUFFER = ThreadLocal.withInitial(() -> new byte[131072]);
    private static final ThreadLocal<Inflater> INFLATER = ThreadLocal.withInitial(Inflater::new);

    private AnvilRegionScanner() {
    }

    public static void triggerScan(double camX, double camZ) {
        if (!LodConfig.isEnabled() || SCANNING.get()) {
            return;
        }

        Path regionDir = resolveRegionDir();
        if (regionDir == null) {
            return;
        }

        File dir = regionDir.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.startsWith("r.") && name.endsWith(".mca"));
        if (files == null || files.length == 0) {
            return;
        }

        if (!SCANNING.compareAndSet(false, true)) {
            return;
        }

        int camChunkX = ((int) camX) >> 4;
        int camChunkZ = ((int) camZ) >> 4;
        int camRegionX = camChunkX >> 5;
        int camRegionZ = camChunkZ >> 5;

        // Sort region files by proximity to the player
        Arrays.sort(files, Comparator.comparingInt(f -> {
            int[] coords = parseRegionCoords(f.getName());
            int dx = coords[0] - camRegionX;
            int dz = coords[1] - camRegionZ;
            return dx * dx + dz * dz;
        }));

        PryzmaLodExecutor.execute(() -> {
            try {
                LOGGER.info("Starting background Anvil region scan for native LOD (found {} region files)", files.length);
                int totalLoaded = 0;
                int maxRegions = 64; // prioritize the 64 closest regions (covers ~1000 chunks radius)

                for (int i = 0; i < Math.min(files.length, maxRegions); i++) {
                    File file = files[i];
                    int[] coords = parseRegionCoords(file.getName());
                    int rx = coords[0];
                    int rz = coords[1];

                    int loadedInRegion = scanRegionFile(file, rx, rz);
                    totalLoaded += loadedInRegion;
                }
                LOGGER.info("Completed Anvil region scan: loaded {} distant chunks into LOD storage", totalLoaded);
            } catch (Throwable t) {
                LOGGER.warn("Anvil region scan interrupted", t);
            } finally {
                SCANNING.set(false);
            }
        });
    }

    private static int scanRegionFile(File file, int regionX, int regionZ) {
        if (file.length() < 8192) {
            return 0;
        }

        LodWorldStorage storage = LodWorldStorage.get();
        int loaded = 0;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[4096];
            raf.readFully(header);

            byte[] chunkBuffer = new byte[65536];

            for (int i = 0; i < 1024; i++) {
                int off = ((header[i * 4] & 0xFF) << 16) | ((header[i * 4 + 1] & 0xFF) << 8) | (header[i * 4 + 2] & 0xFF);
                if (off <= 0) {
                    continue;
                }

                int chunkX = (regionX << 5) | (i & 31);
                int chunkZ = (regionZ << 5) | (i >>> 5);

                if (storage.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                raf.seek((long) off * 4096);
                int length = raf.readInt();
                if (length <= 1 || length > 1000000) {
                    continue;
                }

                byte compression = raf.readByte();
                if (compression != 2) { // 2 = ZLIB
                    continue;
                }

                if (length - 1 > chunkBuffer.length) {
                    chunkBuffer = new byte[length * 2];
                }
                raf.readFully(chunkBuffer, 0, length - 1);

                // Decompress
                byte[] decomp = INFLATE_BUFFER.get();
                Inflater inflater = INFLATER.get();
                inflater.reset();
                inflater.setInput(chunkBuffer, 0, length - 1);

                int decompLen = 0;
                while (!inflater.finished()) {
                    int count = inflater.inflate(decomp, decompLen, decomp.length - decompLen);
                    if (count == 0 && inflater.needsInput()) {
                        break;
                    }
                    decompLen += count;
                    if (decompLen == decomp.length) {
                        byte[] expanded = new byte[decomp.length * 2];
                        System.arraycopy(decomp, 0, expanded, 0, decompLen);
                        decomp = expanded;
                        INFLATE_BUFFER.set(decomp);
                    }
                }

                LodChunk lodChunk = parseChunkHeightmaps(chunkX, chunkZ, decomp, decompLen);
                if (lodChunk != null) {
                    storage.putChunk(lodChunk);
                    loaded++;
                }
            }
        } catch (Throwable ignored) {
        }
        return loaded;
    }

    private static LodChunk parseChunkHeightmaps(int chunkX, int chunkZ, byte[] data, int length) {
        long[] ws = extractLongArray(data, length, TAG_WORLD_SURFACE);
        if (ws == null || ws.length < 37) {
            return null;
        }

        long[] of = extractLongArray(data, length, TAG_OCEAN_FLOOR);

        LodChunk chunk = new LodChunk(chunkX, chunkZ);
        int minBuildHeight = -64; // Standard overworld minimum height in 1.18+

        for (int cell = 0; cell < 256; cell++) {
            int x = cell & 15;
            int z = cell >>> 4;
            int lIdx = cell / 7;
            int bitOff = (cell % 7) * 9;

            int ty = (int) ((ws[lIdx] >>> bitOff) & 0x1FFL) + minBuildHeight;
            int wy = -128;
            int flags = 0;
            int mat = LodMaterial.GRASS;

            if (of != null && of.length >= 37) {
                int ofy = (int) ((of[lIdx] >>> bitOff) & 0x1FFL) + minBuildHeight;
                if (ty > ofy) {
                    // Water exists between ofy and ty
                    wy = ty;
                    ty = ofy;
                    mat = LodMaterial.WATER;
                    flags |= LodColumn.FLAG_HAS_WATER;
                }
            } else if (ty <= 62) {
                // Fallback sea level heuristic
                wy = 63;
                flags |= LodColumn.FLAG_HAS_WATER;
                mat = LodMaterial.WATER;
            }

            if ((flags & LodColumn.FLAG_HAS_WATER) == 0) {
                if (ty > 110) {
                    mat = LodMaterial.STONE;
                } else if (ty == 63 || ty == 62) {
                    mat = LodMaterial.SAND;
                }
            }

            long col = LodColumn.pack(ty, wy, mat, 0, 15, 0, flags);
            chunk.setColumn(x, z, col);
        }

        return chunk;
    }

    private static long[] extractLongArray(byte[] data, int length, byte[] tagName) {
        int pos = findBytes(data, length, tagName);
        if (pos == -1) {
            return null;
        }

        int start = pos + tagName.length;
        if (start + 4 > length) {
            return null;
        }

        int arrayLen = ((data[start] & 0xFF) << 24)
                | ((data[start + 1] & 0xFF) << 16)
                | ((data[start + 2] & 0xFF) << 8)
                | (data[start + 3] & 0xFF);

        if (arrayLen < 37 || arrayLen > 100) {
            return null;
        }

        int dataStart = start + 4;
        if (dataStart + arrayLen * 8 > length) {
            return null;
        }

        long[] result = new long[arrayLen];
        for (int i = 0; i < arrayLen; i++) {
            int off = dataStart + i * 8;
            result[i] = (((long) data[off] & 0xFF) << 56)
                    | (((long) data[off + 1] & 0xFF) << 48)
                    | (((long) data[off + 2] & 0xFF) << 40)
                    | (((long) data[off + 3] & 0xFF) << 32)
                    | (((long) data[off + 4] & 0xFF) << 24)
                    | (((long) data[off + 5] & 0xFF) << 16)
                    | (((long) data[off + 6] & 0xFF) << 8)
                    | ((long) data[off + 7] & 0xFF);
        }
        return result;
    }

    private static int findBytes(byte[] data, int length, byte[] target) {
        int max = length - target.length;
        for (int i = 0; i <= max; i++) {
            if (data[i] == target[0]) {
                boolean match = true;
                for (int j = 1; j < target.length; j++) {
                    if (data[i + j] != target[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int[] parseRegionCoords(String filename) {
        try {
            String[] parts = filename.split("\\.");
            return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (Throwable t) {
            return new int[]{0, 0};
        }
    }

    private static Path resolveRegionDir() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return null;
            }
            Object server = mc.getSingleplayerServer();
            if (server != null) {
                ClientLevel level = mc.level;
                if (level != null) {
                    String dimKey = level.dimension().location().getPath();
                    java.lang.reflect.Method getWorldPath = server.getClass().getMethod("getWorldPath", LevelResource.class);
                    Path root = (Path) getWorldPath.invoke(server, LevelResource.ROOT);
                    if ("the_nether".equals(dimKey)) {
                        return root.resolve("dimensions/minecraft/the_nether/region");
                    } else if ("the_end".equals(dimKey)) {
                        return root.resolve("dimensions/minecraft/the_end/region");
                    } else {
                        return root.resolve("region");
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
