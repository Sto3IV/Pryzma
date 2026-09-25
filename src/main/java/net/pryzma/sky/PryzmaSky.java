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

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
    private static final Set<ResourceLocation> REGISTERED = new HashSet<>();

    private PryzmaSky() {
    }

    /** Parsed layers per world id, with the physical texture location of every layer. */
    public record Data(Map<Integer, PrSkyLayer[]> worlds, Map<ResourceLocation, ResourceLocation> textures) {
        static final Data EMPTY = new Data(Map.of(), Map.of());
    }

    public static Data prepare(PrResources res) {
        Map<Integer, TreeMap<Integer, PrSkyLayer>> byWorld = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> textures = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : res.list("sky", ".properties").entrySet()) {
            ResourceLocation loc = e.getKey();
            Matcher m = LAYER.matcher(loc.getPath());
            if (!"minecraft".equals(loc.getNamespace()) || !m.matches()) {
                continue;
            }
            int world = Integer.parseInt(m.group(1));
            int index = Integer.parseInt(m.group(2));
            res.properties(loc, e.getValue())
                    .flatMap(p -> PrSkyLayer.parse(p, index, w -> warnings.add(w)))
                    .ifPresent(layer -> {
                        ResourceLocation physical = res.physicalLocation(layer.texture).orElse(null);
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
        Set<ResourceLocation> wanted = new HashSet<>(d.textures().values());
        for (ResourceLocation old : REGISTERED) {
            if (!wanted.contains(old)) {
                textures.release(old);
            }
        }
        REGISTERED.clear();
        for (ResourceLocation physical : wanted) {
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

    /**
     * Draws every active layer. {@code pose} holds vanilla's sky orientation (rotated -90 degrees
     * around Y, before the celestial rotation); blend state is restored to vanilla's afterwards.
     */
    public static void render(ClientLevel level, PoseStack pose, float partialTick) {
        if (!PryzmaConfig.prCustomSky) {
            return;
        }
        Data d = data;
        PrSkyLayer[] layers = d.worlds().get(worldId(level));
        if (layers == null) {
            return;
        }
        long dayTime = level.getDayTime();
        int timeOfDay = (int) (dayTime % 24000L);
        float celestialAngle = level.getTimeOfDay(partialTick);
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        float thunderRel = rain > 0.0F ? thunder / rain : 0.0F;
        Entity camera = Minecraft.getInstance().getCameraEntity();
        BlockPos cameraPos = camera != null ? camera.blockPosition() : BlockPos.ZERO;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        for (PrSkyLayer layer : layers) {
            if (!layer.isActive(dayTime, timeOfDay)) {
                continue;
            }
            float brightness = Math.clamp(layer.positionBrightness(level, cameraPos)
                    * layer.weatherBrightness(rain, thunderRel) * layer.fadeBrightness(timeOfDay), 0.0F, 1.0F);
            if (brightness < 1.0E-4F) {
                continue;
            }
            ResourceLocation texture = d.textures().get(layer.texture);
            RenderSystem.setShaderTexture(0, texture);
            layer.blend.apply(brightness);
            pose.pushPose();
            layer.rotate(pose, dayTime, celestialAngle);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            PrSkyLayer.emitCube(pose, buffer);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            pose.popPose();
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F - rain);
    }

    /** End sky variant: vanilla's End pose is used unrotated and the blend reset matches renderEndSky. */
    public static void renderEnd(ClientLevel level, PoseStack pose) {
        if (!hasLayers(level)) {
            return;
        }
        render(level, pose, 0.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
    }
}
