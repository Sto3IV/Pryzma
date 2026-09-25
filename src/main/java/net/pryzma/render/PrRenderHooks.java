package net.pryzma.render;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.pryzma.PryzmaConfig;

/** Pryzma options that act on client rendering: rain, vignette, fog, particles, Smooth FPS. */
public final class PrRenderHooks {
    private PrRenderHooks() {
    }

    /** Rain and Snow: Default follows the graphics mode, Fast and Fancy force it. */
    public static boolean isRainFancy(boolean fancyGraphics) {
        return switch (PryzmaConfig.prRain) {
            case 1 -> false;
            case 2 -> true;
            default -> fancyGraphics;
        };
    }

    public static boolean isRainOff() {
        return PryzmaConfig.prRain == 3;
    }

    /** Trees: Default follows the graphics mode; Fancy and Smart keep leaves transparent, Fast makes them opaque. */
    public static boolean isTreesFancy(boolean fancyGraphics) {
        return PryzmaConfig.prTrees == 0 ? fancyGraphics : PryzmaConfig.prTrees != 1;
    }

    public static boolean isTreesSmart() {
        return PryzmaConfig.prTrees == 4;
    }

    /** Vignette: Default follows the graphics mode, Fast hides it, Fancy always draws it. */
    public static boolean isVignetteEnabled(boolean fancyGraphics) {
        return switch (PryzmaConfig.prVignette) {
            case 1 -> false;
            case 2 -> true;
            default -> fancyGraphics;
        };
    }

    /** Smooth Lighting Level: vanilla's 0.2 occlusion shade scaled towards 1 (no shadow). */
    public static float shadeBrightness(float vanilla) {
        return vanilla >= 1.0F ? vanilla : 1.0F - (1.0F - vanilla) * (float) PryzmaConfig.prAoLevel;
    }

    /** The per-type particle switches of the Animations page. */
    public static boolean isParticleEnabled(ParticleOptions options) {
        ParticleType<?> type = options.getType();
        if (type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.EXPLOSION || type == ParticleTypes.POOF) {
            return PryzmaConfig.prAnimatedExplosion;
        }
        if (type == ParticleTypes.UNDERWATER) {
            return PryzmaConfig.prWaterParticles;
        }
        if (type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE) {
            return PryzmaConfig.prAnimatedSmoke;
        }
        if (type == ParticleTypes.ENTITY_EFFECT || type == ParticleTypes.EFFECT || type == ParticleTypes.INSTANT_EFFECT
                || type == ParticleTypes.WITCH) {
            return PryzmaConfig.prPotionParticles;
        }
        if (type == ParticleTypes.PORTAL) {
            return PryzmaConfig.prPortalParticles;
        }
        if (type == ParticleTypes.FLAME || type == ParticleTypes.SOUL_FIRE_FLAME) {
            return PryzmaConfig.prAnimatedFlame;
        }
        if (type == ParticleTypes.DUST) {
            return PryzmaConfig.prAnimatedRedstone;
        }
        if (type == ParticleTypes.DRIPPING_WATER || type == ParticleTypes.DRIPPING_LAVA) {
            return PryzmaConfig.prDrippingWaterLava;
        }
        if (type == ParticleTypes.FIREWORK) {
            return PryzmaConfig.prFireworkParticles;
        }
        return true;
    }

    /**
     * Terrain fog in clear air: it starts at Fog Start times the far plane, and with Fog off it
     * starts beyond anything rendered. Water, lava, snow, blindness and foggy biomes keep theirs.
     */
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE || !isClearAir(event.getCamera())) {
            return;
        }
        float far = event.getFarPlaneDistance();
        if (PryzmaConfig.isFogOff()) {
            event.setNearPlaneDistance(far * 16.0F);
            event.setFarPlaneDistance(far * 16.0F + 1.0F);
        } else {
            event.setNearPlaneDistance(far * PryzmaConfig.prFogStart);
        }
        event.setCanceled(true);
    }

    private static boolean isClearAir(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return false;
        }
        var pos = camera.getBlockPosition();
        return mc.level != null && !mc.level.effects().isFoggyAt(pos.getX(), pos.getZ())
                && !mc.gui.getBossOverlay().shouldCreateWorldFog();
    }

    /** Smooth FPS: wait for the GPU before the terrain, as 1.x did, to even out frame times. */
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (PryzmaConfig.prSmoothFps && event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            GL11.glFinish();
        }
    }
}
