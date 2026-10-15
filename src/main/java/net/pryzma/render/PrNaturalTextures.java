package net.pryzma.render;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;

/**
 * OptiFine-compatible Natural Textures engine.
 * Eliminates repeating grid patterns by deterministically rotating/flipping quad UVs based on block world position.
 */
public final class PrNaturalTextures {
    private static final PrNaturalProperties EMPTY = new PrNaturalProperties(1, false);

    private static final String DEFAULT_PROPERTIES = """
        # Grass
        grass_block_side = F
        grass_block_side_overlay = F
        grass_block_snow = F
        mycelium_side = F
        mycelium_top = 4F
        dirt_path_top = 4
        dirt_path_side = F
        # Snow
        snow = 4F
        # Dirt
        coarse_dirt = 4F
        podzol_top = 4F
        podzol_side = F
        farmland = 2F
        farmland_moist = 2F
        # Stone
        granite = 2F
        diorite = 2F
        andesite = 2F
        sandstone_top = 4
        sandstone_bottom = 4F
        stone_slab_top = F
        end_stone = 4
        # Gravel
        gravel = 2
        clay = 4F
        # Logs
        oak_log = 2F
        spruce_log = 2F
        birch_log = F
        jungle_log = 2F
        acacia_log = 2F
        dark_oak_log = 2F
        oak_log_top = 4F
        spruce_log_top = 4F
        birch_log_top = 4F
        jungle_log_top = 4F
        acacia_log_top = 4F
        dark_oak_log_top = 4F
        # Leaves
        oak_leaves = 2F
        spruce_leaves = 2F
        birch_leaves = 2F
        jungle_leaves = 2
        dark_oak_leaves = 2F
        acacia_leaves = 2F
        # Ores
        gold_ore = 2F
        iron_ore = 2F
        coal_ore = 2F
        diamond_ore = 2F
        redstone_ore = 2F
        lapis_ore = 2F
        # Nether
        netherrack = 4F
        nether_quartz_ore = 2
        soul_sand = 4F
        glowstone = 4
        # Redstone
        redstone_lamp_on = 4F
        redstone_lamp = 4F
        # Prismarine
        prismarine = 4F
        # Misc
        obsidian = 4F
        cactus_side = 2F
        """;

    private static volatile Map<String, PrNaturalProperties> rules = loadDefaultRules();
    private static final ConcurrentHashMap<TextureAtlasSprite, PrNaturalProperties> spriteCache = new ConcurrentHashMap<>();

    private PrNaturalTextures() {
    }

    public static void clearCaches() {
        spriteCache.clear();
        for (PrNaturalProperties props : rules.values()) {
            props.clearCache();
        }
    }

    public static void reload(@Nullable ResourceManager manager) {
        Map<String, PrNaturalProperties> loaded = null;
        if (manager != null) {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("minecraft", "optifine/natural.properties");
            Optional<Resource> res = manager.getResource(loc);
            if (res.isEmpty()) {
                loc = ResourceLocation.fromNamespaceAndPath("minecraft", "mcpatcher/natural.properties");
                res = manager.getResource(loc);
            }
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    loaded = parseRules(new InputStreamReader(in, StandardCharsets.UTF_8));
                    Pryzma.LOGGER.info("Natural Textures: Loaded {} rules from {}", loaded.size(), loc);
                } catch (Exception e) {
                    Pryzma.LOGGER.warn("Natural Textures: Failed to read {}: {}", loc, e.getMessage());
                }
            }
        }
        if (loaded == null || loaded.isEmpty()) {
            loaded = loadDefaultRules();
            Pryzma.LOGGER.debug("Natural Textures: Using default configuration ({} rules)", loaded.size());
        }
        rules = loaded;
        clearCaches();
    }

    public static Map<String, PrNaturalProperties> loadDefaultRules() {
        return parseRules(new StringReader(DEFAULT_PROPERTIES));
    }

    public static Map<String, PrNaturalProperties> parseRules(java.io.Reader reader) {
        Map<String, PrNaturalProperties> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                PrNaturalProperties props = PrNaturalProperties.parse(val);
                if (props != null && props.isValid()) {
                    map.put(key, props);
                    if (!key.startsWith("block/")) {
                        map.put("block/" + key, props);
                    }
                    if (key.indexOf(':') < 0) {
                        map.put("minecraft:" + key, props);
                        if (!key.startsWith("block/")) {
                            map.put("minecraft:block/" + key, props);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Pryzma.LOGGER.warn("Natural Textures: Error parsing properties: {}", e.getMessage());
        }
        return map;
    }

    @Nullable
    public static PrNaturalProperties getProperties(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null) {
            return null;
        }
        PrNaturalProperties cached = spriteCache.get(sprite);
        if (cached != null) {
            return cached == EMPTY ? null : cached;
        }
        ResourceLocation name = sprite.contents().name();
        String fullPath = name.toString();
        String path = name.getPath();

        PrNaturalProperties found = rules.get(fullPath);
        if (found == null) {
            found = rules.get(path);
        }
        if (found == null && path.startsWith("block/")) {
            found = rules.get(path.substring("block/".length()));
        }
        spriteCache.put(sprite, found != null ? found : EMPTY);
        return found;
    }

    public static List<BakedQuad> processQuads(BlockState state, @Nullable BlockPos pos, @Nullable Direction direction, List<BakedQuad> quads) {
        if (quads.isEmpty() || pos == null || !PryzmaConfig.prNaturalTextures) {
            return quads;
        }
        if (state != null && state.getBlock() instanceof WallBlock) {
            return quads;
        }

        List<BakedQuad> modified = null;
        int size = quads.size();
        for (int i = 0; i < size; i++) {
            BakedQuad quad = quads.get(i);
            BakedQuad natural = getNaturalTexture(state, pos, direction, quad);
            if (natural != quad) {
                if (modified == null) {
                    modified = new ArrayList<>(quads);
                }
                modified.set(i, natural);
            }
        }
        return modified != null ? modified : quads;
    }

    public static BakedQuad getNaturalTexture(BlockState state, BlockPos pos, @Nullable Direction direction, BakedQuad quad) {
        TextureAtlasSprite sprite = quad.getSprite();
        if (sprite == null) {
            return quad;
        }
        PrNaturalProperties props = getProperties(sprite);
        if (props == null) {
            return quad;
        }

        Direction face = quad.getDirection();
        int side = face != null ? face.get3DDataValue() : (direction != null ? direction.get3DDataValue() : 0);
        int rand = getRandom(pos, side);
        int rotate = 0;
        boolean flipU = false;

        if (props.rotation > 1) {
            rotate = rand & 3;
        }
        if (props.rotation == 2) {
            rotate = (rotate / 2) * 2;
        }
        if (props.flip) {
            flipU = (rand & 4) != 0;
        }
        if (rotate == 0 && !flipU) {
            return quad;
        }
        return props.getQuad(quad, rotate, flipU);
    }

    public static int getRandom(BlockPos blockPos, int face) {
        int rand = intHash(face + 37);
        rand = intHash(rand + blockPos.getX());
        rand = intHash(rand + blockPos.getZ());
        return intHash(rand + blockPos.getY());
    }

    public static int intHash(int x) {
        x = x ^ 61 ^ (x >> 16);
        x += x << 3;
        x ^= x >> 4;
        x *= 668265261;
        return x ^ (x >> 15);
    }
}
