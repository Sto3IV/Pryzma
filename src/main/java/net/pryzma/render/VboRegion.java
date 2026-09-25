package net.pryzma.render;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;
import net.pryzma.util.LinkedList;

/**
 * The vertex buffer of one render region for one terrain layer. Each section owns a
 * {@link VboRange} of it; the visible ranges are queued while the layer draws and submitted with a
 * single {@code glMultiDrawElements} over the game's shared sequential index buffer. A range that
 * outgrows its slot moves to the top; the gaps left behind are closed by {@link #compactRanges},
 * one copy per call, while more than a tenth of the used span is free. Render thread only.
 */
public class VboRegion {
    private static final int INITIAL_CAPACITY = 4096;
    private static final int MIN_DRAWS = 64;

    private final RenderType layer;
    private final long key;
    private int glArrayObjectId = -1;
    private int glBufferId;
    /** The buffer the VAO's attribute pointers were set up with; a replaced buffer needs them again. */
    private int glAttribBufferId = -1;
    private int capacity = INITIAL_CAPACITY;
    private int positionTop;
    private int sizeUsed;
    private final LinkedList<VboRange> rangeList = new LinkedList<>();
    private VboRange compactRangeLast;
    private PointerBuffer bufferIndexVertex;
    private IntBuffer bufferCountVertex;
    private final int vertexBytes;
    private VertexFormat.Mode drawMode = VertexFormat.Mode.QUADS;

    public VboRegion(RenderType layer, long key) {
        this(layer, key, layer.format().getVertexSize());
        glArrayObjectId = GlStateManager._glGenVertexArrays();
        glBufferId = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, glBufferId);
        GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, toBytes(capacity), GL15.GL_STATIC_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    /** The allocator without GL objects; tests drive it through the GL seams. */
    VboRegion(RenderType layer, long key, int vertexBytes) {
        this.layer = layer;
        this.key = key;
        this.vertexBytes = vertexBytes;
    }

    /** Stores a section's vertices in {@code range}: in place when they fit its slot, otherwise at the top. */
    public void bufferData(ByteBuffer data, VboRange range) {
        if (glBufferId < 0) {
            return;
        }
        int size = toVertex(data.remaining());
        if (size <= 0) {
            release(range);
            return;
        }
        LinkedList.Node<VboRange> node = range.getNode();
        boolean owned = rangeList.contains(node);
        int sizeOld = owned ? range.getSize() : 0;
        if (size > sizeOld) {
            checkVboSize(positionTop + size);
            if (owned) {
                rangeList.remove(node);
            }
            range.setPosition(positionTop);
            positionTop += size;
            rangeList.addLast(node);
        }
        range.setSize(size);
        sizeUsed += size - sizeOld;
        uploadGl(toBytes(range.getPosition()), data);
        if (isFragmented()) {
            compactRanges(1);
        }
    }

    /** Frees {@code range}, which holds no data afterwards. Safe on a deleted region. */
    public void release(VboRange range) {
        LinkedList.Node<VboRange> node = range.getNode();
        if (rangeList.contains(node)) {
            rangeList.remove(node);
            sizeUsed -= range.getSize();
            if (rangeList.isEmpty()) {
                positionTop = 0;
                compactRangeLast = null;
            }
        }
        range.setPosition(-1);
        range.setSize(0);
    }

    /**
     * Moves up to {@code count} ranges down into the gaps below them, resuming where the previous
     * call stopped. A range too large for the gap below it goes to the top, widening that gap for
     * the ranges after it. Ranges stay in ascending position order and never overlap.
     */
    void compactRanges(int count) {
        if (rangeList.isEmpty()) {
            return;
        }
        VboRange range = compactRangeLast;
        if (range == null || !rangeList.contains(range.getNode())) {
            range = rangeList.getFirst().getItem();
        }
        VboRange prev = range.getPrev();
        int posCompact = prev == null ? 0 : prev.getPositionNext();
        int moved = 0;
        while (range != null && moved < count) {
            int gap = range.getPosition() - posCompact;
            if (gap == 0) {
                posCompact += range.getSize();
                range = range.getNext();
            } else if (range.getSize() <= gap) {
                copyGl(toBytes(range.getPosition()), toBytes(posCompact), toBytes(range.getSize()));
                range.setPosition(posCompact);
                posCompact += range.getSize();
                range = range.getNext();
                moved++;
            } else {
                checkVboSize(positionTop + range.getSize());
                copyGl(toBytes(range.getPosition()), toBytes(positionTop), toBytes(range.getSize()));
                range.setPosition(positionTop);
                positionTop += range.getSize();
                VboRange next = range.getNext();
                rangeList.remove(range.getNode());
                rangeList.addLast(range.getNode());
                range = next;
                moved++;
            }
        }
        if (range == null) {
            positionTop = rangeList.getLast().getItem().getPositionNext();
        }
        compactRangeLast = range;
    }

    private boolean isFragmented() {
        return positionTop > sizeUsed + sizeUsed / 10;
    }

    private void checkVboSize(int sizeMin) {
        if (capacity < sizeMin) {
            expandVbo(sizeMin);
        }
    }

    /** Grows the buffer in 1.5x steps until {@code sizeMin} vertices fit; the data below the top is kept. */
    private void expandVbo(int sizeMin) {
        int capacityNew = capacity;
        while (capacityNew < sizeMin) {
            capacityNew += capacityNew >> 1;
        }
        resizeGl(toBytes(capacityNew), toBytes(positionTop));
        capacity = capacityNew;
    }

    public boolean hasPendingDraws() {
        return bufferIndexVertex != null && bufferIndexVertex.position() > 0;
    }

    /**
     * Queues {@code range} for the next {@link #finishDraw}. The offset is stored in indices of the
     * sequential index buffer and scaled to bytes once that buffer's index type is known.
     */
    public void drawArrays(VertexFormat.Mode mode, VboRange range) {
        if (drawMode != mode) {
            if (hasPendingDraws()) {
                throw new IllegalArgumentException("Mixed region draw modes: " + drawMode + " != " + mode);
            }
            drawMode = mode;
        }
        if (bufferIndexVertex == null || !bufferIndexVertex.hasRemaining()) {
            growDrawBuffers();
        }
        bufferIndexVertex.put(mode.indexCount(range.getPosition()));
        bufferCountVertex.put(mode.indexCount(range.getSize()));
    }

    /** Draw commands are bounded by the ranges in the region, not by its vertex capacity. */
    private void growDrawBuffers() {
        int draws = bufferIndexVertex == null ? 0 : bufferIndexVertex.position();
        int capacityNew = Math.max(MIN_DRAWS, Math.max(rangeList.getSize(), draws * 2));
        PointerBuffer indices = BufferUtils.createPointerBuffer(capacityNew);
        IntBuffer counts = BufferUtils.createIntBuffer(capacityNew);
        if (draws > 0) {
            indices.put(bufferIndexVertex.flip());
            counts.put(bufferCountVertex.flip());
        }
        bufferIndexVertex = indices;
        bufferCountVertex = counts;
    }

    /** Draws every queued range with one call; the caller has set the region's ChunkOffset. */
    public void finishDraw() {
        int draws = bufferIndexVertex == null ? 0 : bufferIndexVertex.position();
        if (draws == 0) {
            return;
        }
        if (glBufferId < 0) {
            bufferIndexVertex.clear();
            bufferCountVertex.clear();
            return;
        }
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(glArrayObjectId);
        if (glAttribBufferId != glBufferId) {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, glBufferId);
            layer.format().setupBufferState();
            glAttribBufferId = glBufferId;
        }
        // Binding attaches the shared index buffer to this VAO and grows it; its type is final only afterwards.
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(drawMode);
        indices.bind(drawMode.indexCount(positionTop));
        VertexFormat.IndexType indexType = indices.type();
        int shift = Integer.numberOfTrailingZeros(indexType.bytes);
        bufferIndexVertex.flip();
        bufferCountVertex.flip();
        for (int i = 0; i < draws; i++) {
            bufferIndexVertex.put(i, bufferIndexVertex.get(i) << shift);
        }
        GL14.glMultiDrawElements(drawMode.asGLMode, bufferCountVertex, indexType.asGLType, bufferIndexVertex);
        bufferIndexVertex.clear();
        bufferCountVertex.clear();
        if (isFragmented()) {
            compactRanges(1);
        }
    }

    public void deleteGlBuffers() {
        if (glArrayObjectId >= 0) {
            GlStateManager._glDeleteVertexArrays(glArrayObjectId);
            glArrayObjectId = -1;
        }
        if (glBufferId >= 0) {
            GlStateManager._glDeleteBuffers(glBufferId);
            glBufferId = -1;
        }
        bufferIndexVertex = null;
        bufferCountVertex = null;
    }

    // GL seams: package-private so the allocator runs in tests without a context.

    void uploadGl(long offset, ByteBuffer data) {
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, glBufferId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, data);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    void copyGl(long from, long to, long bytes) {
        GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, glBufferId);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, glBufferId);
        GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, from, to, bytes);
        GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    void resizeGl(long bytesNew, long bytesKept) {
        int bufferNew = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, bufferNew);
        GlStateManager._glBufferData(GL31.GL_COPY_WRITE_BUFFER, bytesNew, GL15.GL_STATIC_DRAW);
        if (bytesKept > 0) {
            GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, glBufferId);
            GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0L, 0L, bytesKept);
            GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        }
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        GlStateManager._glDeleteBuffers(glBufferId);
        glBufferId = bufferNew;
    }

    public RenderType getLayer() {
        return layer;
    }

    public long getKey() {
        return key;
    }

    public boolean isDeleted() {
        return glBufferId < 0;
    }

    public boolean isEmpty() {
        return rangeList.isEmpty();
    }

    public int getCapacity() {
        return capacity;
    }

    public int getPositionTop() {
        return positionTop;
    }

    public int getSizeUsed() {
        return sizeUsed;
    }

    private long toBytes(int vertices) {
        return (long) vertices * vertexBytes;
    }

    private int toVertex(long bytes) {
        return (int) (bytes / vertexBytes);
    }

    /**
     * Adds {@code (dx, dy, dz)} to the position of each vertex, turning section-relative vertices
     * into region-relative ones. Reads and writes in the buffer's own byte order.
     */
    public static void translate(ByteBuffer vertices, int vertexCount, int stride, int positionOffset, float dx, float dy, float dz) {
        for (int i = 0, p = positionOffset; i < vertexCount; i++, p += stride) {
            vertices.putFloat(p, vertices.getFloat(p) + dx);
            vertices.putFloat(p + 4, vertices.getFloat(p + 4) + dy);
            vertices.putFloat(p + 8, vertices.getFloat(p + 8) + dz);
        }
    }
}
