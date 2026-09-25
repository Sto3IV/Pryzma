package net.pryzma.shader;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.pryzma.core.expr.PrExpr;
import net.pryzma.core.expr.PrExprEnv;
import net.pryzma.core.expr.PrExprException;
import net.pryzma.core.expr.PrExprParser;

/**
 * OptiFine's built-in uniforms: frame state computed once per frame (matrices, camera, sun and
 * moon, time, weather, eye brightness, fog...) and uploaded by name into the programs that
 * declare them. The same values feed the expressions of {@code shaders.properties}.
 */
final class PrUniforms {
    /** One uniform: how it uploads and how an expression reads it. */
    private interface Value {
        void upload(int location);
    }

    /** A location in a program and the value it receives. */
    record Binding(int location, Value value) {
    }

    private final Map<String, Value> values = new LinkedHashMap<>();
    private final Map<String, PrExpr> expressions = new LinkedHashMap<>();
    /** Custom uniforms and variables, recomputed in declaration order each frame. */
    private final List<Runnable> customUpdates = new ArrayList<>();
    private final FloatBuffer matrix = MemoryUtil.memAllocFloat(16);
    private final PrShaderConfig config;

    final Matrix4f gbufferModelView = new Matrix4f();
    final Matrix4f gbufferModelViewInverse = new Matrix4f();
    final Matrix4f gbufferProjection = new Matrix4f();
    final Matrix4f gbufferProjectionInverse = new Matrix4f();
    final Matrix4f gbufferPreviousModelView = new Matrix4f();
    final Matrix4f gbufferPreviousProjection = new Matrix4f();
    final Matrix4f shadowModelView = new Matrix4f();
    final Matrix4f shadowModelViewInverse = new Matrix4f();
    final Matrix4f shadowProjection = new Matrix4f();
    final Matrix4f shadowProjectionInverse = new Matrix4f();
    final Vector3f cameraPosition = new Vector3f();
    final Vector3f previousCameraPosition = new Vector3f();
    final Vector3f sunPosition = new Vector3f();
    final Vector3f moonPosition = new Vector3f();
    final Vector3f shadowLightPosition = new Vector3f();
    final Vector3f upPosition = new Vector3f();
    final Vector3f fogColor = new Vector3f();
    final Vector3f skyColor = new Vector3f();
    float frameTime;
    float frameTimeCounter;
    int frameCounter;
    int worldTime;
    int worldDay;
    int moonPhase;
    float sunAngle;
    float shadowAngle;
    float rainStrength;
    float wetness;
    float viewWidth;
    float viewHeight;
    float near = 0.05F;
    float far;
    float fogStart;
    float fogEnd;
    int isEyeInWater;
    float nightVision;
    float blindness;
    float darknessFactor;
    float screenBrightness;
    float eyeAltitude;
    float eyeBrightnessX;
    float eyeBrightnessY;
    float eyeBrightnessSmoothX;
    float eyeBrightnessSmoothY;
    int heldItemId = -1;
    int heldBlockLightValue;
    int heldItemId2 = -1;
    int heldBlockLightValue2;
    int hideGui;
    float centerDepthSmooth = 1.0F;
    float playerMood;
    int renderStage;
    float alphaTestRef;
    int atlasWidth = 1024;
    int atlasHeight = 1024;
    private long lastFrameNanos;
    private boolean firstFrame = true;

    PrUniforms(PrShaderConfig config) {
        this.config = config;
        mat("gbufferModelView", gbufferModelView);
        mat("gbufferModelViewInverse", gbufferModelViewInverse);
        mat("gbufferProjection", gbufferProjection);
        mat("gbufferProjectionInverse", gbufferProjectionInverse);
        mat("gbufferPreviousModelView", gbufferPreviousModelView);
        mat("gbufferPreviousProjection", gbufferPreviousProjection);
        mat("shadowModelView", shadowModelView);
        mat("shadowModelViewInverse", shadowModelViewInverse);
        mat("shadowProjection", shadowProjection);
        mat("shadowProjectionInverse", shadowProjectionInverse);
        vec3("cameraPosition", cameraPosition, "x", "y", "z");
        vec3("previousCameraPosition", previousCameraPosition, "x", "y", "z");
        vec3("sunPosition", sunPosition, "x", "y", "z");
        vec3("moonPosition", moonPosition, "x", "y", "z");
        vec3("shadowLightPosition", shadowLightPosition, "x", "y", "z");
        vec3("upPosition", upPosition, "x", "y", "z");
        vec3("fogColor", fogColor, "r", "g", "b");
        vec3("skyColor", skyColor, "r", "g", "b");
        f("frameTime", () -> frameTime);
        f("frameTimeCounter", () -> frameTimeCounter);
        i("frameCounter", () -> frameCounter);
        i("worldTime", () -> worldTime);
        i("worldDay", () -> worldDay);
        i("moonPhase", () -> moonPhase);
        f("sunAngle", () -> sunAngle);
        f("shadowAngle", () -> shadowAngle);
        f("rainStrength", () -> rainStrength);
        f("wetness", () -> wetness);
        f("viewWidth", () -> viewWidth);
        f("viewHeight", () -> viewHeight);
        f("aspectRatio", () -> viewHeight == 0 ? 1 : viewWidth / viewHeight);
        f("near", () -> near);
        f("far", () -> far);
        f("fogStart", () -> fogStart);
        f("fogEnd", () -> fogEnd);
        f("fogDensity", () -> 0.0F);
        i("fogMode", () -> 9729);
        i("fogShape", () -> RenderSystem.getShaderFogShape().getIndex());
        i("isEyeInWater", () -> isEyeInWater);
        f("nightVision", () -> nightVision);
        f("blindness", () -> blindness);
        f("darknessFactor", () -> darknessFactor);
        f("darknessLightFactor", () -> darknessFactor);
        // Iris' End flash (1.21.9+); this version has none.
        f("endFlashIntensity", () -> 0.0F);
        f("screenBrightness", () -> screenBrightness);
        f("eyeAltitude", () -> eyeAltitude);
        ivec2("eyeBrightness", () -> eyeBrightnessX, () -> eyeBrightnessY);
        ivec2("eyeBrightnessSmooth", () -> eyeBrightnessSmoothX, () -> eyeBrightnessSmoothY);
        i("heldItemId", () -> heldItemId);
        i("heldBlockLightValue", () -> heldBlockLightValue);
        i("heldItemId2", () -> heldItemId2);
        i("heldBlockLightValue2", () -> heldBlockLightValue2);
        i("hideGUI", () -> hideGui);
        f("centerDepthSmooth", () -> centerDepthSmooth);
        f("playerMood", () -> playerMood);
        i("renderStage", () -> renderStage);
        i("bossBattle", () -> 0);
        i("entityId", () -> -1);
        i("blockEntityId", () -> -1);
        i("instanceId", () -> 0);
        f("alphaTestRef", () -> alphaTestRef);
        f("pr_AlphaTestRef", () -> alphaTestRef);
        ivec2("atlasSize", () -> atlasWidth, () -> atlasHeight);
        ivec2("terrainTextureSize", () -> atlasWidth, () -> atlasHeight);
        i("terrainIconSize", () -> 16);
        values.put("entityColor", loc -> GL20.glUniform4f(loc, 0, 0, 0, 0));
        values.put("spriteBounds", loc -> GL20.glUniform4f(loc, 0, 0, 1, 1));
        values.put("blendFunc", loc -> GL20.glUniform4i(loc, 770, 771, 1, 771));
        expressions.put("is_alive", (PrExpr.B) () -> player() != null && player().isAlive());
        expressions.put("is_burning", (PrExpr.B) () -> player() != null && player().isOnFire());
        expressions.put("is_child", (PrExpr.B) () -> player() != null && player().isBaby());
        expressions.put("is_glowing", (PrExpr.B) () -> player() != null && player().isCurrentlyGlowing());
        expressions.put("is_hurt", (PrExpr.B) () -> player() != null && player().hurtTime > 0);
        expressions.put("is_in_lava", (PrExpr.B) () -> player() != null && player().isInLava());
        expressions.put("is_in_water", (PrExpr.B) () -> player() != null && player().isInWater());
        expressions.put("is_invisible", (PrExpr.B) () -> player() != null && player().isInvisible());
        expressions.put("is_on_ground", (PrExpr.B) () -> player() != null && player().onGround());
        expressions.put("is_ridden", (PrExpr.B) () -> player() != null && player().isVehicle());
        expressions.put("is_riding", (PrExpr.B) () -> player() != null && player().isPassenger());
        expressions.put("is_sneaking", (PrExpr.B) () -> player() != null && player().isCrouching());
        expressions.put("is_sprinting", (PrExpr.B) () -> player() != null && player().isSprinting());
        expressions.put("is_wet", (PrExpr.B) () -> player() != null && player().isInWaterOrRain());
        expressions.put("temperature", (PrExpr.F) () -> biomeClimate(true));
        expressions.put("biome", (PrExpr.F) PrUniforms::biomeId);
        expressions.put("biome_category", (PrExpr.F) PrUniforms::biomeCategory);
        expressions.put("biome_precipitation", (PrExpr.F) PrUniforms::precipitation);
        expressions.put("PPT_NONE", new PrExpr.Constant(0));
        expressions.put("PPT_RAIN", new PrExpr.Constant(1));
        expressions.put("PPT_SNOW", new PrExpr.Constant(2));
        for (int i = 0; i < CATEGORIES.length; i++) {
            expressions.put("CAT_" + CATEGORIES[i], new PrExpr.Constant(i));
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // BIOME_<NAME>: the registry id of each biome, as OptiFine names them.
            var biomes = mc.level.registryAccess().registryOrThrow(Registries.BIOME);
            for (var key : biomes.registryKeySet()) {
                String path = key.location().getPath().toUpperCase(java.util.Locale.ROOT);
                String name = key.location().getNamespace().equals("minecraft") ? path
                        : key.location().getNamespace().toUpperCase(java.util.Locale.ROOT) + "_" + path;
                expressions.putIfAbsent("BIOME_" + name, new PrExpr.Constant(biomes.getId(biomes.get(key))));
            }
        }
        expressions.put("rainfall", (PrExpr.F) () -> biomeClimate(false));
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    /** OptiFine's biome categories, in the order of their {@code CAT_*} values. */
    private static final String[] CATEGORIES = {"NONE", "TAIGA", "EXTREME_HILLS", "JUNGLE", "MESA", "PLAINS", "SAVANNA",
        "ICY", "THE_END", "BEACH", "FOREST", "OCEAN", "DESERT", "RIVER", "SWAMP", "MUSHROOM", "NETHER", "UNDERGROUND", "MOUNTAIN"};

    private static float biomeId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return -1;
        }
        var biomes = mc.level.registryAccess().registryOrThrow(Registries.BIOME);
        return biomes.getId(mc.level.getBiome(mc.player.blockPosition()).value());
    }

    private static float precipitation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0;
        }
        BlockPos pos = mc.player.blockPosition();
        return mc.level.getBiome(pos).value().getPrecipitationAt(pos).ordinal();
    }

    /** The category of the player's biome, from the biome tags that replaced categories. */
    private static float biomeCategory() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0;
        }
        var biome = mc.level.getBiome(mc.player.blockPosition());
        String path = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        String category;
        if (biome.is(BiomeTags.IS_NETHER)) {
            category = "NETHER";
        } else if (biome.is(BiomeTags.IS_END)) {
            category = "THE_END";
        } else if (biome.is(BiomeTags.IS_OCEAN)) {
            category = "OCEAN";
        } else if (biome.is(BiomeTags.IS_RIVER)) {
            category = "RIVER";
        } else if (biome.is(BiomeTags.IS_BEACH)) {
            category = "BEACH";
        } else if (biome.is(BiomeTags.IS_JUNGLE)) {
            category = "JUNGLE";
        } else if (biome.is(BiomeTags.IS_BADLANDS)) {
            category = "MESA";
        } else if (biome.is(BiomeTags.IS_SAVANNA)) {
            category = "SAVANNA";
        } else if (biome.is(BiomeTags.IS_TAIGA)) {
            category = "TAIGA";
        } else if (path.contains("peak") || path.contains("slope")) {
            category = "MOUNTAIN";
        } else if (biome.is(BiomeTags.IS_HILL) || path.contains("windswept")) {
            category = "EXTREME_HILLS";
        } else if (path.contains("frozen") || path.contains("snowy") || path.contains("ice")) {
            category = "ICY";
        } else if (biome.is(BiomeTags.IS_FOREST)) {
            category = "FOREST";
        } else if (path.contains("desert")) {
            category = "DESERT";
        } else if (path.contains("swamp")) {
            category = "SWAMP";
        } else if (path.contains("mushroom")) {
            category = "MUSHROOM";
        } else if (path.contains("cave") || path.contains("deep_dark")) {
            category = "UNDERGROUND";
        } else if (path.contains("plains") || path.contains("meadow")) {
            category = "PLAINS";
        } else {
            category = "NONE";
        }
        return java.util.Arrays.asList(CATEGORIES).indexOf(category);
    }

    private static float biomeClimate(boolean temperature) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0.0F;
        }
        var biome = mc.level.getBiome(mc.player.blockPosition()).value();
        return temperature ? biome.getBaseTemperature() : biome.getModifiedClimateSettings().downfall();
    }

    // ------------------------------------------------------------------ registration

    private void f(String name, FloatValue v) {
        values.put(name, loc -> GL20.glUniform1f(loc, v.get()));
        expressions.put(name, (PrExpr.F) v::get);
    }

    private void i(String name, IntValue v) {
        values.put(name, loc -> GL20.glUniform1i(loc, v.get()));
        expressions.put(name, (PrExpr.F) () -> v.get());
    }

    private void ivec2(String name, FloatValue x, FloatValue y) {
        values.put(name, loc -> GL20.glUniform2i(loc, (int) x.get(), (int) y.get()));
        expressions.put(name + ".x", (PrExpr.F) x::get);
        expressions.put(name + ".y", (PrExpr.F) y::get);
    }

    private void vec3(String name, Vector3f v, String... components) {
        values.put(name, loc -> GL20.glUniform3f(loc, v.x, v.y, v.z));
        expressions.put(name + "." + components[0], (PrExpr.F) () -> v.x);
        expressions.put(name + "." + components[1], (PrExpr.F) () -> v.y);
        expressions.put(name + "." + components[2], (PrExpr.F) () -> v.z);
    }

    private void mat(String name, Matrix4f m) {
        values.put(name, loc -> {
            m.get(matrix);
            GL20.glUniformMatrix4fv(loc, false, matrix);
        });
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                int c = col;
                int r = row;
                expressions.put(name + "." + c + "." + r, (PrExpr.F) () -> m.get(c, r));
            }
        }
    }

    @FunctionalInterface
    private interface FloatValue {
        float get();
    }

    @FunctionalInterface
    private interface IntValue {
        int get();
    }

    /**
     * The {@code uniform.<type>.<name>} and {@code variable.<type>.<name>} entries of
     * {@code shaders.properties}, in declaration order: each expression may read the built-in
     * uniforms and every entry declared before it. Values are computed once per frame; only
     * {@code uniform.*} entries reach the programs.
     */
    void addCustom(Map<String, String> properties, Consumer<String> warn) {
        // Packs name biomes of later versions (BIOME_PALE_GARDEN); such a biome is never the current one.
        PrExprParser parser = new PrExprParser(name -> {
            PrExpr e = expressions.get(name);
            return e == null && name.startsWith("BIOME_") ? new PrExpr.Constant(-1) : e;
        }, PrExprEnv.minecraft());
        properties.forEach((key, text) -> {
            boolean uniform = key.startsWith("uniform.");
            if (!uniform && !key.startsWith("variable.")) {
                return;
            }
            String[] parts = key.split("\\.", 3);
            if (parts.length < 3) {
                warn.accept("Invalid custom uniform " + key);
                return;
            }
            String type = parts[1];
            String name = parts[2];
            int size = switch (type) {
                case "float", "int", "bool" -> 1;
                case "vec2" -> 2;
                case "vec3" -> 3;
                case "vec4" -> 4;
                default -> 0;
            };
            if (size == 0) {
                warn.accept("Unknown type of " + key);
                return;
            }
            PrExpr expr;
            try {
                expr = parser.parse(text);
            } catch (PrExprException | RuntimeException e) {
                warn.accept("Cannot parse " + key + "=" + text + ": " + e.getMessage());
                return;
            }
            float[] value = new float[size];
            PrExpr source = expr;
            customUpdates.add(() -> evaluate(source, value));
            if (size == 1) {
                expressions.put(name, type.equals("bool") ? (PrExpr.B) () -> value[0] != 0.0F : (PrExpr.F) () -> value[0]);
            } else {
                expressions.put(name, (PrExpr.V) () -> value.clone());
                String[] components = {"x", "y", "z", "w"};
                for (int i = 0; i < size; i++) {
                    int c = i;
                    expressions.put(name + "." + components[i], (PrExpr.F) () -> value[c]);
                }
            }
            if (uniform) {
                values.put(name, switch (type) {
                    case "int", "bool" -> loc -> GL20.glUniform1i(loc, (int) value[0]);
                    case "vec2" -> loc -> GL20.glUniform2f(loc, value[0], value[1]);
                    case "vec3" -> loc -> GL20.glUniform3f(loc, value[0], value[1], value[2]);
                    case "vec4" -> loc -> GL20.glUniform4f(loc, value[0], value[1], value[2], value[3]);
                    default -> loc -> GL20.glUniform1f(loc, value[0]);
                });
            }
        });
    }

    private static void evaluate(PrExpr expr, float[] out) {
        switch (expr) {
            case PrExpr.F f -> out[0] = f.eval();
            case PrExpr.B b -> out[0] = b.eval() ? 1.0F : 0.0F;
            case PrExpr.V v -> {
                float[] r = v.eval();
                System.arraycopy(r, 0, out, 0, Math.min(r.length, out.length));
            }
        }
    }

    /** An expression reading a built-in (or earlier custom) uniform, or {@code null}. */
    PrExpr expression(String name) {
        return expressions.get(name);
    }

    /** Binds every uniform {@code program} declares. */
    List<Binding> bind(int program) {
        List<Binding> out = new ArrayList<>();
        values.forEach((name, value) -> {
            int location = GL20.glGetUniformLocation(program, name);
            if (location >= 0) {
                out.add(new Binding(location, value));
            }
        });
        return out;
    }

    static void upload(List<Binding> bindings) {
        for (Binding b : bindings) {
            b.value.upload(b.location);
        }
    }

    // ------------------------------------------------------------------ per frame

    /**
     * Computes the frame's values. {@code modelView} is the view rotation the world renders with
     * and {@code projection} its projection (bobbing included), as OptiFine's gbuffer matrices.
     */
    void beginFrame(Camera camera, Matrix4f modelView, Matrix4f projection, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        long now = System.nanoTime();
        frameTime = firstFrame ? 0.0F : Math.min(1.0F, (now - lastFrameNanos) / 1.0E9F);
        lastFrameNanos = now;
        frameTimeCounter = (frameTimeCounter + frameTime) % 3600.0F;
        frameCounter = (frameCounter + 1) % 720720;

        gbufferPreviousModelView.set(firstFrame ? modelView : gbufferModelView);
        gbufferPreviousProjection.set(firstFrame ? projection : gbufferProjection);
        gbufferModelView.set(modelView);
        gbufferModelView.invert(gbufferModelViewInverse);
        gbufferProjection.set(projection);
        gbufferProjection.invert(gbufferProjectionInverse);

        Vec3 pos = camera.getPosition();
        previousCameraPosition.set(firstFrame ? new Vector3f((float) pos.x, (float) pos.y, (float) pos.z) : cameraPosition);
        cameraPosition.set((float) pos.x, (float) pos.y, (float) pos.z);
        eyeAltitude = (float) pos.y;
        viewWidth = width;
        viewHeight = height;
        far = mc.gameRenderer.getRenderDistance();

        float[] fog = RenderSystem.getShaderFogColor();
        fogColor.set(fog[0], fog[1], fog[2]);
        fogStart = RenderSystem.getShaderFogStart();
        fogEnd = RenderSystem.getShaderFogEnd();
        FogType fluid = camera.getFluidInCamera();
        isEyeInWater = fluid == FogType.WATER ? 1 : fluid == FogType.LAVA ? 2 : fluid == FogType.POWDER_SNOW ? 3 : 0;
        screenBrightness = mc.options.gamma().get().floatValue();
        hideGui = mc.options.hideGui ? 1 : 0;

        if (level != null) {
            long dayTime = level.getDayTime();
            worldTime = (int) (dayTime % 24000L);
            worldDay = (int) (dayTime / 24000L);
            moonPhase = level.getMoonPhase();
            float celestial = level.getTimeOfDay(partialTick);
            sunAngle = celestial < 0.75F ? celestial + 0.25F : celestial - 0.75F;
            shadowAngle = sunAngle > 0.5F ? sunAngle - 0.5F : sunAngle;
            celestialPositions(celestial);
            shadowMatrices(celestial);
            rainStrength = level.getRainLevel(partialTick);
            Vec3 sky = level.getSkyColor(pos, partialTick);
            skyColor.set((float) sky.x, (float) sky.y, (float) sky.z);
            BlockPos eye = BlockPos.containing(pos);
            float block = level.getBrightness(LightLayer.BLOCK, eye) * 16.0F;
            float skyLight = level.getBrightness(LightLayer.SKY, eye) * 16.0F;
            eyeBrightnessX = block;
            eyeBrightnessY = skyLight;
            float step = firstFrame ? 1.0F : halfLife(config.eyeBrightnessHalflife);
            eyeBrightnessSmoothX += (block - eyeBrightnessSmoothX) * step;
            eyeBrightnessSmoothY += (skyLight - eyeBrightnessSmoothY) * step;
            float wet = rainStrength > wetness ? halfLife(config.wetnessHalflife / 20.0F) : halfLife(config.drynessHalflife / 20.0F);
            wetness += (rainStrength - wetness) * (firstFrame ? 1.0F : wet);
        }

        LocalPlayer player = mc.player;
        if (player != null) {
            nightVision = player.hasEffect(MobEffects.NIGHT_VISION) ? GameRenderer.getNightVisionScale(player, partialTick) : 0.0F;
            MobEffectInstance blind = player.getEffect(MobEffects.BLINDNESS);
            blindness = blind == null ? 0.0F : blind.isInfiniteDuration() ? 1.0F : Mth.clamp(blind.getDuration() / 20.0F, 0.0F, 1.0F);
            MobEffectInstance dark = player.getEffect(MobEffects.DARKNESS);
            darknessFactor = dark == null ? 0.0F : dark.getBlendFactor(player, partialTick) * mc.options.darknessEffectScale().get().floatValue();
            playerMood = player.getCurrentMood();
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            heldBlockLightValue = lightOf(main);
            heldBlockLightValue2 = lightOf(off);
        }
        firstFrame = false;
        for (Runnable update : customUpdates) {
            update.run();
        }
    }

    /**
     * OptiFine's shadow camera: 100 units above the player looking down, turned with the sun (or
     * the moon at night) and {@code sunPathRotation}, shifted by the camera position modulo
     * {@code shadowIntervalSize} so the map moves in steps instead of shimmering; an orthographic
     * projection {@code shadowDistance} wide on each side, or a perspective one with {@code shadowMapFov}.
     */
    void shadowMatrices(float celestial) {
        float angle = celestial * -360.0F;
        Matrix4f mv = new Matrix4f().translate(0.0F, 0.0F, -100.0F).rotateX((float) Math.toRadians(90.0));
        mv.rotateZ((float) Math.toRadians(sunAngle <= 0.5F ? angle : angle + 180.0F));
        mv.rotateX((float) Math.toRadians(config.sunPathRotation));
        float step = config.shadowIntervalSize;
        if (step > 0.0F && config.shadowMapFov <= 0.0F) {
            mv.translate(cameraPosition.x % step - step / 2.0F, cameraPosition.y % step - step / 2.0F,
                    cameraPosition.z % step - step / 2.0F);
        }
        shadowModelView.set(mv);
        shadowModelView.invert(shadowModelViewInverse);
        float half = config.shadowDistance;
        if (config.shadowMapFov > 0.0F) {
            shadowProjection.setPerspective((float) Math.toRadians(config.shadowMapFov), 1.0F, 0.05F, 256.0F);
        } else {
            shadowProjection.setOrtho(-half, half, -half, half, 0.05F, 256.0F);
        }
        shadowProjection.invert(shadowProjectionInverse);
    }

    /** Exponential smoothing factor for one frame with a half-life in seconds. */
    private float halfLife(float seconds) {
        return seconds <= 0.0F ? 1.0F : 1.0F - (float) Math.pow(0.5, frameTime / seconds);
    }

    private static int lightOf(ItemStack stack) {
        return net.pryzma.light.PrDynamicLights.getLightLevel(stack);
    }

    /** The sun and moon where the game draws them, in view space, 100 units away, as OptiFine computes them. */
    private void celestialPositions(float celestial) {
        Matrix4f m = new Matrix4f(gbufferModelView);
        m.rotate(Axis.YP.rotationDegrees(-90.0F));
        m.rotate(Axis.ZP.rotationDegrees(config.sunPathRotation));
        m.rotate(Axis.XP.rotationDegrees(celestial * 360.0F));
        Vector4f sun = m.transform(new Vector4f(0.0F, 100.0F, 0.0F, 0.0F));
        Vector4f moon = m.transform(new Vector4f(0.0F, -100.0F, 0.0F, 0.0F));
        sunPosition.set(sun.x, sun.y, sun.z);
        moonPosition.set(moon.x, moon.y, moon.z);
        shadowLightPosition.set(sunAngle <= 0.5F ? sunPosition : moonPosition);
        Vector4f up = gbufferModelView.transform(new Vector4f(0.0F, 100.0F, 0.0F, 0.0F));
        upPosition.set(up.x, up.y, up.z);
    }

    /** Normal matrix of a model-view matrix: the inverse transpose of its rotation part. */
    static Matrix3f normalMatrix(Matrix4f modelView, Matrix3f out) {
        return out.set(modelView).invert().transpose();
    }

    void close() {
        MemoryUtil.memFree(matrix);
    }
}
