package net.pryzma.ctm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.Nameable;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;
import net.pryzma.color.PrBiomes;
import net.pryzma.mixin.WeightedBakedModelAccessor;

/**
 * Connected textures engine. Rules come from {@link PrCtmSpriteSource} while the block atlas is
 * stitched; once models are baked they are bound to sprites, indexed, and every block model they
 * can affect is wrapped in a {@link PrCtmModel}. Evaluation follows OptiFine's ConnectedTextures
 * step for step so existing packs connect exactly as they were drawn.
 */
public final class PryzmaCtm {
    private static final BakedQuad[] NONE = new BakedQuad[0];
    /** Identity of a {@code <default>} tile in tile comparisons. */
    private static final Object KEEP = new Object();
    private static final int PASSES = 4;
    private static final Direction[] SIDES_AND_NULL = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};

    private static volatile PrCtmLoader.Result pending = PrCtmLoader.Result.EMPTY;
    private static volatile Index index = Index.EMPTY;
    private static final ThreadLocal<BlockPos.MutableBlockPos> SCRATCH = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static final ThreadLocal<RandomSource> EVAL_RANDOM = ThreadLocal.withInitial(RandomSource::create);

    private PryzmaCtm() {
    }

    /**
     * Everything derived from one model bake. The caches live here, not in statics: a chunk build
     * still running on the old models while resources reload can then only fill the old index.
     */
    private record Index(boolean active, Map<Block, PrCtmRule[]> byBlock, Map<TextureAtlasSprite, PrCtmRule[]> bySprite,
            boolean multipass, int ruleCount, Map<ModelResourceLocation, BakedModel> models,
            Map<BlockState, TextureAtlasSprite[]> faceSprites, Map<BlockState, ChunkRenderTypeSet> overlayLayers) {
        static final Index EMPTY = new Index(false, Map.of(), Map.of(), false, 0, Map.of(), Map.of(), Map.of());
    }

    static void setPending(PrCtmLoader.Result result) {
        pending = result;
    }

    /** True while the baked models carry CTM: the option is on and the last bake ran with it on. */
    public static boolean enabled() {
        return PryzmaConfig.isConnectedTextures() && index.active;
    }

    // ------------------------------------------------------------------ binding and wrapping

    /**
     * Binds the pending rules to the new atlas and wraps affected block models: blocks named by a
     * rule, blocks whose quads show a matched sprite, and every pane and bar (OptiFine hides their
     * inner edges whenever connected textures are on). Model bake worker.
     */
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        long start = System.nanoTime();
        PrCtmLoader.Result result = pending;
        pending = PrCtmLoader.Result.EMPTY;
        PrQuads.clearCaches();
        PrCtmCompact.clearCache();
        if (!PryzmaConfig.isConnectedTextures()) {
            index = Index.EMPTY;
            return;
        }
        Function<Material, TextureAtlasSprite> atlas = event.getTextureGetter();
        List<PrCtmRule> bound = new ArrayList<>();
        for (PrCtmRule rule : result.rules()) {
            if (bind(rule, atlas)) {
                bound.add(rule);
            }
        }
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        Index built = buildIndex(bound, models);
        Set<TextureAtlasSprite> spriteKeys = built.bySprite.keySet();
        int wrapped = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            boolean byBlock = built.byBlock.containsKey(block) || block instanceof IronBarsBlock;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation mrl = BlockModelShaper.stateToModelLocation(blockId, state);
                BakedModel model = models.get(mrl);
                if (model == null || model instanceof PrCtmModel || model.isCustomRenderer()) {
                    continue;
                }
                if (byBlock || anyQuad(model, state, q -> spriteKeys.contains(q.getSprite()))) {
                    built.overlayLayers.put(state, computeOverlayLayers(built, state, model));
                    models.put(mrl, new PrCtmModel(model));
                    wrapped++;
                }
            }
        }
        index = built;
        Pryzma.LOGGER.info("ConnectedTextures: {} rules bound, {} block models wrapped, multipass={} in {} ms",
                built.ruleCount, wrapped, built.multipass, (System.nanoTime() - start) / 1_000_000);
    }

    private static boolean bind(PrCtmRule rule, Function<Material, TextureAtlasSprite> atlas) {
        int n = rule.tiles.size();
        TextureAtlasSprite[] tiles = new TextureAtlasSprite[n];
        boolean[] keep = new boolean[n];
        for (int i = 0; i < n; i++) {
            PrCtmRule.Tile tile = rule.tiles.get(i);
            if (tile.keep()) {
                keep[i] = true;
            } else if (!tile.skip()) {
                tiles[i] = sprite(atlas, tile.sprite());
                if (tiles[i] == null) {
                    Pryzma.LOGGER.warn("ConnectedTextures: missing tile sprite {} in {}", tile.sprite(), rule.source);
                    return false;
                }
            }
        }
        rule.tileSprites = tiles;
        rule.tileKeep = keep;
        rule.matchSprites = sprites(atlas, rule.matchTiles);
        rule.connectSprites = sprites(atlas, rule.connectTiles);
        // matchTiles naming only sprites that do not exist can never match a quad.
        return rule.matchSprites == null || rule.matchSprites.length > 0;
    }

    private static TextureAtlasSprite sprite(Function<Material, TextureAtlasSprite> atlas, ResourceLocation id) {
        TextureAtlasSprite sprite = atlas.apply(new Material(InventoryMenu.BLOCK_ATLAS, id));
        return sprite == null || sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : sprite;
    }

    private static TextureAtlasSprite[] sprites(Function<Material, TextureAtlasSprite> atlas, List<ResourceLocation> ids) {
        if (ids == null) {
            return null;
        }
        List<TextureAtlasSprite> out = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            TextureAtlasSprite s = sprite(atlas, id);
            if (s != null) {
                out.add(s);
            }
        }
        return out.toArray(TextureAtlasSprite[]::new);
    }

    private static Index buildIndex(List<PrCtmRule> rules, Map<ModelResourceLocation, BakedModel> models) {
        Map<Block, List<PrCtmRule>> byBlock = new IdentityHashMap<>();
        Map<TextureAtlasSprite, List<PrCtmRule>> bySprite = new IdentityHashMap<>();
        Set<TextureAtlasSprite> matched = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<TextureAtlasSprite> produced = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PrCtmRule rule : rules) {
            if (rule.matchSprites != null) {
                for (TextureAtlasSprite s : rule.matchSprites) {
                    bySprite.computeIfAbsent(s, k -> new ArrayList<>()).add(rule);
                    matched.add(s);
                }
            }
            if (rule.matchBlocks != null) {
                for (Block b : rule.matchBlocks.blocks()) {
                    byBlock.computeIfAbsent(b, k -> new ArrayList<>()).add(rule);
                }
            }
            for (TextureAtlasSprite t : rule.tileSprites) {
                if (t != null) {
                    produced.add(t);
                }
            }
        }
        matched.retainAll(produced);
        Map<Block, PrCtmRule[]> blocks = new IdentityHashMap<>();
        byBlock.forEach((k, v) -> blocks.put(k, v.toArray(PrCtmRule[]::new)));
        Map<TextureAtlasSprite, PrCtmRule[]> sprites = new IdentityHashMap<>();
        bySprite.forEach((k, v) -> sprites.put(k, v.toArray(PrCtmRule[]::new)));
        return new Index(true, blocks, sprites, !matched.isEmpty(), rules.size(), models,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    /**
     * Tests the quads a model can show for {@code state} on every side, through every variant of
     * a weighted model (a random variant may carry a sprite the first one does not).
     */
    private static boolean anyQuad(BakedModel model, BlockState state, Predicate<BakedQuad> test) {
        if (model instanceof WeightedBakedModel weighted) {
            for (WeightedEntry.Wrapper<BakedModel> variant : ((WeightedBakedModelAccessor) weighted).prGetList()) {
                if (anyQuad(variant.data(), state, test)) {
                    return true;
                }
            }
            return false;
        }
        RandomSource random = RandomSource.create();
        for (Direction side : SIDES_AND_NULL) {
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(state, side, random, ModelData.EMPTY, null)) {
                if (test.test(quad)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Overlay layers any rule may add for {@code state}; the model adds them to its render types. */
    static ChunkRenderTypeSet overlayLayers(BlockState state) {
        ChunkRenderTypeSet layers = index.overlayLayers.get(state);
        return layers != null ? layers : ChunkRenderTypeSet.none();
    }

    private static ChunkRenderTypeSet computeOverlayLayers(Index idx, BlockState state, BakedModel model) {
        Set<RenderType> layers = new HashSet<>();
        PrCtmRule[] blockRules = idx.byBlock.get(state.getBlock());
        if (blockRules != null) {
            for (PrCtmRule r : blockRules) {
                if (r.method.isOverlay()) {
                    layers.add(r.layer.type());
                }
            }
        }
        anyQuad(model, state, q -> {
            PrCtmRule[] rules = idx.bySprite.get(q.getSprite());
            if (rules != null) {
                for (PrCtmRule r : rules) {
                    if (r.method.isOverlay()) {
                        layers.add(r.layer.type());
                    }
                }
            }
            return false;
        });
        return layers.isEmpty() ? ChunkRenderTypeSet.none() : ChunkRenderTypeSet.of(layers);
    }

    /** Whether the chunk renderer will draw this side at all; culled faces need no evaluation. */
    static boolean faceVisible(PrCtmContext ctx, Direction side) {
        return Block.shouldRenderFace(ctx.state, ctx.level, ctx.pos, side, SCRATCH.get().setWithOffset(ctx.pos, side));
    }

    /**
     * A random source in the state the chunk renderer gives the model for this block, so the
     * evaluation fetch picks the same weighted variant as the render fetch without consuming the
     * caller's source.
     */
    static RandomSource modelRandom(PrCtmContext ctx) {
        RandomSource random = EVAL_RANDOM.get();
        // Alternate Blocks off renders every block with seed 0; the variant must match.
        random.setSeed(PryzmaConfig.prAlternateBlocks ? ctx.state.getSeed(ctx.pos) : 0L);
        return random;
    }

    // ------------------------------------------------------------------ evaluation

    /**
     * The quads that replace {@code quad}: {@code null} when no rule applies (keep it), an empty
     * array to drop it. Overlays are recorded into {@code ctx} under {@code sideSlot}.
     */
    static BakedQuad[] evaluate(PrCtmContext ctx, BakedQuad quad, int sideSlot) {
        if (skipPaneEdge(ctx, quad)) {
            return NONE;
        }
        Index idx = index;
        BakedQuad[] result = single(idx, ctx, quad, true, 0, sideSlot);
        if (!idx.multipass || result == null) {
            return result;
        }
        for (int i = 0; i < result.length; i++) {
            BakedQuad q = result[i];
            for (int pass = 1; pass < PASSES; pass++) {
                BakedQuad[] next = single(idx, ctx, q, false, pass, sideSlot);
                if (next == null || next.length != 1 || next[0] == q) {
                    break;
                }
                q = next[0];
            }
            result[i] = q;
        }
        return result;
    }

    private static BakedQuad[] single(Index idx, PrCtmContext ctx, BakedQuad quad, boolean checkBlocks, int pass, int sideSlot) {
        TextureAtlasSprite sprite = quad.getSprite();
        PrCtmRule[] rules = idx.bySprite.get(sprite);
        if (rules != null) {
            for (PrCtmRule r : rules) {
                if (r.matchBlocks == null || r.matchBlocks.matches(ctx.state)) {
                    BakedQuad[] out = apply(r, ctx, quad, pass, sideSlot);
                    if (out != null) {
                        return out;
                    }
                }
            }
        }
        if (checkBlocks) {
            rules = idx.byBlock.get(ctx.state.getBlock());
            if (rules != null) {
                for (PrCtmRule r : rules) {
                    if (matchesSprite(r, sprite) && r.matchBlocks.matches(ctx.state)) {
                        BakedQuad[] out = apply(r, ctx, quad, pass, sideSlot);
                        if (out != null) {
                            return out;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean matchesSprite(PrCtmRule r, TextureAtlasSprite sprite) {
        return r.matchSprites == null || contains(r.matchSprites, sprite);
    }

    private static BakedQuad[] apply(PrCtmRule r, PrCtmContext ctx, BakedQuad quad, int pass, int sideSlot) {
        int side = quad.getDirection().get3DDataValue();
        int axis = pillarAxis(ctx.state);
        if (r.faces != 63 && !r.hasFace(axis != 0 ? PrCtmLogic.fixSideByAxis(side, axis) : side)) {
            return null;
        }
        if (r.heights != null && !r.heights.contains(ctx.pos.getY())) {
            return null;
        }
        if (r.biomes != null) {
            Holder<Biome> biome = PrBiomes.biomeHolderAt(ctx.level, ctx.pos);
            if (biome == null || !r.biomes.matches(biome)) {
                return null;
            }
        }
        if (r.name != null && !r.name.matches(blockEntityName(ctx))) {
            return null;
        }
        TextureAtlasSprite icon = quad.getSprite();
        return switch (r.method) {
            case CTM -> tileAt(r, ctmIndex(r, ctx, side, icon), quad);
            case HORIZONTAL -> tileAt(r, horizontalIndex(r, ctx, side, axis, icon), quad);
            case VERTICAL -> tileAt(r, verticalIndex(r, ctx, side, axis, icon), quad);
            case HORIZONTAL_VERTICAL -> tileAt(r, horizontalVertical(r, ctx, side, axis, icon), quad);
            case VERTICAL_HORIZONTAL -> tileAt(r, verticalHorizontal(r, ctx, side, axis, icon), quad);
            case TOP -> top(r, ctx, side, axis, icon) ? tileAt(r, 0, quad) : null;
            case RANDOM -> tileAt(r, randomIndex(r, ctx, side), quad);
            case REPEAT -> tileAt(r, repeatIndex(r, ctx, side), quad);
            case FIXED -> tileAt(r, 0, quad);
            case CTM_COMPACT -> pass == 0 ? PrCtmCompact.quads(ctmIndex(r, ctx, side, icon), r, side, quad) : null;
            case OVERLAY -> overlay(r, ctx, side, icon, quad, sideSlot);
            case OVERLAY_CTM -> overlayTile(r, ctx, quad, sideSlot, ctmIndex(r, ctx, side, icon));
            case OVERLAY_RANDOM -> overlayTile(r, ctx, quad, sideSlot, randomIndex(r, ctx, side));
            case OVERLAY_REPEAT -> overlayTile(r, ctx, quad, sideSlot, repeatIndex(r, ctx, side));
            case OVERLAY_FIXED -> overlayTile(r, ctx, quad, sideSlot, 0);
        };
    }

    /** OptiFine getQuads: {@code <default>} keeps the quad, {@code <skip>} passes to the next rule. */
    static BakedQuad[] tileAt(PrCtmRule r, int index, BakedQuad quad) {
        if (r.tileKeep[index]) {
            return new BakedQuad[] {quad};
        }
        TextureAtlasSprite sprite = r.tileSprites[index];
        return sprite == null ? null : new BakedQuad[] {PrQuads.remap(quad, sprite)};
    }

    // ------------------------------------------------------------------ neighbours

    private static boolean isNeighbour(PrCtmRule r, PrCtmContext ctx, BlockPos pos, int side, TextureAtlasSprite icon) {
        return isNeighbour(r, ctx, ctx.level.getBlockState(pos), side, icon);
    }

    private static boolean isNeighbour(PrCtmRule r, PrCtmContext ctx, BlockState neighbour, int side, TextureAtlasSprite icon) {
        if (neighbour == ctx.state) {
            return true;
        }
        return switch (r.connect) {
            case PrCtmRule.CONNECT_TILE -> !neighbour.isAir() && faceSprite(neighbour, side) == icon;
            case PrCtmRule.CONNECT_BLOCK -> neighbour.getBlock() == ctx.state.getBlock();
            default -> false;
        };
    }

    private static BlockPos offset(BlockPos.MutableBlockPos m, BlockPos o, Direction a) {
        return m.setWithOffset(o, a);
    }

    private static BlockPos offset(BlockPos.MutableBlockPos m, BlockPos o, Direction a, Direction b) {
        return m.set(o.getX() + a.getStepX() + b.getStepX(), o.getY() + a.getStepY() + b.getStepY(),
                o.getZ() + a.getStepZ() + b.getStepZ());
    }

    private static BlockPos offset(BlockPos.MutableBlockPos m, BlockPos o, Direction a, Direction b, Direction c) {
        return m.set(o.getX() + a.getStepX() + b.getStepX() + c.getStepX(),
                o.getY() + a.getStepY() + b.getStepY() + c.getStepY(),
                o.getZ() + a.getStepZ() + b.getStepZ() + c.getStepZ());
    }

    /** OptiFine getConnectedTextureCtmIndex, including inner seams and the Fancy corner pass. */
    static int ctmIndex(PrCtmRule r, PrCtmContext ctx, int side, TextureAtlasSprite icon) {
        BlockPos.MutableBlockPos m = SCRATCH.get();
        Direction[] dirs = PrCtmLogic.SIDES[side];
        Direction front = Direction.from3DDataValue(side);
        boolean[] b = new boolean[4];
        for (int i = 0; i < 4; i++) {
            b[i] = isNeighbour(r, ctx, offset(m, ctx.pos, dirs[i]), side, icon);
            if (r.innerSeams && b[i]) {
                b[i] = !isNeighbour(r, ctx, offset(m, ctx.pos, front, dirs[i]), side, icon);
            }
        }
        int idx = PrCtmLogic.baseIndex(b[0], b[1], b[2], b[3]);
        if (idx == 0 || !PryzmaConfig.isConnectedTexturesFancy()) {
            return idx;
        }
        Direction left = dirs[0];
        Direction right = dirs[1];
        Direction bottom = dirs[2];
        Direction top = dirs[3];
        return PrCtmLogic.refine(idx,
                cornerGap(r, ctx, m, front, right, bottom, side, icon),
                cornerGap(r, ctx, m, front, left, bottom, side, icon),
                cornerGap(r, ctx, m, front, right, top, side, icon),
                cornerGap(r, ctx, m, front, left, top, side, icon));
    }

    private static boolean cornerGap(PrCtmRule r, PrCtmContext ctx, BlockPos.MutableBlockPos m, Direction front,
            Direction a, Direction b, int side, TextureAtlasSprite icon) {
        boolean gap = !isNeighbour(r, ctx, offset(m, ctx.pos, a, b), side, icon);
        if (r.innerSeams && !gap) {
            gap = isNeighbour(r, ctx, offset(m, ctx.pos, front, a, b), side, icon);
        }
        return gap;
    }

    private static int horizontalIndex(PrCtmRule r, PrCtmContext ctx, int side, int axis, TextureAtlasSprite icon) {
        BlockPos.MutableBlockPos m = SCRATCH.get();
        Direction[] lr = PrCtmLogic.HORIZONTAL[axis][side];
        boolean left = isNeighbour(r, ctx, offset(m, ctx.pos, lr[0]), side, icon);
        boolean right = isNeighbour(r, ctx, offset(m, ctx.pos, lr[1]), side, icon);
        return PrCtmLogic.pairIndex(left, right);
    }

    private static int verticalIndex(PrCtmRule r, PrCtmContext ctx, int side, int axis, TextureAtlasSprite icon) {
        BlockPos.MutableBlockPos m = SCRATCH.get();
        Direction[] bt = PrCtmLogic.VERTICAL[axis][side];
        boolean bottom = isNeighbour(r, ctx, offset(m, ctx.pos, bt[0]), side, icon);
        boolean top = isNeighbour(r, ctx, offset(m, ctx.pos, bt[1]), side, icon);
        return PrCtmLogic.pairIndex(bottom, top);
    }

    /**
     * Tile identity as OptiFine compares tiles in h+v and v+h: the sprite, {@code null} for
     * {@code <skip>}, and a sprite of its own for {@code <default>}.
     */
    private static Object tileId(PrCtmRule r, int i) {
        return r.tileKeep[i] ? KEEP : r.tileSprites[i];
    }

    /** OptiFine h+v: the horizontal tile unless it is the lone tile, else the vertical one shifted to tiles 4-6. */
    private static int horizontalVertical(PrCtmRule r, PrCtmContext ctx, int side, int axis, TextureAtlasSprite icon) {
        int h = horizontalIndex(r, ctx, side, axis, icon);
        Object th = tileId(r, h);
        if (th != null && th != icon && th != tileId(r, 3)) {
            return h;
        }
        int v = verticalIndex(r, ctx, side, axis, icon);
        return shiftPair(r, v);
    }

    private static int verticalHorizontal(PrCtmRule r, PrCtmContext ctx, int side, int axis, TextureAtlasSprite icon) {
        int v = verticalIndex(r, ctx, side, axis, icon);
        Object tv = tileId(r, v);
        if (tv != null && tv != icon && tv != tileId(r, 3)) {
            return v;
        }
        int h = horizontalIndex(r, ctx, side, axis, icon);
        return shiftPair(r, h);
    }

    private static int shiftPair(PrCtmRule r, int i) {
        Object t = tileId(r, i);
        return t == tileId(r, 0) ? 4 : t == tileId(r, 1) ? 5 : t == tileId(r, 2) ? 6 : i;
    }

    private static boolean top(PrCtmRule r, PrCtmContext ctx, int side, int axis, TextureAtlasSprite icon) {
        Direction up = switch (axis) {
            case 1 -> side == 2 || side == 3 ? null : Direction.SOUTH;
            case 2 -> side == 4 || side == 5 ? null : Direction.EAST;
            default -> side == 0 || side == 1 ? null : Direction.UP;
        };
        return up != null && isNeighbour(r, ctx, offset(SCRATCH.get(), ctx.pos, up), side, icon);
    }

    private static int randomIndex(PrCtmRule r, PrCtmContext ctx, int side) {
        int count = r.tileSprites.length;
        if (count == 1) {
            return 0;
        }
        int face = side / r.symmetry * r.symmetry;
        BlockPos pos = ctx.pos;
        if (r.linked) {
            BlockPos.MutableBlockPos m = SCRATCH.get().set(pos);
            Block block = ctx.state.getBlock();
            int minY = ctx.level.getMinBuildHeight();
            while (m.getY() > minY) {
                m.move(Direction.DOWN);
                if (ctx.level.getBlockState(m).getBlock() != block) {
                    m.move(Direction.UP);
                    break;
                }
            }
            pos = m.immutable();
        }
        int rand = PrCtmLogic.random(pos.getX(), pos.getY(), pos.getZ(), face) & Integer.MAX_VALUE;
        for (int i = 0; i < r.randomLoops; i++) {
            rand = PrCtmLogic.intHash(rand);
        }
        if (r.sumWeights == null) {
            return rand % count;
        }
        int pick = rand % r.sumAllWeights;
        for (int i = 0; i < r.sumWeights.length; i++) {
            if (pick < r.sumWeights[i]) {
                return i;
            }
        }
        return 0;
    }

    private static int repeatIndex(PrCtmRule r, PrCtmContext ctx, int side) {
        if (r.tileSprites.length == 1) {
            return 0;
        }
        return PrCtmLogic.repeatIndex(ctx.pos.getX(), ctx.pos.getY(), ctx.pos.getZ(), side, r.width, r.height);
    }

    // ------------------------------------------------------------------ overlays

    private static BakedQuad[] overlayTile(PrCtmRule r, PrCtmContext ctx, BakedQuad quad, int sideSlot, int tile) {
        if (PrQuads.isFullQuad(quad)) {
            addOverlay(r, ctx, quad, sideSlot, tile);
        }
        return null;
    }

    /** Adds one overlay tile; {@code <skip>} adds nothing, and so does {@code <default>}, as in OptiFine. */
    private static void addOverlay(PrCtmRule r, PrCtmContext ctx, BakedQuad quad, int sideSlot, int tile) {
        TextureAtlasSprite sprite = r.tileSprites[tile];
        if (sprite == null) {
            return;
        }
        BakedQuad overlay = PrQuads.fullFace(quad.getDirection(), sprite, r.tintIndex);
        if (r.tintIndex >= 0) {
            int color = r.tintBlock == null || r.tintBlock.isAir() ? 0xFFFFFF
                    : Minecraft.getInstance().getBlockColors().getColor(r.tintBlock, ctx.level, ctx.pos, r.tintIndex);
            overlay = PrQuads.withColor(overlay, color == -1 ? 0xFFFFFF : color);
        }
        ctx.addOverlay(sideSlot, r.layer.type(), overlay);
    }

    /** OptiFine getConnectedTextureOverlay: 17 tiles, edges drawn where a different block borders this one. */
    private static BakedQuad[] overlay(PrCtmRule r, PrCtmContext ctx, int side, TextureAtlasSprite icon, BakedQuad quad, int sideSlot) {
        if (!PrQuads.isFullQuad(quad)) {
            return null;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Direction[] dirs = PrCtmLogic.SIDES[side];
        Direction front = Direction.from3DDataValue(side);
        boolean[] s = new boolean[4];
        for (int i = 0; i < 4; i++) {
            s[i] = overlayNeighbour(r, ctx, offset(m, ctx.pos, dirs[i]).immutable(), front, side, icon);
        }
        if (s[0] && s[1] && s[2] && s[3]) {
            addOverlay(r, ctx, quad, sideSlot, 8);
            return null;
        }
        if (s[0] && s[1] && s[2]) {
            addOverlay(r, ctx, quad, sideSlot, 5);
            return null;
        }
        if (s[0] && s[2] && s[3]) {
            addOverlay(r, ctx, quad, sideSlot, 6);
            return null;
        }
        if (s[1] && s[2] && s[3]) {
            addOverlay(r, ctx, quad, sideSlot, 12);
            return null;
        }
        if (s[0] && s[1] && s[3]) {
            addOverlay(r, ctx, quad, sideSlot, 13);
            return null;
        }
        // Corners: 0 right-bottom, 1 left-bottom, 2 right-top, 3 left-top (OptiFine's EDGES order).
        boolean[] e = {
                overlayNeighbour(r, ctx, offset(m, ctx.pos, dirs[1], dirs[2]).immutable(), front, side, icon),
                overlayNeighbour(r, ctx, offset(m, ctx.pos, dirs[0], dirs[2]).immutable(), front, side, icon),
                overlayNeighbour(r, ctx, offset(m, ctx.pos, dirs[1], dirs[3]).immutable(), front, side, icon),
                overlayNeighbour(r, ctx, offset(m, ctx.pos, dirs[0], dirs[3]).immutable(), front, side, icon),
        };
        if (s[1] && s[2]) {
            addOverlay(r, ctx, quad, sideSlot, 3);
            if (e[3]) {
                addOverlay(r, ctx, quad, sideSlot, 16);
            }
            return null;
        }
        if (s[0] && s[2]) {
            addOverlay(r, ctx, quad, sideSlot, 4);
            if (e[2]) {
                addOverlay(r, ctx, quad, sideSlot, 14);
            }
            return null;
        }
        if (s[1] && s[3]) {
            addOverlay(r, ctx, quad, sideSlot, 10);
            if (e[1]) {
                addOverlay(r, ctx, quad, sideSlot, 2);
            }
            return null;
        }
        if (s[0] && s[3]) {
            addOverlay(r, ctx, quad, sideSlot, 11);
            if (e[0]) {
                addOverlay(r, ctx, quad, sideSlot, 0);
            }
            return null;
        }
        boolean[] match = new boolean[4];
        for (int i = 0; i < 4; i++) {
            match[i] = overlayMatching(r, ctx, offset(m, ctx.pos, dirs[i]).immutable(), front, side, icon);
        }
        if (s[0]) {
            addOverlay(r, ctx, quad, sideSlot, 9);
        }
        if (s[1]) {
            addOverlay(r, ctx, quad, sideSlot, 7);
        }
        if (s[2]) {
            addOverlay(r, ctx, quad, sideSlot, 1);
        }
        if (s[3]) {
            addOverlay(r, ctx, quad, sideSlot, 15);
        }
        if (e[0] && (match[1] || match[2]) && !s[1] && !s[2]) {
            addOverlay(r, ctx, quad, sideSlot, 0);
        }
        if (e[1] && (match[0] || match[2]) && !s[0] && !s[2]) {
            addOverlay(r, ctx, quad, sideSlot, 2);
        }
        if (e[2] && (match[1] || match[3]) && !s[1] && !s[3]) {
            addOverlay(r, ctx, quad, sideSlot, 14);
        }
        if (e[3] && (match[0] || match[3]) && !s[0] && !s[3]) {
            addOverlay(r, ctx, quad, sideSlot, 16);
        }
        return null;
    }

    /** OptiFine isNeighbourOverlay: a different, full-cube block whose face towards us is exposed. */
    private static boolean overlayNeighbour(PrCtmRule r, PrCtmContext ctx, BlockPos pos, Direction front, int side,
            TextureAtlasSprite icon) {
        BlockState neighbour = ctx.level.getBlockState(pos);
        if (!isFullCube(neighbour, ctx, pos)) {
            return false;
        }
        if (r.connectBlocks != null && !r.connectBlocks.matches(neighbour)) {
            return false;
        }
        if (r.connectSprites != null && !contains(r.connectSprites, faceSprite(neighbour, side))) {
            return false;
        }
        BlockPos above = pos.relative(front);
        BlockState aboveState = ctx.level.getBlockState(above);
        if (aboveState.isSolidRender(ctx.level, above)) {
            return false;
        }
        if (side == 1 && aboveState.getBlock() == Blocks.SNOW) {
            return false;
        }
        return !isNeighbour(r, ctx, neighbour, side, icon);
    }

    /** OptiFine isNeighbourMatching: a block this rule applies to, with an exposed face. */
    private static boolean overlayMatching(PrCtmRule r, PrCtmContext ctx, BlockPos pos, Direction front, int side,
            TextureAtlasSprite icon) {
        BlockState neighbour = ctx.level.getBlockState(pos);
        if (neighbour.isAir()) {
            return false;
        }
        if (r.matchBlocks != null && !r.matchBlocks.matches(neighbour)) {
            return false;
        }
        if (r.matchSprites != null && faceSprite(neighbour, side) != icon) {
            return false;
        }
        BlockPos above = pos.relative(front);
        BlockState aboveState = ctx.level.getBlockState(above);
        if (aboveState.isSolidRender(ctx.level, above)) {
            return false;
        }
        return side != 1 || aboveState.getBlock() != Blocks.SNOW;
    }

    private static boolean isFullCube(BlockState state, PrCtmContext ctx, BlockPos pos) {
        if (state.isCollisionShapeFullBlock(ctx.level, pos)) {
            return true;
        }
        Block block = state.getBlock();
        return block == Blocks.GLASS || block instanceof StainedGlassBlock;
    }

    private static boolean contains(TextureAtlasSprite[] sprites, TextureAtlasSprite s) {
        for (TextureAtlasSprite x : sprites) {
            if (x == s) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    /** The sprite a neighbour shows on {@code side}: its first face quad, else a matching general quad. */
    static TextureAtlasSprite faceSprite(BlockState state, int side) {
        Index idx = index;
        return idx.faceSprites.computeIfAbsent(state, s -> computeFaceSprites(idx, s))[side];
    }

    private static TextureAtlasSprite[] computeFaceSprites(Index idx, BlockState state) {
        TextureAtlasSprite[] out = new TextureAtlasSprite[6];
        BakedModel model = idx.models.get(BlockModelShaper.stateToModelLocation(state));
        if (model == null) {
            return out;
        }
        RandomSource random = RandomSource.create();
        for (Direction d : Direction.values()) {
            random.setSeed(0L);
            List<BakedQuad> quads = model.getQuads(state, d, random, ModelData.EMPTY, null);
            if (!quads.isEmpty()) {
                out[d.get3DDataValue()] = quads.get(0).getSprite();
                continue;
            }
            random.setSeed(0L);
            for (BakedQuad q : model.getQuads(state, null, random, ModelData.EMPTY, null)) {
                if (q.getDirection() == d) {
                    out[d.get3DDataValue()] = q.getSprite();
                    break;
                }
            }
        }
        return out;
    }

    /** Pillar axis in OptiFine numbering: 0 = Y, 1 = Z, 2 = X. */
    private static int pillarAxis(BlockState state) {
        if (!(state.getBlock() instanceof RotatedPillarBlock) || !state.hasProperty(RotatedPillarBlock.AXIS)) {
            return 0;
        }
        return switch (state.getValue(RotatedPillarBlock.AXIS)) {
            case X -> 2;
            case Z -> 1;
            case Y -> 0;
        };
    }

    private static String blockEntityName(PrCtmContext ctx) {
        BlockEntity be = ctx.level.getBlockEntity(ctx.pos);
        if (be instanceof Nameable nameable && nameable.getCustomName() != null) {
            return nameable.getCustomName().getString();
        }
        return null;
    }

    /**
     * OptiFine skipConnectedTexture: the top/bottom edge of a pane or bar is hidden where the pane
     * above/below continues on the same side, so stacked panes read as one sheet.
     */
    private static boolean skipPaneEdge(PrCtmContext ctx, BakedQuad quad) {
        Block block = ctx.state.getBlock();
        if (!(block instanceof IronBarsBlock)) {
            return false;
        }
        Direction face = quad.getDirection();
        if (face != Direction.UP && face != Direction.DOWN || !PrQuads.isFaceQuad(quad)) {
            return false;
        }
        BlockState other = ctx.level.getBlockState(ctx.pos.relative(face));
        if (other.getBlock() != block) {
            return false;
        }
        if (block instanceof StainedGlassPaneBlock a && other.getBlock() instanceof StainedGlassPaneBlock b
                && a.getColor() != b.getColor()) {
            return false;
        }
        float midX = PrQuads.midX(quad);
        if (midX < 0.4F) {
            return other.getValue(BlockStateProperties.WEST);
        }
        if (midX > 0.6F) {
            return other.getValue(BlockStateProperties.EAST);
        }
        float midZ = PrQuads.midZ(quad);
        if (midZ < 0.4F) {
            return other.getValue(BlockStateProperties.NORTH);
        }
        return midZ <= 0.6F || other.getValue(BlockStateProperties.SOUTH);
    }
}
