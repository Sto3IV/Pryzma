package net.pryzma.ctm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.core.match.PrBiomeMatcher;
import net.pryzma.core.match.PrBlockMatcher;
import net.pryzma.core.match.PrRangeList;
import net.pryzma.core.match.PrTextMatcher;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrProperties;

/**
 * One OptiFine CTM properties file. Parsed on the atlas worker; the sprite fields are bound once
 * the block atlas is stitched, after which the rule is immutable and shared by all build threads.
 */
public final class PrCtmRule {
    public static final int CONNECT_BLOCK = 1;
    public static final int CONNECT_TILE = 2;
    public static final int CONNECT_STATE = 3;

    /**
     * Chunk layer of an overlay rule. Kept apart from {@link RenderType} so parsing never loads the
     * render classes; resolved when quads are built.
     */
    enum Layer {
        SOLID, CUTOUT_MIPPED, CUTOUT, TRANSLUCENT, TRIPWIRE;

        RenderType type() {
            return switch (this) {
                case SOLID -> RenderType.solid();
                case CUTOUT_MIPPED -> RenderType.cutoutMipped();
                case CUTOUT -> RenderType.cutout();
                case TRANSLUCENT -> RenderType.translucent();
                case TRIPWIRE -> RenderType.tripwire();
            };
        }
    }

    /** A tile reference: an atlas sprite, or one of the {@code <skip>} / {@code <default>} markers. */
    public record Tile(ResourceLocation sprite, ResourceLocation file, boolean skip, boolean keep) {
        static final Tile SKIP = new Tile(null, null, true, false);
        static final Tile DEFAULT = new Tile(null, null, false, true);
    }

    final ResourceLocation source;
    final PrCtmMethod method;
    /** {@code null} when absent (any block); present with unknown blocks only, it matches nothing. */
    final PrBlockMatcher matchBlocks;
    final List<ResourceLocation> matchTiles;
    final List<Tile> tiles;
    final int connect;
    final int faces;
    final PrBiomeMatcher biomes;
    final PrRangeList heights;
    final PrTextMatcher name;
    final boolean innerSeams;
    final int[] ctmTileIndexes;
    final int width;
    final int height;
    final int[] sumWeights;
    final int sumAllWeights;
    final int randomLoops;
    final int symmetry;
    final boolean linked;
    /** {@code null} when absent. */
    final PrBlockMatcher connectBlocks;
    final List<ResourceLocation> connectTiles;
    final int tintIndex;
    /** {@code null} means untinted, as OptiFine's default of air. */
    final BlockState tintBlock;
    final Layer layer;

    // Bound after stitching. A null tile sprite is <skip> unless tileKeep marks it <default>.
    TextureAtlasSprite[] tileSprites;
    boolean[] tileKeep;
    TextureAtlasSprite[] matchSprites;
    TextureAtlasSprite[] connectSprites;

    private PrCtmRule(Builder b) {
        this.source = b.source;
        this.method = b.method;
        this.matchBlocks = b.matchBlocks;
        this.matchTiles = b.matchTiles;
        this.tiles = b.tiles;
        this.connect = b.connect;
        this.faces = b.faces;
        this.biomes = b.biomes;
        this.heights = b.heights;
        this.name = b.name;
        this.innerSeams = b.innerSeams;
        this.ctmTileIndexes = b.ctmTileIndexes;
        this.width = b.width;
        this.height = b.height;
        this.sumWeights = b.sumWeights;
        this.sumAllWeights = b.sumAllWeights;
        this.randomLoops = b.randomLoops;
        this.symmetry = b.symmetry;
        this.linked = b.linked;
        this.connectBlocks = b.connectBlocks;
        this.connectTiles = b.connectTiles;
        this.tintIndex = b.tintIndex;
        this.tintBlock = b.tintBlock;
        this.layer = b.layer;
    }

    public ResourceLocation source() {
        return source;
    }

    public PrCtmMethod method() {
        return method;
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public List<ResourceLocation> matchTiles() {
        return matchTiles;
    }

    public List<ResourceLocation> connectTiles() {
        return connectTiles;
    }

    public PrBlockMatcher matchBlocks() {
        return matchBlocks;
    }

    boolean hasFace(int side) {
        return (faces & (1 << side)) != 0;
    }

    @Override
    public String toString() {
        return source + " (" + method + ")";
    }

    // ------------------------------------------------------------------ parsing

    private static final class Builder {
        ResourceLocation source;
        PrCtmMethod method;
        PrBlockMatcher matchBlocks;
        List<ResourceLocation> matchTiles;
        List<Tile> tiles;
        int connect;
        int faces = 63;
        PrBiomeMatcher biomes;
        PrRangeList heights;
        PrTextMatcher name;
        boolean innerSeams;
        int[] ctmTileIndexes;
        int width;
        int height;
        int[] sumWeights;
        int sumAllWeights = 1;
        int randomLoops;
        int symmetry = 1;
        boolean linked;
        PrBlockMatcher connectBlocks;
        List<ResourceLocation> connectTiles;
        int tintIndex = -1;
        BlockState tintBlock;
        Layer layer = Layer.CUTOUT_MIPPED;
    }

    /**
     * Parses and validates a rule. Warnings name the file; an invalid file yields empty, exactly
     * where OptiFine would drop it.
     *
     * @param spriteExists tells whether a sprite name already exists in the atlas ({@code matchTiles}
     *                     auto-detection from the file name)
     */
    static Optional<PrCtmRule> parse(PrProperties p, Predicate<ResourceLocation> spriteExists,
            Consumer<String> warn) {
        Consumer<String> w = msg -> warn.accept(msg + " in " + p);
        Builder b = new Builder();
        b.source = p.location();
        b.method = PrCtmMethod.parse(p.get("method"));
        if (b.method == null) {
            w.accept("Unknown method '" + p.get("method") + "'");
            return Optional.empty();
        }
        // An absent list is null and matches anything; a list naming only unknown blocks is
        // present but empty and matches nothing, as in OptiFine.
        String metadata = p.get("metadata");
        String blockList = p.get("matchBlocks");
        b.matchBlocks = blockList == null ? null : PrBlockMatcher.parse(blockList, metadata, w);
        if (metadata != null && (b.matchBlocks == null || !b.matchBlocks.usedLegacy())) {
            w.accept("metadata is only supported with legacy block ids");
            return Optional.empty();
        }
        b.matchTiles = parseSpriteList(p.get("matchTiles"), p);
        String fileName = p.name();
        if (b.matchBlocks == null && fileName.startsWith("block_")) {
            ResourceLocation blockId = ResourceLocation.tryParse(fileName.substring("block_".length()));
            if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
                b.matchBlocks = PrBlockMatcher.parse(blockId.toString(), null, w);
            }
        }
        if (b.matchBlocks == null && b.matchTiles == null) {
            ResourceLocation auto = spriteId(fileName, p);
            if (auto != null && spriteExists.test(auto)) {
                b.matchTiles = List.of(auto);
            }
        }
        if (b.matchBlocks == null && b.matchTiles == null) {
            w.accept("No matchBlocks or matchTiles");
            return Optional.empty();
        }

        String tileList = p.get("tiles");
        b.tiles = tileList == null ? null : parseTiles(tileList, p, w);
        if (b.tiles == null) {
            w.accept("No tiles specified");
            return Optional.empty();
        }

        String connect = p.get("connect");
        if (connect == null) {
            b.connect = b.matchBlocks != null ? CONNECT_BLOCK : CONNECT_TILE;
        } else {
            b.connect = switch (connect.trim()) {
                case "block" -> CONNECT_BLOCK;
                case "tile" -> CONNECT_TILE;
                case "state" -> CONNECT_STATE;
                default -> -1;
            };
            if (b.connect < 0) {
                w.accept("Unknown connect '" + connect + "'");
                return Optional.empty();
            }
        }
        if (p.has("faces")) {
            b.faces = parseFaces(p.get("faces"));
            if (b.faces < 0) {
                w.accept("Invalid faces '" + p.get("faces") + "'");
                return Optional.empty();
            }
        }
        b.biomes = PrBiomeMatcher.parse(p.get("biomes"));
        b.heights = PrRangeList.parseSigned(p.get("heights"));
        if (b.heights == null && (p.has("minHeight") || p.has("maxHeight"))) {
            b.heights = PrRangeList.of(p.getIntSigned("minHeight", Integer.MIN_VALUE),
                    p.getIntSigned("maxHeight", Integer.MAX_VALUE));
        }
        b.name = PrTextMatcher.parse(p.get("name"));
        if (p.getInt("renderPass", 0) > 0) {
            w.accept("renderPass is not supported");
            return Optional.empty();
        }
        b.innerSeams = p.getBool("innerSeams", false);
        b.ctmTileIndexes = parseCtmIndexes(p, b.tiles.size(), w);
        b.width = p.getInt("width", -1);
        b.height = p.getInt("height", -1);
        b.randomLoops = p.getInt("randomLoops", 0);
        String symmetry = p.get("symmetry");
        if (symmetry != null) {
            b.symmetry = switch (symmetry.trim()) {
                case "opposite" -> 2;
                case "all" -> 6;
                default -> -1;
            };
            if (b.symmetry < 0) {
                w.accept("Unknown symmetry '" + symmetry + "'");
                return Optional.empty();
            }
        }
        b.linked = p.getBool("linked", false);
        String connectBlocks = p.get("connectBlocks");
        b.connectBlocks = connectBlocks == null ? null : PrBlockMatcher.parse(connectBlocks, null, w);
        b.connectTiles = parseSpriteList(p.get("connectTiles"), p);
        b.tintIndex = p.getInt("tintIndex", -1);
        String tintBlock = p.get("tintBlock");
        if (tintBlock != null) {
            // OptiFine: one block, its default state; block properties in the name are ignored.
            PrBlockMatcher tint = PrBlockMatcher.parse(tintBlock, null, w);
            if (tint.blocks().size() == 1) {
                b.tintBlock = tint.blocks().iterator().next().defaultBlockState();
            } else {
                w.accept("Unknown tintBlock '" + tintBlock + "'");
            }
        }
        String layer = p.get("layer");
        if (layer != null) {
            Layer type = parseLayer(layer);
            if (type == null) {
                w.accept("Unknown layer '" + layer + "'");
            } else {
                b.layer = type;
            }
        }
        if (!validate(b, p, w)) {
            return Optional.empty();
        }
        return Optional.of(new PrCtmRule(b));
    }

    private static boolean validate(Builder b, PrProperties p, Consumer<String> w) {
        int n = b.tiles.size();
        PrCtmMethod m = b.method;
        if (m.exact ? n != m.tileCount : n < m.tileCount) {
            w.accept("Method " + m + " needs " + (m.exact ? "exactly " : "at least ") + m.tileCount + " tiles, found " + n);
            return false;
        }
        if (m == PrCtmMethod.REPEAT || m == PrCtmMethod.OVERLAY_REPEAT) {
            if (b.width <= 0 || b.height <= 0 || n != b.width * b.height) {
                w.accept("Repeat needs width x height tiles");
                return false;
            }
        }
        if (m == PrCtmMethod.RANDOM || m == PrCtmMethod.OVERLAY_RANDOM) {
            if (b.randomLoops < 0 || b.randomLoops > 9) {
                w.accept("Invalid randomLoops " + b.randomLoops);
                return false;
            }
            int[] weights = parseIntList(p.get("weights"));
            if (weights != null) {
                int[] fixed = new int[n];
                int sum = 0;
                for (int i = 0; i < n; i++) {
                    fixed[i] = i < weights.length ? weights[i] : average(weights);
                    sum += fixed[i];
                    fixed[i] = sum;
                }
                b.sumWeights = fixed;
                b.sumAllWeights = sum > 0 ? sum : 1;
            }
        }
        if (m.isOverlay() && b.layer == Layer.SOLID) {
            w.accept("Overlays cannot use the solid layer");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * OptiFine tile list: numbers, number ranges and names, resolved against the file's folder. A
     * token splits on {@code -} like a StringTokenizer (empty parts dropped); two non-negative
     * numbers make a range, a reversed range is skipped with a warning, anything else is a name.
     */
    static List<Tile> parseTiles(String text, PrProperties p, Consumer<String> warn) {
        List<Tile> tiles = new ArrayList<>();
        for (String token : text.trim().split("[ ,]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.indexOf('-') >= 0) {
                String[] parts = dashParts(token);
                if (parts.length == 2) {
                    int lo = PrProperties.parseInt(parts[0], -1);
                    int hi = PrProperties.parseInt(parts[1], -1);
                    if (lo >= 0 && hi >= 0) {
                        if (lo > hi) {
                            warn.accept("Invalid interval '" + token + "' in " + p);
                            continue;
                        }
                        for (int i = lo; i <= hi; i++) {
                            tiles.add(tile(Integer.toString(i), p));
                        }
                        continue;
                    }
                }
            }
            Tile t = tile(token, p);
            if (t == null) {
                return null;
            }
            tiles.add(t);
        }
        return tiles.isEmpty() ? null : tiles;
    }

    static Tile tile(String token, PrProperties p) {
        String name = PrPaths.stripExtension(token, ".png");
        if (name.endsWith("<skip>")) {
            return Tile.SKIP;
        }
        if (name.endsWith("<default>")) {
            return Tile.DEFAULT;
        }
        String ns = p.location().getNamespace();
        String path;
        int colon = name.indexOf(':');
        if (colon >= 0) {
            ns = name.substring(0, colon);
            path = name.substring(colon + 1);
            if (!path.startsWith("textures/") && !path.startsWith(PrPaths.OPTIFINE) && !path.startsWith(PrPaths.MCPATCHER)) {
                path = "textures/" + path;
            }
        } else {
            String base = p.basePath();
            path = PrPaths.resolve(name, base);
            if (!path.startsWith(base) && !path.startsWith("textures/") && !path.startsWith(PrPaths.OPTIFINE)
                    && !path.startsWith(PrPaths.MCPATCHER)) {
                path = base.isEmpty() ? path : base + "/" + path;
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (!path.contains("/")) {
                path = "textures/block/" + path;
            }
        }
        ResourceLocation file = ResourceLocation.tryBuild(ns, path + ".png");
        if (file == null) {
            return null;
        }
        file = PrPaths.logical(file);
        String filePath = file.getPath();
        ResourceLocation sprite = filePath.startsWith("textures/")
                ? file.withPath(filePath.substring("textures/".length(), filePath.length() - 4))
                : file.withPath(filePath.substring(0, filePath.length() - 4));
        return new Tile(sprite, file, false, false);
    }

    /** {@code matchTiles} / {@code connectTiles}: atlas sprite names, bare names meaning {@code block/<name>}. */
    static List<ResourceLocation> parseSpriteList(String text, PrProperties p) {
        if (text == null) {
            return null;
        }
        List<ResourceLocation> out = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                ResourceLocation id = spriteId(token, p);
                if (id != null) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    static ResourceLocation spriteId(String token, PrProperties p) {
        String name = PrPaths.resolve(PrPaths.stripExtension(token, ".png"), p.basePath());
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        if (!path.contains("/")) {
            path = "block/" + path;
        } else if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        return PrPaths.logical(id.withPath(path));
    }

    /** Face mask; -1 when a face name is unknown. Bit n is Direction.get3DDataValue() == n. */
    static int parseFaces(String text) {
        int mask = 0;
        for (String token : text.trim().toLowerCase(Locale.ROOT).split("[ ,]+")) {
            switch (token) {
                case "bottom", "down" -> mask |= 1;
                case "top", "up" -> mask |= 2;
                case "north" -> mask |= 4;
                case "south" -> mask |= 8;
                case "west" -> mask |= 16;
                case "east" -> mask |= 32;
                case "sides" -> mask |= 60;
                case "all" -> mask |= 63;
                case "" -> {
                }
                default -> {
                    return -1;
                }
            }
        }
        return mask;
    }

    private static int[] parseCtmIndexes(PrProperties p, int tileCount, Consumer<String> w) {
        Map<String, String> entries = p.withPrefix("ctm.");
        if (entries.isEmpty()) {
            return null;
        }
        int[] indexes = new int[47];
        Arrays.fill(indexes, -1);
        for (Map.Entry<String, String> e : entries.entrySet()) {
            int ctm = PrProperties.parseInt(e.getKey(), -1);
            int tile = PrProperties.parseInt(e.getValue(), -1);
            if (ctm < 0 || ctm > 46 || tile < 0 || tile >= tileCount) {
                w.accept("Invalid ctm." + e.getKey() + "=" + e.getValue());
                continue;
            }
            indexes[ctm] = tile;
        }
        return indexes;
    }

    /** OptiFine matches the chunk layer names; an unknown name keeps the default. */
    private static Layer parseLayer(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "solid" -> Layer.SOLID;
            case "cutout_mipped" -> Layer.CUTOUT_MIPPED;
            case "cutout" -> Layer.CUTOUT;
            case "translucent" -> Layer.TRANSLUCENT;
            case "tripwire" -> Layer.TRIPWIRE;
            default -> null;
        };
    }

    /** OptiFine ConnectedParser.parseIntList: non-negative numbers and ascending ranges; the rest is skipped. */
    static int[] parseIntList(String text) {
        if (text == null) {
            return null;
        }
        List<Integer> values = new ArrayList<>();
        for (String token : text.trim().split("[ ,]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.indexOf('-') >= 0) {
                String[] parts = dashParts(token);
                int lo = parts.length == 2 ? PrProperties.parseInt(parts[0], -1) : -1;
                int hi = parts.length == 2 ? PrProperties.parseInt(parts[1], -1) : -1;
                if (lo >= 0 && hi >= 0 && lo <= hi) {
                    for (int i = lo; i <= hi; i++) {
                        values.add(i);
                    }
                }
            } else {
                int v = PrProperties.parseInt(token, -1);
                if (v >= 0) {
                    values.add(v);
                }
            }
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Splits on {@code -} dropping empty parts, as OptiFine's StringTokenizer-based tokenize does. */
    private static String[] dashParts(String token) {
        return Arrays.stream(token.split("-")).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    private static int average(int[] values) {
        if (values.length == 0) {
            return 0;
        }
        long sum = 0;
        for (int v : values) {
            sum += v;
        }
        return (int) (sum / values.length);
    }
}
