package net.pryzma.render;

import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import net.pryzma.PryzmaConfig;
import net.pryzma.mixin.SpriteContentsAnimatedTextureAccessor;
import net.pryzma.mixin.SpriteContentsAnimationStateAccessor;

/**
 * Which Animations switch governs a block atlas sprite: water, lava, fire (all fire and campfire
 * frames), the nether portal, and everything else under Terrain Animated.
 */
public final class PrAnimations {
    public enum Kind {
        WATER, LAVA, FIRE, PORTAL, TERRAIN, OTHER;

        public boolean enabled() {
            return switch (this) {
                case WATER -> PryzmaConfig.prAnimatedWater != 2;
                case LAVA -> PryzmaConfig.prAnimatedLava != 2;
                case FIRE -> PryzmaConfig.prAnimatedFire;
                case PORTAL -> PryzmaConfig.prAnimatedPortal;
                case TERRAIN -> PryzmaConfig.prAnimatedTerrain;
                case OTHER -> true;
            };
        }
    }

    private static final Map<String, Kind> NAMED = Map.ofEntries(
            Map.entry("block/water_still", Kind.WATER),
            Map.entry("block/water_flow", Kind.WATER),
            Map.entry("block/lava_still", Kind.LAVA),
            Map.entry("block/lava_flow", Kind.LAVA),
            Map.entry("block/fire_0", Kind.FIRE),
            Map.entry("block/fire_1", Kind.FIRE),
            Map.entry("block/soul_fire_0", Kind.FIRE),
            Map.entry("block/soul_fire_1", Kind.FIRE),
            Map.entry("block/campfire_fire", Kind.FIRE),
            Map.entry("block/campfire_log_lit", Kind.FIRE),
            Map.entry("block/soul_campfire_fire", Kind.FIRE),
            Map.entry("block/soul_campfire_log_lit", Kind.FIRE),
            Map.entry("block/nether_portal", Kind.PORTAL));

    private PrAnimations() {
    }

    public static Kind kindOf(Identifier name) {
        if (name == null) {
            return Kind.OTHER;
        }
        Kind kind = Identifier.DEFAULT_NAMESPACE.equals(name.getNamespace()) ? NAMED.get(name.getPath()) : null;
        return kind != null ? kind : Kind.TERRAIN;
    }

    /** Animations that advance: all but the switched ones whose switch is off. */
    public static int countRunning(List<SpriteContents.AnimationState> states) {
        if (states == null) {
            return 0;
        }
        int running = 0;
        for (SpriteContents.AnimationState state : states) {
            try {
                SpriteContents.AnimatedTexture info = ((SpriteContentsAnimationStateAccessor) (Object) state).prGetAnimationInfo();
                if (info != null) {
                    SpriteContents contents = ((SpriteContentsAnimatedTextureAccessor) (Object) info).prGetContents();
                    if (contents != null) {
                        Kind kind = kindOf(contents.name());
                        if (kind != null && !kind.enabled()) {
                            continue;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            running++;
        }
        return running;
    }
}
