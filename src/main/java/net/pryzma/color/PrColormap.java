package net.pryzma.color;

import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.pryzma.core.PrHash;
import net.pryzma.core.match.PrBlockMatcher;
import net.pryzma.core.res.PrImage;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrProperties;
import net.pryzma.core.res.PrResources;

/**
 * One OptiFine colormap.
 *
 * <ul>
 *   <li>{@code vanilla}: x = 1 - temperature, y = 1 - temperature * downfall (the grass.png scheme)</li>
 *   <li>{@code grid}: x = OptiFine biome id, y = block height - {@code yOffset}, optionally jittered
 *       per column by {@code yVariance}</li>
 *   <li>{@code fixed}: a single {@code color}</li>
 * </ul>
 *
 * Instances are immutable and shared across render and chunk-build threads.
 */
public final class PrColormap {
    public enum Format {
        VANILLA, GRID, FIXED;

        static Format parse(String s, Format def) {
            if (s == null) {
                return def;
            }
            return switch (s.trim()) {
                case "vanilla" -> VANILLA;
                case "grid" -> GRID;
                case "fixed" -> FIXED;
                default -> null;
            };
        }
    }

    private final String name;
    private final Format format;
    private final PrImage image;
    private final int color;
    private final int yVariance;
    private final int yOffset;
    private final PrBlockMatcher blocks;

    PrColormap(String name, Format format, PrImage image, int color, int yVariance, int yOffset, PrBlockMatcher blocks) {
        this.name = name;
        this.format = format;
        this.image = image;
        this.yVariance = yVariance;
        this.yOffset = yOffset;
        this.blocks = blocks;
        this.color = color >= 0 ? color : defaultColor(format, image, yOffset);
    }

    private static int defaultColor(Format format, PrImage image, int yOffset) {
        return switch (format) {
            case FIXED -> 0xFFFFFF;
            case VANILLA -> image.getClamped(127, 127) & 0xFFFFFF;
            case GRID -> image.getClamped(1, 64 - yOffset) & 0xFFFFFF;
        };
    }

    /**
     * Loads a colormap image, reading its sibling {@code .properties} (same name) when present.
     *
     * @param defaultFormat {@code palette.format} of color.properties
     */
    static Optional<PrColormap> load(PrResources res, ResourceLocation png, Format defaultFormat, Consumer<String> warn) {
        if (!res.exists(png)) {
            return Optional.empty();
        }
        ResourceLocation propsLoc = png.withPath(PrPaths.stripExtension(png.getPath(), ".png") + ".properties");
        Optional<PrProperties> props = res.properties(propsLoc);
        if (props.isPresent()) {
            return fromProperties(res, props.get(), defaultFormat, false, warn);
        }
        return res.image(png).map(img -> new PrColormap(png.toString(), defaultFormat == Format.FIXED ? Format.VANILLA : defaultFormat,
                img, -1, 0, 0, PrBlockMatcher.empty()));
    }

    /**
     * Builds a colormap from a properties file ({@code colormap/custom/*.properties} or a sibling
     * of a colormap image).
     *
     * @param blockColormap {@code true} for block palettes, whose {@code blocks} default to the file name
     */
    static Optional<PrColormap> fromProperties(PrResources res, PrProperties p, Format defaultFormat, boolean blockColormap,
            Consumer<String> warn) {
        Format format = Format.parse(p.get("format"), defaultFormat);
        if (format == null) {
            warn.accept("Unknown colormap format '" + p.get("format") + "' in " + p);
            return Optional.empty();
        }
        PrBlockMatcher blocks = PrBlockMatcher.empty();
        if (blockColormap) {
            String blockList = p.get("blocks");
            blocks = PrBlockMatcher.parse(blockList != null ? blockList : p.name(), p.get("metadata"),
                    msg -> warn.accept(msg + " in " + p));
            if (blocks.isEmpty()) {
                warn.accept("No blocks for colormap " + p);
                return Optional.empty();
            }
        }
        int color = p.getColor("color", -1);
        int yVariance = p.getInt("yVariance", 0);
        int yOffset = p.getIntSigned("yOffset", 0);
        if (format == Format.FIXED) {
            return Optional.of(new PrColormap(p.toString(), format, null, color, 0, 0, blocks));
        }
        String source = p.get("source");
        String sourcePath = source != null ? PrPaths.withExtension(source, ".png") : "./" + p.name() + ".png";
        ResourceLocation image = PrPaths.resolveSibling(sourcePath, p.location());
        if (image == null) {
            warn.accept("Invalid colormap source '" + source + "' in " + p);
            return Optional.empty();
        }
        Optional<PrImage> img = res.image(image);
        if (img.isEmpty() || img.get().width() <= 0 || img.get().height() <= 0) {
            warn.accept("Colormap image not found: " + image + " for " + p);
            return Optional.empty();
        }
        return Optional.of(new PrColormap(p.toString(), format, img.get(), color, yVariance, yOffset, blocks));
    }

    /** The same colormap applied to {@code matcher} ({@code palette.block.*} entries). */
    PrColormap withBlocks(PrBlockMatcher matcher) {
        return new PrColormap(name, format, image, color, yVariance, yOffset, matcher);
    }

    public String name() {
        return name;
    }

    public Format format() {
        return format;
    }

    public PrBlockMatcher blocks() {
        return blocks;
    }

    public boolean isConstant() {
        return format == Format.FIXED;
    }

    public int width() {
        return image == null ? 1 : image.width();
    }

    public int height() {
        return image == null ? 1 : image.height();
    }

    /** Pixel by linear index (1-D strips such as redstone.png, stem.png, xporb.png). */
    public int byIndex(int index) {
        if (image == null) {
            return color;
        }
        int[] px = image.argb();
        return px[Math.clamp(index, 0, px.length - 1)] & 0xFFFFFF;
    }

    public int length() {
        return image == null ? 1 : image.argb().length;
    }

    /** Colour for one column/height, without smoothing. */
    public int colorAt(Biome biome, int x, int y, int z) {
        return switch (format) {
            case FIXED -> color;
            case VANILLA -> vanilla(biome);
            case GRID -> grid(biome, x, y, z);
        };
    }

    private int vanilla(Biome biome) {
        double temperature = Math.clamp(PrBiomes.temperature(biome), 0.0F, 1.0F);
        double rainfall = Math.clamp(PrBiomes.downfall(biome), 0.0F, 1.0F) * temperature;
        int cx = (int) ((1.0 - temperature) * (image.width() - 1));
        int cy = (int) ((1.0 - rainfall) * (image.height() - 1));
        return image.getClamped(cx, cy) & 0xFFFFFF;
    }

    private int grid(Biome biome, int x, int y, int z) {
        int cy = y - yOffset;
        if (yVariance > 0) {
            // OptiFine writes "x << 16 + z", which Java parses as x << (16 + z); packs are tuned to it.
            int seed = x << 16 + z;
            int range = yVariance * 2 + 1;
            cy += (PrHash.intHash(seed) & 0xFF) % range - yVariance;
        }
        return image.getClamped(PrBiomes.gridId(biome), cy) & 0xFFFFFF;
    }

    /** Average over a square of columns at {@code pos}'s height, as OptiFine's smooth sampling does. */
    public int smooth(BlockAndTintGetter level, BlockPos pos, int radius) {
        if (format == Format.FIXED) {
            return color;
        }
        if (radius <= 0) {
            return colorAt(PrBiomes.colorBiome(PrBiomes.biomeAt(level, pos)), pos.getX(), pos.getY(), pos.getZ());
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = 0;
        int g = 0;
        int b = 0;
        int y = pos.getY();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = pos.getX() + dx;
                int z = pos.getZ() + dz;
                p.set(x, y, z);
                int c = colorAt(PrBiomes.colorBiome(PrBiomes.biomeAt(level, p)), x, y, z);
                r += c >> 16 & 0xFF;
                g += c >> 8 & 0xFF;
                b += c & 0xFF;
            }
        }
        int n = (radius * 2 + 1) * (radius * 2 + 1);
        return (r / n) << 16 | (g / n) << 8 | (b / n);
    }

    @Override
    public String toString() {
        return name + " (" + format + ")";
    }
}
