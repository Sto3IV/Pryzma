package net.pryzma.detail;

import java.util.Arrays;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase B4: the decorator blocks one chunk worker deferred while meshing one section, and for each
 * layer they write to, the vertex count it held before they were replayed ({@code [core | decorators]}).
 * One instance per thread, reset at the start and the end of every compile; allocation-free once warm.
 */
public final class PrDeferredDetailQueue {
    /** One deferred {@code tesselateBlock} call. The position is copied: the caller's is a loop cursor. */
    public static final class Entry {
        public float x;
        public float y;
        public float z;
        public final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        public BlockState state;
        public BlockStateModel model;
        public long seed;
    }

    private static final ThreadLocal<PrDeferredDetailQueue> QUEUES = ThreadLocal.withInitial(PrDeferredDetailQueue::new);

    private final ObjectArrayList<Entry> entries = new ObjectArrayList<>();
    private int size;
    /** The layers with deferred blocks, at most the chunk layers, and the core vertex count of each. */
    private ChunkSectionLayer[] layers = new ChunkSectionLayer[3];
    private int[] cores = new int[3];
    private int layerCount;

    private PrDeferredDetailQueue() {
    }

    public static PrDeferredDetailQueue get() {
        return QUEUES.get();
    }

    public void add(float x, float y, float z, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
        if (size == entries.size()) {
            entries.add(new Entry());
        }
        Entry e = entries.get(size++);
        e.x = x;
        e.y = y;
        e.z = z;
        e.pos.set(pos);
        e.state = state;
        e.model = model;
        e.seed = seed;
    }

    public void recordLayer(ChunkSectionLayer layer) {
        if (indexOf(layer) < 0) {
            if (layerCount == layers.length) {
                layers = Arrays.copyOf(layers, layerCount * 2);
                cores = Arrays.copyOf(cores, layerCount * 2);
            }
            layers[layerCount] = layer;
            cores[layerCount++] = 0;
        }
    }

    public int size() {
        return size;
    }

    public Entry get(int index) {
        return entries.get(index);
    }

    public int layerCount() {
        return layerCount;
    }

    public ChunkSectionLayer layer(int index) {
        return layers[index];
    }

    /** The vertices layer {@code index} held when its decorators were replayed; 0 when they opened it. */
    public int core(int index) {
        return cores[index];
    }

    public void setCore(int index, int vertices) {
        cores[index] = vertices;
    }

    private int indexOf(ChunkSectionLayer type) {
        for (int i = 0; i < layerCount; i++) {
            if (layers[i] == type) {
                return i;
            }
        }
        return -1;
    }

    /** Drops every reference, so a worker parked in its pool keeps no block state or model data alive. */
    public void clear() {
        for (int i = 0; i < size; i++) {
            Entry e = entries.get(i);
            e.state = null;
            e.model = null;
        }
        size = 0;
        layerCount = 0;
    }
}
