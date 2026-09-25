package net.pryzma.render;

import java.util.Map;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.pryzma.PryzmaConfig;

/**
 * Which Animations switch governs a block atlas sprite: water, lava, fire (all fire and campfire
 * frames), the nether portal, and everything else under Terrain Animated. Other atlases always
 * animate, as in 1.x.
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

    public static Kind kindOf(TextureAtlasSprite sprite) {
        if (!InventoryMenu.BLOCK_ATLAS.equals(sprite.atlasLocation())) {
            return Kind.OTHER;
        }
        ResourceLocation name = sprite.contents().name();
        Kind kind = ResourceLocation.DEFAULT_NAMESPACE.equals(name.getNamespace()) ? NAMED.get(name.getPath()) : null;
        return kind != null ? kind : Kind.TERRAIN;
    }
}
