package net.pryzma.sky;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.pryzma.core.match.PrBiomeMatcher;
import net.pryzma.core.match.PrRangeList;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrProperties;

/**
 * One OptiFine sky layer ({@code sky/world<N>/sky<i>.properties}): a cube-mapped texture (3x2 faces)
 * shown between fade times, optionally rotating with the sun, filtered by weather, day number,
 * biome and height.
 */
final class PrSkyLayer {
    private static final float[] DEFAULT_AXIS = {1.0F, 0.0F, 0.0F};

    final ResourceLocation texture;
    private final int startFadeIn;
    private final int endFadeIn;
    private final int startFadeOut;
    private final int endFadeOut;
    final PrSkyBlend blend;
    private final boolean rotate;
    private final float speed;
    private final float[] axis;
    private final PrRangeList days;
    private final int daysLoop;
    private final boolean weatherClear;
    private final boolean weatherRain;
    private final boolean weatherThunder;
    private final PrBiomeMatcher biomes;
    private final PrRangeList heights;
    private final float transition;

    // Smoothing of the biome/height brightness, render thread only.
    private Level smoothLevel;
    private float smoothValue;
    private long smoothMillis;

    private PrSkyLayer(ResourceLocation texture, int[] fade, PrSkyBlend blend, boolean rotate, float speed, float[] axis,
            PrRangeList days, int daysLoop, boolean[] weather, PrBiomeMatcher biomes, PrRangeList heights, float transition) {
        this.texture = texture;
        this.startFadeIn = fade[0];
        this.endFadeIn = fade[1];
        this.startFadeOut = fade[2];
        this.endFadeOut = fade[3];
        this.blend = blend;
        this.rotate = rotate;
        this.speed = speed;
        this.axis = axis;
        this.days = days;
        this.daysLoop = daysLoop;
        this.weatherClear = weather[0];
        this.weatherRain = weather[1];
        this.weatherThunder = weather[2];
        this.biomes = biomes;
        this.heights = heights;
        this.transition = transition;
    }

    static Optional<PrSkyLayer> parse(PrProperties p, int index, Consumer<String> warn) {
        String source = p.get("source", "./sky" + index + ".png");
        ResourceLocation texture = PrPaths.resolveLocation(PrPaths.withExtension(source, ".png"), p.location());
        if (texture == null) {
            warn.accept("Invalid source '" + source + "' in " + p);
            return Optional.empty();
        }
        int[] fade = {parseTime(p.get("startFadeIn"), warn), parseTime(p.get("endFadeIn"), warn),
                parseTime(p.get("startFadeOut"), warn), parseTime(p.get("endFadeOut"), warn)};
        if (fade[0] < 0 && fade[1] < 0 && fade[2] < 0 && fade[3] < 0) {
            fade = new int[] {0, 0, 24000, 24000};
        }
        if (fade[0] < 0 || fade[1] < 0 || fade[3] < 0) {
            warn.accept("startFadeIn, endFadeIn and endFadeOut are required in " + p);
            return Optional.empty();
        }
        int fadeInTime = normalize(fade[1] - fade[0]);
        if (fade[2] < 0) {
            fade[2] = normalize(fade[3] - fadeInTime);
            if (between(fade[2], fade[0], fade[1])) {
                fade[2] = fade[1];
            }
        }
        int sum = fadeInTime + normalize(fade[2] - fade[1]) + normalize(fade[3] - fade[2]) + normalize(fade[0] - fade[3]);
        if (sum != 0 && sum != 24000) {
            warn.accept("Fade times do not add up to 24h (" + sum + ") in " + p);
            return Optional.empty();
        }
        float speed = p.getFloat("speed", 1.0F);
        int daysLoop = p.getInt("daysLoop", 8);
        if (speed < 0.0F || daysLoop <= 0) {
            warn.accept("Invalid speed or daysLoop in " + p);
            return Optional.empty();
        }
        String weatherList = p.get("weather", "clear").toLowerCase(Locale.ROOT);
        boolean[] weather = {false, false, false};
        for (String w : weatherList.trim().split("\\s+")) {
            switch (w) {
                case "clear" -> weather[0] = true;
                case "rain" -> weather[1] = true;
                case "thunder" -> weather[2] = true;
                default -> warn.accept("Unknown weather '" + w + "' in " + p);
            }
        }
        return Optional.of(new PrSkyLayer(texture, fade, PrSkyBlend.parse(p.get("blend")), p.getBool("rotate", true), speed,
                parseAxis(p.get("axis"), warn), PrRangeList.parse(p.get("days")), daysLoop, weather,
                PrBiomeMatcher.parse(p.get("biomes")), PrRangeList.parseSigned(p.get("heights")),
                p.getFloat("transition", 1.0F)));
    }

    /** {@code hh:mm} to ticks, where 06:00 is tick 0. -1 when absent or malformed. */
    static int parseTime(String s, Consumer<String> warn) {
        if (s == null) {
            return -1;
        }
        String[] parts = s.trim().split(":");
        try {
            if (parts.length == 2) {
                int hour = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());
                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                    return Math.floorMod(hour - 6, 24) * 1000 + (int) (minute / 60.0 * 1000.0);
                }
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        warn.accept("Invalid time '" + s + "'");
        return -1;
    }

    /** OptiFine axis: {@code x y z} in pack space becomes {@code (z, y, -x)}. */
    static float[] parseAxis(String s, Consumer<String> warn) {
        if (s == null) {
            return DEFAULT_AXIS;
        }
        String[] parts = s.trim().split("\\s+");
        if (parts.length == 3) {
            try {
                float x = Float.parseFloat(parts[0]);
                float y = Float.parseFloat(parts[1]);
                float z = Float.parseFloat(parts[2]);
                if (x * x + y * y + z * z >= 1.0E-5F) {
                    return new float[] {z, y, -x};
                }
            } catch (NumberFormatException ignored) {
                // reported below
            }
        }
        warn.accept("Invalid axis '" + s + "'");
        return DEFAULT_AXIS;
    }

    static int normalize(int ticks) {
        return Math.floorMod(ticks, 24000);
    }

    static boolean between(int t, int start, int end) {
        return start <= end ? t >= start && t <= end : t >= start || t <= end;
    }

    boolean isActive(long dayTime, int timeOfDay) {
        // An always-on layer ends at 24000 and starts at 0: the same instant, so there is no off window.
        int off = normalize(endFadeOut);
        int on = normalize(startFadeIn);
        if (off != on && between(timeOfDay, off, on)) {
            return false;
        }
        if (days != null) {
            long shifted = dayTime - startFadeIn;
            while (shifted < 0) {
                shifted += 24000L * daysLoop;
            }
            int dayOfLoop = (int) (shifted / 24000L) % daysLoop;
            return days.contains(dayOfLoop);
        }
        return true;
    }

    float fadeBrightness(int timeOfDay) {
        if (between(timeOfDay, startFadeIn, endFadeIn)) {
            int length = normalize(endFadeIn - startFadeIn);
            return length == 0 ? 1.0F : normalize(timeOfDay - startFadeIn) / (float) length;
        }
        if (between(timeOfDay, endFadeIn, startFadeOut)) {
            return 1.0F;
        }
        if (between(timeOfDay, startFadeOut, endFadeOut)) {
            int length = normalize(endFadeOut - startFadeOut);
            return length == 0 ? 1.0F : 1.0F - normalize(timeOfDay - startFadeOut) / (float) length;
        }
        return 0.0F;
    }

    float weatherBrightness(float rain, float thunderRel) {
        float b = 0.0F;
        if (weatherClear) {
            b += 1.0F - rain;
        }
        if (weatherRain) {
            b += rain - thunderRel;
        }
        if (weatherThunder) {
            b += thunderRel;
        }
        return Math.clamp(b, 0.0F, 1.0F);
    }

    /** Biome / height filter, eased over {@code transition} seconds as OptiFine's SmoothFloat does. */
    float positionBrightness(Level level, BlockPos camera) {
        if (biomes == null && heights == null) {
            return 1.0F;
        }
        float target = 1.0F;
        if (biomes != null && !biomes.matches(level.getBiome(camera))) {
            target = 0.0F;
        }
        if (heights != null && !heights.contains(camera.getY())) {
            target = 0.0F;
        }
        long now = System.currentTimeMillis();
        if (smoothLevel != level) {
            smoothLevel = level;
            smoothValue = target;
            smoothMillis = now;
            return target;
        }
        float dt = (now - smoothMillis) / 1000.0F;
        smoothMillis = now;
        smoothValue = smooth(smoothValue, target, dt, transition);
        return smoothValue;
    }

    /** OptiFine SmoothFloat step, frame-rate independent. */
    static float smooth(float previous, float target, float dt, float fadeSeconds) {
        if (dt <= 0.0F) {
            return previous;
        }
        float delta = target - previous;
        if (fadeSeconds <= 0.0F || dt >= fadeSeconds || Math.abs(delta) <= 1.0E-6F) {
            return target;
        }
        float updates = fadeSeconds / dt;
        float correction = 4.61F - 1.0F / (0.13F + updates / 10.0F);
        return previous + delta * Math.clamp(dt / fadeSeconds * correction, 0.0F, 1.0F);
    }

    /** Rotation applied before the cube: the layer turns with the sun, plus a per-day drift for fractional speeds. */
    void rotate(PoseStack pose, long dayTime, float celestialAngle) {
        if (!rotate) {
            return;
        }
        float dayStart = 0.0F;
        if (speed != Math.round(speed)) {
            long day = (dayTime + 18000L) / 24000L;
            dayStart = (float) ((day * (double) (speed % 1.0F)) % 1.0);
        }
        float angle = (float) Math.toRadians(360.0F * (dayStart + celestialAngle * speed));
        pose.mulPose(new Quaternionf().rotationAxis(angle, axis[0], axis[1], axis[2]));
    }

    /** Emits the six cube faces; the texture is a 3x2 grid of faces, OptiFine face order. */
    static void emitCube(PoseStack pose, BufferBuilder buffer) {
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(90.0F)));
        pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-90.0F)));
        emitFace(pose, buffer, 4);
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(90.0F)));
        emitFace(pose, buffer, 1);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(-90.0F)));
        emitFace(pose, buffer, 0);
        pose.popPose();
        pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(90.0F)));
        emitFace(pose, buffer, 5);
        pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(90.0F)));
        emitFace(pose, buffer, 2);
        pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(90.0F)));
        emitFace(pose, buffer, 3);
        pose.popPose();
    }

    private static void emitFace(PoseStack pose, BufferBuilder buffer, int side) {
        float u = (side % 3) / 3.0F;
        float v = (side / 3) / 2.0F;
        Matrix4f m = pose.last().pose();
        buffer.addVertex(m, -100.0F, -100.0F, -100.0F).setUv(u, v);
        buffer.addVertex(m, -100.0F, -100.0F, 100.0F).setUv(u, v + 0.5F);
        buffer.addVertex(m, 100.0F, -100.0F, 100.0F).setUv(u + 1.0F / 3.0F, v + 0.5F);
        buffer.addVertex(m, 100.0F, -100.0F, -100.0F).setUv(u + 1.0F / 3.0F, v);
    }

    @Override
    public String toString() {
        return texture + " " + startFadeIn + "-" + endFadeIn + " " + startFadeOut + "-" + endFadeOut + " " + blend;
    }
}
