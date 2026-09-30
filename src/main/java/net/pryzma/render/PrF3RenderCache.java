package net.pryzma.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.pryzma.iris.vertices.ImmediateState;

/**
 * The F3 text cache, OptiFine's {@code RenderCache(100L)} on Blaze3D. The two text columns of the
 * debug screen are built at most every 100 ms and replayed in between from vertex buffers that stay
 * on the GPU. A replayed frame skips both raycasts, both information lists with the noise router,
 * the DebugText event, string measuring, tessellation and the vertex upload. The charts are not
 * cached: vanilla draws them after the text on every frame.
 *
 * <p>A build frame runs vanilla {@code renderLines} into a buffer source that gives every render
 * type its own buffer and uploads each finished batch instead of drawing it. Vanilla ends the current
 * batch whenever the render type changes; with a TTF font whose glyphs spread over many atlas pages
 * that happens at almost every character, 1.2 to 1.5 ms of draw calls per frame. Merged, a column is
 * one draw for its boxes and one per font page, in first-use order: the boxes stay under the text,
 * and only glyphs of different pages change order against each other.
 *
 * <p>Render thread only.
 */
public final class PrF3RenderCache {
    /** OptiFine's interval: the text refreshes ten times a second. */
    public static final long TTL_NANOS = 100_000_000L;

    /** Off: vanilla draws the text every frame. The self-test compares the two. */
    public static boolean enabled = true;

    private static final Column LEFT = new Column();
    private static final Column RIGHT = new Column();
    private static final Matrix4f POSE = new Matrix4f();
    private static final List<String> NO_LINES = new ArrayList<>();

    private static boolean replaying;
    private static boolean valid;
    private static boolean eventSkippable;
    /** Columns of the current build: bit 0 left, bit 1 right. */
    private static int built;
    private static long builtAt;
    private static int width = -1;
    private static int height = -1;
    private static int builds;
    /** Created by the first build: it allocates native memory and a GuiGraphics. */
    private static Capture capture;

    private PrF3RenderCache() {
    }

    /**
     * Start of {@code DebugScreenOverlay.render}: decides once per frame whether the text replays. A
     * new build starts when the text is older than {@link #TTL_NANOS}, when the GUI size or the pose
     * of the debug layer changed (the vertices hold both), or after {@link #invalidate}.
     */
    public static boolean beginFrame(long now, int guiWidth, int guiHeight, Matrix4f pose) {
        replaying = enabled && valid && eventSkippable && now - builtAt < TTL_NANOS && guiWidth == width
                && guiHeight == height && POSE.equals(pose);
        if (!replaying) {
            valid = false;
            built = 0;
            builtAt = now;
            width = guiWidth;
            height = guiHeight;
            POSE.set(pose);
        }
        return replaying;
    }

    /** This frame replays: the raycasts, the lists and the DebugText event are skipped. */
    public static boolean replaying() {
        return replaying;
    }

    /** Stands in for a skipped list; mutable, like the lists vanilla hands on. */
    public static List<String> noLines() {
        NO_LINES.clear();
        return NO_LINES;
    }

    /**
     * The DebugText hook ran on a build frame. Until it has, no frame replays: skipping the lists
     * without the event would hand its listeners empty lists to edit by index.
     */
    public static void eventHookRan() {
        eventSkippable = true;
    }

    /** The next frame builds: F3 or a chart toggled, the world left, resources reloaded. */
    public static void invalidate() {
        valid = false;
    }

    /** Completed builds since start; the self-test reads the refresh rate from it. */
    public static int builds() {
        return builds;
    }

    /** Draws one column from its vertex buffers. */
    public static void replay(GuiGraphics graphics, boolean left) {
        (left ? LEFT : RIGHT).draw(graphics);
    }

    /**
     * Builds one column: {@code renderLines} draws into the capture, whose batches become the column's
     * vertex buffers, and the column is drawn from them as a replayed frame draws it.
     */
    public static void build(GuiGraphics graphics, boolean left, Consumer<GuiGraphics> renderLines) {
        Column column = left ? LEFT : RIGHT;
        if (capture == null) {
            capture = new Capture();
        }
        capture.record(graphics, column, renderLines);
        columnBuilt(left);
        column.draw(graphics);
    }

    /** Both columns of the build are in: the following frames may replay. */
    static void columnBuilt(boolean left) {
        built |= left ? 1 : 2;
        if (built == 3 && !valid) {
            valid = true;
            builds++;
        }
    }

    /** Records {@code renderLines} and hands every finished batch to the column being built. */
    private static final class Capture extends MultiBufferSource.BufferSource {
        private final GuiGraphics graphics;
        private Column column;

        Capture() {
            super(new ByteBufferBuilder(256), new TypeBuffers());
            graphics = new GuiGraphics(Minecraft.getInstance(), this);
        }

        /** drawManaged is deprecated in 1.21.1 without a replacement; vanilla's own F3 draws through it. */
        @SuppressWarnings("deprecation")
        void record(GuiGraphics target, Column column, Consumer<GuiGraphics> renderLines) {
            this.column = column;
            column.count = 0;
            PoseStack.Pose from = target.pose().last();
            PoseStack.Pose to = graphics.pose().last();
            to.pose().set(from.pose());
            to.normal().set(from.normal());
            // Managed: otherwise every fill and every string flushes, one batch each.
            graphics.drawManaged(() -> renderLines.accept(graphics));
            this.column = null;
        }

        /** Where vanilla draws a finished batch; flush() ends every type here. */
        @Override
        public void endBatch(RenderType type) {
            BufferBuilder builder = startedBuilders.remove(type);
            MeshData mesh = builder != null ? builder.build() : null;
            if (mesh != null) {
                if (column == null) {
                    mesh.close();
                } else {
                    if (type.sortOnUpload()) {
                        mesh.sortQuads(fixedBuffers.getOrDefault(type, sharedBuffer), RenderSystem.getVertexSorting());
                    }
                    column.add(type, mesh);
                }
            }
            if (type.equals(lastSharedType)) {
                lastSharedType = null;
            }
        }
    }

    /**
     * Makes every render type a fixed type of the capture, so requesting one no longer ends the batch
     * of another. Vanilla getBuffer, with the mixins on it, still creates the builders.
     */
    private static final class TypeBuffers extends LinkedHashMap<RenderType, ByteBufferBuilder> {
        @Override
        public ByteBufferBuilder get(Object key) {
            ByteBufferBuilder buffer = super.get(key);
            if (buffer == null && key instanceof RenderType type) {
                buffer = new ByteBufferBuilder(4096);
                put(type, buffer);
            }
            return buffer;
        }
    }

    /** One text column: its batches in draw order, each in a vertex buffer that stays on the GPU. */
    private static final class Column {
        private final List<RenderType> types = new ArrayList<>();
        private final List<VertexBuffer> buffers = new ArrayList<>();
        private int count;

        void add(RenderType type, MeshData mesh) {
            if (count == buffers.size()) {
                buffers.add(new VertexBuffer(VertexBuffer.Usage.DYNAMIC));
                types.add(type);
            } else {
                types.set(count, type);
            }
            VertexBuffer buffer = buffers.get(count++);
            // With a shader pack active Iris lays POSITION_COLOR_TEX_LIGHTMAP out as its extended glyph
            // format unless this flag is off; its flush hook turns it off for every GUI batch vanilla draws.
            boolean extended = ImmediateState.renderWithExtendedVertexFormat;
            ImmediateState.renderWithExtendedVertexFormat = false;
            try {
                buffer.bind();
                buffer.upload(mesh);
            } finally {
                ImmediateState.renderWithExtendedVertexFormat = extended;
                VertexBuffer.unbind();
            }
        }

        /** RenderType.draw without the upload. What the frame queued before the column draws first. */
        void draw(GuiGraphics graphics) {
            if (count == 0) {
                return;
            }
            graphics.bufferSource().endLastBatch();
            for (int i = 0; i < count; i++) {
                RenderType type = types.get(i);
                VertexBuffer buffer = buffers.get(i);
                type.setupRenderState();
                buffer.bind();
                buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                type.clearRenderState();
            }
            VertexBuffer.unbind();
        }
    }
}
