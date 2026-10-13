package net.pryzma.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/** Farlands LOD data store: capture order, sector versions, snapshots, area queries and eviction. */
class LodDataStoreTest {
    private static LodChunkData chunk(int x, int z, long sequence) {
        LodChunkData data = new LodChunkData(x, z, sequence);
        data.setColumn(0, 64, 1, LodChunkData.NO_FLUID, 0, 15, 0);
        return data;
    }

    /** Stores every chunk with {@code -half <= x, z <= half}. */
    private static LodDataStore square(int half) {
        LodDataStore store = new LodDataStore();
        for (int z = -half; z <= half; z++) {
            for (int x = -half; x <= half; x++) {
                store.put(chunk(x, z, 1L));
            }
        }
        return store;
    }

    @Test
    void keysPackLikeChunkPosAndRoundTrip() {
        int[] coords = {0, 1, -1, 15, -16, 1875000, -1875000, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int x : coords) {
            for (int z : coords) {
                long key = LodDataStore.key(x, z);
                assertEquals((long) x & 0xFFFFFFFFL | (long) z << 32, key);
                assertEquals(x, LodDataStore.keyX(key));
                assertEquals(z, LodDataStore.keyZ(key));
            }
        }
        assertEquals(LodDataStore.key(-1, -1), LodDataStore.sectorKeyOf(-1, -16));
        assertEquals(LodDataStore.key(0, -1), LodDataStore.sectorKeyOf(15, -1));
        assertEquals(-1, LodDataStore.sectorCoord(-16));
        assertEquals(-2, LodDataStore.sectorCoord(-17));
    }

    @Test
    void aNewerCaptureReplacesAnOlderOneButNeverTheReverse() {
        LodDataStore store = new LodDataStore();
        LodChunkData live = chunk(2, 3, 5L);
        assertTrue(store.put(live));
        assertFalse(store.put(chunk(2, 3, 4L)), "an older capture loses");
        assertFalse(store.put(chunk(2, 3, 0L)), "a saved-world read never replaces a live capture");
        assertSame(live, store.get(2, 3));
        LodChunkData newer = chunk(2, 3, 6L);
        assertTrue(store.put(newer));
        assertSame(newer, store.get(2, 3));
        assertEquals(1, store.size());
        assertNull(store.get(3, 2));
    }

    @Test
    void interiorChunksTouchOnlyTheirSector() {
        LodDataStore store = new LodDataStore();
        assertEquals(0, store.sectorVersion(0, 0), "untouched");
        store.put(chunk(5, 5, 1L));
        assertTrue(store.sectorVersion(0, 0) > 0);
        for (int[] n : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}}) {
            assertEquals(0, store.sectorVersion(n[0], n[1]), "neighbour " + n[0] + ", " + n[1]);
        }
    }

    @Test
    void borderChunksAlsoTouchTheNeighbourWhoseSkirtsReadThem() {
        LodDataStore store = new LodDataStore();
        store.put(chunk(15, 15, 1L));
        assertTrue(store.sectorVersion(0, 0) > 0);
        assertTrue(store.sectorVersion(1, 0) > 0, "east neighbour reads column x = 15");
        assertTrue(store.sectorVersion(0, 1) > 0, "south neighbour reads row z = 15");
        assertEquals(0, store.sectorVersion(1, 1), "skirts never read diagonal neighbours");
        assertEquals(0, store.sectorVersion(-1, 0));

        LodDataStore negative = new LodDataStore();
        negative.put(chunk(-1, -16, 1L));
        assertTrue(negative.sectorVersion(-1, -1) > 0, "chunk -1, -16 lies in sector -1, -1");
        assertTrue(negative.sectorVersion(0, -1) > 0, "its east edge borders sector 0");
        assertTrue(negative.sectorVersion(-1, -2) > 0, "its north edge borders sector -2");
        assertEquals(0, negative.sectorVersion(-2, -1));
        assertEquals(0, negative.sectorVersion(-1, 0));
    }

    @Test
    void everyChangeAdvancesTheVersion() {
        LodDataStore store = new LodDataStore();
        store.put(chunk(3, 3, 1L));
        int first = store.sectorVersion(0, 0);
        store.put(chunk(3, 3, 2L));
        int second = store.sectorVersion(0, 0);
        assertTrue(second > first, "replaced");
        assertTrue(store.remove(3, 3));
        assertTrue(store.sectorVersion(0, 0) > second, "removed");
        assertFalse(store.remove(3, 3));
    }

    @Test
    void sectorDataCountsItsChunks() {
        LodDataStore store = new LodDataStore();
        assertFalse(store.hasSectorData(0, 0));
        store.put(chunk(3, 3, 1L));
        store.put(chunk(4, 4, 1L));
        store.put(chunk(4, 4, 2L));
        assertTrue(store.hasSectorData(0, 0));
        assertTrue(store.remove(3, 3));
        assertTrue(store.hasSectorData(0, 0), "one chunk left");
        assertTrue(store.remove(4, 4));
        assertFalse(store.hasSectorData(0, 0));
        assertFalse(store.hasSectorData(-1, 0));
    }

    @Test
    void snapshotHoldsTheSectorAndItsOneChunkBorder() {
        LodDataStore store = new LodDataStore();
        int[][] inside = {{-1, -1}, {0, 0}, {15, 15}, {16, 8}, {8, 16}, {-1, 16}};
        for (int[] c : inside) {
            store.put(chunk(c[0], c[1], 1L));
        }
        store.put(chunk(17, 0, 1L));
        store.put(chunk(-2, 5, 1L));
        LodDataStore.SectorSnapshot snapshot = store.snapshotSector(0, 0);
        assertEquals(LodDataStore.SNAPSHOT_SPAN * LodDataStore.SNAPSHOT_SPAN, snapshot.chunks().length);
        assertEquals(store.sectorVersion(0, 0), snapshot.version());
        for (int[] c : inside) {
            assertSame(store.get(c[0], c[1]), snapshot.chunk(c[0], c[1]), "chunk " + c[0] + ", " + c[1]);
        }
        int present = 0;
        for (LodChunkData data : snapshot.chunks()) {
            if (data != null) {
                present++;
            }
        }
        assertEquals(inside.length, present, "chunks two away from the sector are not part of it");

        LodDataStore.SectorSnapshot west = store.snapshotSector(-1, 0);
        assertSame(store.get(-2, 5), west.chunk(14, 5));
        assertSame(store.get(0, 0), west.chunk(16, 0), "sector 0's first column is sector -1's east border");
    }

    @Test
    void rectangularAndCircularQueries() {
        LodDataStore store = square(4);
        assertEquals(81, store.size());
        assertEquals(9, store.queryRect(-1, -1, 1, 1).size(), "small area: walks the rectangle");
        assertEquals(81, store.queryRect(-10, -10, 10, 10).size(), "large area: walks the store");
        assertEquals(3, store.queryRect(4, -1, 9, 1).size(), "clipped by the stored area");
        assertTrue(store.queryRect(1, 1, 0, 0).isEmpty(), "an inverted rectangle is empty");

        List<LodChunkData> disc = store.queryRadius(0, 0, 2);
        // dx^2 + dz^2 <= 4: the centre, 4 at distance 1, 4 diagonals at sqrt 2, 4 at distance 2.
        assertEquals(13, disc.size());
        Set<Long> keys = new HashSet<>();
        for (LodChunkData data : disc) {
            assertTrue(LodDataStore.inRadius(data.chunkX(), data.chunkZ(), 0, 0, 2));
            assertTrue(keys.add(data.key()), "no duplicates");
        }
        assertTrue(LodDataStore.inRadius(2, 0, 0, 0, 2));
        assertFalse(LodDataStore.inRadius(2, 1, 0, 0, 2));
    }

    @Test
    void evictionDropsEverythingOutsideTheRadius() {
        LodDataStore store = square(4);
        int before = store.sectorVersion(-1, -1);
        assertEquals(81 - 13, store.evictOutside(0, 0, 2));
        assertEquals(13, store.size());
        assertTrue(store.contains(2, 0));
        assertFalse(store.contains(2, 1));
        assertFalse(store.contains(-4, -4));
        assertTrue(store.sectorVersion(-1, -1) > before, "evicted chunks touch their sectors");
        assertEquals(0, store.evictOutside(0, 0, 2));
        store.clear();
        assertEquals(0, store.size());
        assertFalse(store.hasSectorData(0, 0));
        assertEquals(0, store.sectorVersion(0, 0));
    }

    @Test
    void concurrentWritersAndSnapshotsAgree() throws Exception {
        LodDataStore store = new LodDataStore();
        int threads = 4;
        int perThread = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?>[] writers = new Future<?>[threads];
            for (int t = 0; t < threads; t++) {
                int offset = t;
                writers[t] = pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        // Every writer covers the same 32 x 32 chunks with its own sequence numbers.
                        int index = i % 1024;
                        store.put(chunk(index % 32 - 16, index / 32 - 16, (long) i * threads + offset));
                    }
                    return null;
                });
            }
            Future<?> reader = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 500; i++) {
                    for (LodChunkData data : store.snapshotSector(-1, -1).chunks()) {
                        if (data != null) {
                            assertTrue(data.chunkX() >= -17 && data.chunkX() <= 0);
                        }
                    }
                }
                return null;
            });
            start.countDown();
            for (Future<?> writer : writers) {
                writer.get(30, TimeUnit.SECONDS);
            }
            reader.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1024, store.size());
        assertTrue(store.hasSectorData(-1, -1));
        assertTrue(store.hasSectorData(0, 0));
        // Whatever the interleaving, each chunk ends with the highest sequence any writer gave it: the last
        // iteration that wrote it, from the writer with the highest offset.
        for (int index = 0; index < 1024; index++) {
            int last = index + 1024 * ((perThread - 1 - index) / 1024);
            long expected = (long) last * threads + threads - 1;
            assertEquals(expected, store.get(index % 32 - 16, index / 32 - 16).sequence(), "chunk " + index);
        }
    }
}
