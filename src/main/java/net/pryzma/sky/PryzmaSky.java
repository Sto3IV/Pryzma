package net.pryzma.sky;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;
import net.pryzma.core.res.PrResources;

/**
 * OptiFine custom skies: {@code optifine/sky/world<N>/sky<i>.properties} layers, drawn between the
 * sunrise glow and the sun as in OptiFine, rotating with the celestial angle. {@code world0} is the
 * overworld and every dimension OptiFine counts as such; {@code world1} is the End.
 */
public final class PryzmaSky {
    private static final Pattern LAYER = Pattern.compile("optifine/sky/world(-?\\d+)/sky(\\d+)\\.properties");

    private static volatile Data data = Data.EMPTY;
    private static final Set<Identifier> REGISTERED = new HashSet<>();
    private static GpuBuffer cubeBuffer;

    private PryzmaSky() {
    }

    /** Parsed layers per world id, with the physical texture location of every layer. */
    public record Data(Map<Integer, PrSkyLayer[]> worlds, Map<Identifier, Identifier> textures) {
        static final Data EMPTY = new Data(Map.of(), Map.of());
    }

    public static Data prepare(PrResources res) {
        Map<Integer, TreeMap<Integer, PrSkyLayer>> byWorld = new HashMap<>();
        Map<Identifier, Identifier> textures = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<Identifier, Resource> e : res.list("sky", ".properties").entrySet()) {
            Identifier loc = e.getKey();
            Matcher m = LAYER.matcher(loc.getPath());
            if (!"minecraft".equals(loc.getNamespace()) || !m.matches()) {
                continue;
            }
            int world = Integer.parseInt(m.group(1));
            int index = Integer.parseInt(m.group(2));
            res.properties(loc, e.getValue())
                    .flatMap(p -> PrSkyLayer.parse(p, index, warnings::add))
                    .ifPresent(layer -> {
                        Identifier physical = res.physicalLocation(layer.texture).orElse(null);
                        if (physical == null) {
                            warnings.add("Texture not found: " + layer.texture + " (" + loc + ")");
                            return;
                        }
                        textures.put(layer.texture, physical);
                        byWorld.computeIfAbsent(world, w -> new TreeMap<>()).put(index, layer);
                    });
        }
        warnings.forEach(w -> Pryzma.LOGGER.warn("CustomSky: {}", w));
        Map<Integer, PrSkyLayer[]> worlds = new HashMap<>();
        byWorld.forEach((world, layers) -> worlds.put(world, layers.values().toArray(PrSkyLayer[]::new)));
        if (!worlds.isEmpty()) {
            Map<Integer, Integer> counts = new TreeMap<>();
            worlds.forEach((w, l) -> counts.put(w, l.length));
            Pryzma.LOGGER.info("CustomSky: layers per world {}", counts);
        }
        return worlds.isEmpty() ? Data.EMPTY : new Data(Map.copyOf(worlds), Map.copyOf(textures));
    }

    /** Swaps in the new layers and (re)loads their textures. Render thread. */
    public static void apply(Data d) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        Set<Identifier> wanted = new HashSet<>(d.textures().values());
        for (Identifier old : REGISTERED) {
            if (!wanted.contains(old)) {
                textures.release(old);
            }
        }
        REGISTERED.clear();
        for (Identifier physical : wanted) {
            textures.register(physical, new SimpleTexture(physical));
            REGISTERED.add(physical);
        }
        data = d;
    }

    /** OptiFine dimension id: Nether -1, End 1, everything else 0. */
    static int worldId(Level level) {
        if (level.dimension() == Level.NETHER) {
            return -1;
        }
        return level.dimension() == Level.END ? 1 : 0;
    }

    public static boolean hasLayers(Level level) {
        return PryzmaConfig.prCustomSky && data.worlds().containsKey(worldId(level));
    }

    /** Vanilla stars are hidden where custom layers draw the night sky, as in OptiFine. */
    public static boolean starsVisible(Level level) {
        return PryzmaConfig.prStars && !hasLayers(level);
    }

    private static synchronized GpuBuffer getOrCreateCubeBuffer() {
        if (cubeBuffer == null || cubeBuffer.isClosed()) {
            try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
                BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
                PoseStack pose = new PoseStack();
                PrSkyLayer.emitCube(pose, bufferBuilder);
                try (MeshData meshData = bufferBuilder.buildOrThrow()) {
                    cubeBuffer = RenderSystem.getDevice().createBuffer(() -> "Pryzma custom sky cube", 40, meshData.vertexBuffer());
                }
            }
        }
        return cubeBuffer;
    }

    /**
     * Draws every active layer. {@code pose} holds vanilla's sky orientation (rotated -90 degrees
     * around Y, before the celestial rotation).
     */
    public static void render(RenderPass renderPass, PoseStack pose, float sunAngle, float rainBrightness) {
        if (!PryzmaConfig.prCustomSky) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Data d = data;
        PrSkyLayer[] layers = d.worlds().get(worldId(level));
        if (layers == null || layers.length == 0) {
            return;
        }
        long dayTime = level.getDefaultClockTime();
        int timeOfDay = (int) (dayTime % 24000L);
        float celestialAngle = sunAngle / ((float) (Math.PI * 2.0));
        float rain = 1.0F - rainBrightness;
        float thunder = level.getThunderLevel(1.0F);
        float thunderRel = rain > 0.0F ? thunder / rain : 0.0F;
        Entity camera = mc.getCameraEntity();
        BlockPos cameraPos = camera != null ? camera.blockPosition() : BlockPos.ZERO;

        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = autoIndices.getBuffer(36);
        GpuBuffer vertexBuffer = getOrCreateCubeBuffer();
        TextureManager textureManager = mc.getTextureManager();

        for (PrSkyLayer layer : layers) {
            if (!layer.isActive(dayTime, timeOfDay)) {
                continue;
            }
            float brightness = Math.clamp(layer.positionBrightness(level, cameraPos)
                    * layer.weatherBrightness(rain, thunderRel) * layer.fadeBrightness(timeOfDay), 0.0F, 1.0F);
            if (brightness < 1.0E-4F) {
                continue;
            }
            Identifier textureLoc = d.textures().get(layer.texture);
            if (textureLoc == null) {
                continue;
            }
            AbstractTexture texture = textureManager.getTexture(textureLoc);
            if (texture == null) {
                continue;
            }

            Vector4f color;
            if (layer.blend == PrSkyBlend.MULTIPLY) {
                color = new Vector4f(brightness, brightness, brightness, brightness);
            } else if (layer.blend.isRgbBrightness()) {
                color = new Vector4f(brightness, brightness, brightness, 1.0F);
            } else {
                color = new Vector4f(1.0F, 1.0F, 1.0F, brightness);
            }

            pose.pushPose();
            layer.rotate(pose, dayTime, celestialAngle);

            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.mul(pose.last().pose());
            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(new Matrix4f(modelViewStack), color);

            renderPass.pushDebugGroup(() -> "Pryzma custom sky: " + layer.texture);
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(layer.blend.pipeline()));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
            renderPass.setVertexBuffer(0, vertexBuffer.slice());
            renderPass.setIndexBuffer(indexBuffer, autoIndices.type());
            renderPass.drawIndexed(36, 1, 0, 0, 0);
            renderPass.popDebugGroup();

            modelViewStack.popMatrix();
            pose.popPose();
        }
    }

    /** End sky variant: draws End sky layers unrotated. */
    public static void renderEnd(RenderPass renderPass, PoseStack pose) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !hasLayers(level)) {
            return;
        }
        render(renderPass, pose, 0.0F, 1.0F);
    }
}
