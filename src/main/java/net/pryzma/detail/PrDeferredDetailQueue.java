package net.pryzma.detail;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.PoseStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Phase B4: the decorator blocks one chunk worker deferred while meshing one section, and for each
 * layer they write to, the vertex count it held before they were replayed ({@code [core | decorators]}).
 * One instance per thread, reset at the start and the end of every compile; allocation-free once warm.
 */
public final class PrDeferredDetailQueue {
    /** One deferred {@code renderBatched} call. The position is copied: the caller's is a loop cursor. */
    public static final class Entry {
        public BlockState state;
        public final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        public ModelData modelData;
        public RenderType renderType;
    }

    private static final ThreadLocal<PrDeferredDetailQueue> QUEUES = ThreadLocal.withInitial(PrDeferredDetailQueue::new);

    /** At identity between blocks: the replay pushes and pops it around each one. */
    public final PoseStack poseStack = new PoseStack();
    /** Same LCG as the compile's own; tesselateBlock reseeds it with {@code state.getSeed(pos)} before every getQuads. */
    public final RandomSource random = RandomSource.create(0L);

    private final ObjectArrayList<Entry> entries = new ObjectArrayList<>();
    private int size;
    /** The layers with deferred blocks, at most the five chunk layers, and the core vertex count of each. */
    private RenderType[] layers = new RenderType[5];
    private int[] cores = new int[5];
    private int layerCount;

    private PrDeferredDetailQueue() {
    }

    public static PrDeferredDetailQueue get() {
        return QUEUES.get();
    }

    public void add(BlockState state, BlockPos pos, ModelData modelData, RenderType renderType) {
        if (size == entries.size()) {
            entries.add(new Entry());
        }
        Entry e = entries.get(size++);
        e.state = state;
        e.pos.set(pos);
        e.modelData = modelData;
        e.renderType = renderType;
        if (indexOf(renderType) < 0) {
            if (layerCount == layers.length) {
                layers = Arrays.copyOf(layers, layerCount * 2);
                cores = Arrays.copyOf(cores, layerCount * 2);
            }
            layers[layerCount] = renderType;
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

    public RenderType layer(int index) {
        return layers[index];
    }

    /** The vertices layer {@code index} held when its decorators were replayed; 0 when they opened it. */
    public int core(int index) {
        return cores[index];
    }

    public void setCore(int index, int vertices) {
        cores[index] = vertices;
    }

    private int indexOf(RenderType type) {
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
            e.modelData = null;
            e.renderType = null;
        }
        size = 0;
        Arrays.fill(layers, 0, layerCount, null);
        layerCount = 0;
    }
}
