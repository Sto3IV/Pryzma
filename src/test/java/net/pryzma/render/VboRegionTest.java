package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class VboRegionTest {
    private static final int VERTEX_BYTES = 32;

    /** A region whose buffer is simulated: one cell per vertex holding the serial of the upload that wrote it. */
    static final class SimRegion extends VboRegion {
        int[] cells = new int[getCapacity()];
        int serial;
        int copies;

        SimRegion() {
            super(null, 0L, VERTEX_BYTES);
        }

        @Override
        void uploadGl(long offset, ByteBuffer data) {
            int from = (int) (offset / VERTEX_BYTES);
            Arrays.fill(cells, from, from + data.remaining() / VERTEX_BYTES, serial);
        }

        @Override
        void copyGl(long from, long to, long bytes) {
            assertTrue(from + bytes <= to || to + bytes <= from, "glCopyBufferSubData ranges overlap");
            System.arraycopy(cells, (int) (from / VERTEX_BYTES), cells, (int) (to / VERTEX_BYTES), (int) (bytes / VERTEX_BYTES));
            copies++;
        }

        @Override
        void resizeGl(long bytesNew, long bytesKept) {
            int[] grown = new int[(int) (bytesNew / VERTEX_BYTES)];
            System.arraycopy(cells, 0, grown, 0, (int) (bytesKept / VERTEX_BYTES));
            cells = grown;
        }

        void upload(VboRange range, int serial, int vertices) {
            this.serial = serial;
            bufferData(ByteBuffer.allocate(vertices * VERTEX_BYTES), range);
        }
    }

    /** Every live range holds exactly its last upload; ranges are disjoint, list-ordered by position and inside the buffer. */
    private static void assertConsistent(SimRegion region, VboRange[] ranges, int[] serials) {
        List<VboRange> live = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < ranges.length; i++) {
            VboRange range = ranges[i];
            if (range.getPosition() < 0) {
                assertEquals(0, range.getSize(), "released range keeps a size");
                continue;
            }
            live.add(range);
            used += range.getSize();
            for (int v = range.getPosition(); v < range.getPositionNext(); v++) {
                assertEquals(serials[i], region.cells[v], "range " + i + " " + range + " lost its data at vertex " + v);
            }
            VboRange prev = range.getPrev();
            assertTrue(prev == null || prev.getPositionNext() <= range.getPosition(), "list out of position order at " + range);
        }
        live.sort(Comparator.comparingInt(VboRange::getPosition));
        for (int i = 1; i < live.size(); i++) {
            assertTrue(live.get(i - 1).getPositionNext() <= live.get(i).getPosition(), "overlap " + live.get(i - 1) + " " + live.get(i));
        }
        assertEquals(used, region.getSizeUsed());
        assertEquals(live.isEmpty(), region.isEmpty());
        if (!live.isEmpty()) {
            assertTrue(region.getPositionTop() >= live.get(live.size() - 1).getPositionNext());
        }
        assertTrue(region.getCapacity() >= region.getPositionTop());
        assertEquals(region.getCapacity(), region.cells.length);
    }

    private static VboRange[] ranges(int count) {
        VboRange[] ranges = new VboRange[count];
        Arrays.setAll(ranges, i -> new VboRange());
        return ranges;
    }

    @Test
    void randomUploadsReleasesAndCompactionKeepEveryRangeIntact() {
        Random random = new Random(0x5EED);
        SimRegion region = new SimRegion();
        VboRange[] ranges = ranges(48);
        int[] serials = new int[ranges.length];
        int serial = 0;
        for (int step = 0; step < 20_000; step++) {
            int i = random.nextInt(ranges.length);
            int op = random.nextInt(10);
            if (op < 6) {
                serials[i] = ++serial;
                region.upload(ranges[i], serials[i], 4 * (1 + random.nextInt(op < 3 ? 64 : 1024)));
            } else if (op < 8) {
                region.release(ranges[i]);
            } else {
                region.compactRanges(1 + random.nextInt(3));
            }
            assertConsistent(region, ranges, serials);
        }
        assertTrue(region.copies > 0, "compaction never ran");
    }

    @Test
    void compactionConvergesToADenseBuffer() {
        Random random = new Random(42);
        SimRegion region = new SimRegion();
        VboRange[] ranges = ranges(64);
        int[] serials = new int[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            serials[i] = i + 1;
            region.upload(ranges[i], serials[i], 4 * (1 + random.nextInt(256)));
        }
        for (int i = 0; i < ranges.length; i += 2) {
            region.release(ranges[i]);
        }
        assertTrue(region.getPositionTop() > region.getSizeUsed());
        for (int calls = 0; calls < 10_000 && region.getPositionTop() > region.getSizeUsed(); calls++) {
            region.compactRanges(1);
            assertConsistent(region, ranges, serials);
        }
        assertEquals(region.getSizeUsed(), region.getPositionTop());
    }

    /** Sizes keep the unused span under a tenth, so no compaction step moves anything afterwards. */
    @Test
    void shrinkStaysInPlaceGrowthMovesToTheTop() {
        SimRegion region = new SimRegion();
        VboRange[] ranges = ranges(2);
        int[] serials = {1, 2};
        region.upload(ranges[0], 1, 400);
        region.upload(ranges[1], 2, 4000);
        serials[0] = 3;
        region.upload(ranges[0], 3, 396);
        assertEquals(0, ranges[0].getPosition());
        assertEquals(396, ranges[0].getSize());
        assertConsistent(region, ranges, serials);
        serials[0] = 4;
        region.upload(ranges[0], 4, 800);
        assertEquals(4400, ranges[0].getPosition());
        assertEquals(5200, region.getPositionTop());
        assertEquals(0, region.copies);
        assertConsistent(region, ranges, serials);
    }

    @Test
    void expansionKeepsDataAndReleasingEverythingResetsTheTop() {
        SimRegion region = new SimRegion();
        VboRange[] ranges = ranges(8);
        int[] serials = new int[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            serials[i] = i + 1;
            region.upload(ranges[i], serials[i], 3000);
        }
        assertTrue(region.getCapacity() >= 24_000);
        assertConsistent(region, ranges, serials);
        for (VboRange range : ranges) {
            region.release(range);
        }
        assertTrue(region.isEmpty());
        assertEquals(0, region.getPositionTop());
        assertEquals(0, region.getSizeUsed());
        serials[0] = 99;
        region.upload(ranges[0], 99, 8);
        assertEquals(0, ranges[0].getPosition());
    }

    @Test
    void translateMovesOnlyPositions() {
        for (ByteOrder order : new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
            ByteBuffer vertices = ByteBuffer.allocate(3 * VERTEX_BYTES).order(order);
            for (int v = 0; v < 3; v++) {
                vertices.putFloat(v * VERTEX_BYTES, v);
                vertices.putFloat(v * VERTEX_BYTES + 4, 16.0F);
                vertices.putFloat(v * VERTEX_BYTES + 8, 0.5F);
                vertices.putInt(v * VERTEX_BYTES + 12, 0xCAFEBABE);
            }
            VboRegion.translate(vertices, 3, VERTEX_BYTES, 0, 112.0F, -64.0F, 16.0F);
            for (int v = 0; v < 3; v++) {
                assertEquals(v + 112.0F, vertices.getFloat(v * VERTEX_BYTES));
                assertEquals(-48.0F, vertices.getFloat(v * VERTEX_BYTES + 4));
                assertEquals(16.5F, vertices.getFloat(v * VERTEX_BYTES + 8));
                assertEquals(0xCAFEBABE, vertices.getInt(v * VERTEX_BYTES + 12));
            }
        }
    }
}
