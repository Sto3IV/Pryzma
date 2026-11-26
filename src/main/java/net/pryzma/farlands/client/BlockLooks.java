package net.pryzma.farlands.client;

import net.pryzma.farlands.lod.BlockShapes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects.GrassColorModifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class BlockLooks {
   private static final BlockPos TINT_POS = new BlockPos(0, 64, 0);
   private static final Direction[] DIRECTIONS = Direction.values();
   public static volatile FluidLooks fluids = FluidLooks.VANILLA;
   private final SpriteTable sprites;
   private static final int TINT_BITS = 14;
   private volatile BlockLooks.Look[] looks = new BlockLooks.Look[0];
   private volatile BlockLooks.Tint[] tints = new BlockLooks.Tint[16384];
   private final ThreadLocal<TintLevel> tintLevels = ThreadLocal.withInitial(TintLevel::new);
   private final ThreadLocal<MutableBlockPos> tintPositions = ThreadLocal.withInitial(MutableBlockPos::new);
   private final ThreadLocal<List<BlockStateModelPart>> parts = ThreadLocal.withInitial(ArrayList::new);

   public BlockLooks(SpriteTable sprites) {
      this.sprites = sprites;
   }

   public void clear() {
      this.looks = new BlockLooks.Look[0];
      this.tints = new BlockLooks.Tint[16384];
   }

   public BlockLooks.Look look(int stateId) {
      BlockLooks.Look[] table = this.looks;
      if (stateId >= 0 && stateId < table.length) {
         BlockLooks.Look known = table[stateId];
         if (known != null) {
            return known;
         }
      }

      BlockLooks.Look look;
      try {
         look = this.resolve(Block.stateById(stateId));
      } catch (RuntimeException e) {
         look = new BlockLooks.Look(0, 0, 0, -1, -1, -1, false, false, 16, 0, false, false, -1, 0, false);
      }

      if (stateId >= 0) {
         this.remember(stateId, look);
      }

      return look;
   }

   private void remember(int stateId, BlockLooks.Look look) {
      BlockLooks.Look[] table = this.looks;
      if (stateId >= table.length) {
         synchronized (this) {
            table = this.looks;
            if (stateId >= table.length) {
               table = Arrays.copyOf(table, Math.max(stateId + 1, Block.BLOCK_STATE_REGISTRY.size()));
               this.looks = table;
            }
         }
      }

      table[stateId] = look;
   }

   private BlockLooks.Look resolve(BlockState state) {
      FluidState fluid = state.getFluidState();
      if (!fluid.isEmpty() && state.getBlock() instanceof LiquidBlock) {
         TintLevel level = this.tintLevels.get();
         level.set(null, state, 0, 384);
         TextureAtlasSprite still = fluids.still(fluid, level, TINT_POS);
         int sprite = still == null ? 0 : this.sprites.index(still);
         return new BlockLooks.Look(sprite, sprite, sprite, 0, 0, 0, true, false, 16, fluid.is(FluidTags.LAVA) ? 6 : 12, false, false, -1, 0, false);
      } else {
         BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
         List<BlockStateModelPart> list = this.parts.get();
         list.clear();
         model.collectParts(RandomSource.create(42L), list);
         if (!(state.getBlock() instanceof SnowLayerBlock) && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() && !closed(list)) {
            BakedQuad cross = find(list, null);
            TextureAtlasSprite plantSprite = cross != null ? cross.materialInfo().sprite() : model.particleMaterial().sprite();
            int sprite = this.sprites.index(plantSprite);
            int tintIndex = cross != null ? cross.materialInfo().tintIndex() : -1;
            int water = fluid.isEmpty() ? -1 : Block.getId(fluid.createLegacyBlock());
            float[] cover = cover(list);
            int segments = cover[0] > 0.0F && cover[0] >= cover[1] ? Math.max(1, Math.min(4, Math.round(cover[0] * 4.0F))) : 0;
            int height = segments > 0 ? Math.max(1, Math.min(15, Math.round(cover[2] * 16.0F))) : 16;
            return new BlockLooks.Look(
               sprite, sprite, sprite, tintIndex, tintIndex, tintIndex, false, true, height, material(state, false), true, false, water, segments, false
            );
         } else {
            BakedQuad up = find(list, Direction.UP);
            BakedQuad side = firstNonNull(find(list, Direction.NORTH), find(list, Direction.EAST), find(list, Direction.SOUTH), find(list, Direction.WEST));
            BakedQuad down = find(list, Direction.DOWN);
            TextureAtlasSprite particle = model.particleMaterial().sprite();
            int top = this.sprites.index(up != null ? up.materialInfo().sprite() : particle);
            int baseSide = this.sprites.index(side != null ? side.materialInfo().sprite() : (up != null ? up.materialInfo().sprite() : particle));
            int bottom = this.sprites.index(down != null ? down.materialInfo().sprite() : particle);
            boolean cutout = this.sprites.hasTransparency(top) || this.sprites.hasTransparency(baseSide);
            BakedQuad overlay = side == null ? null : overlay(list, side);
            int sideSprite = overlay == null ? baseSide : this.sprites.layered(baseSide, this.sprites.index(overlay.materialInfo().sprite()));
            int sideTint = overlay != null ? overlay.materialInfo().tintIndex() : (side != null ? side.materialInfo().tintIndex() : -1);
            BakedQuad any = up != null ? up : side;
            boolean translucent = any != null && any.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT;
            boolean mixed = mixedSides(list);
            BlockLooks.Look own = new BlockLooks.Look(
               top,
               sideSprite,
               bottom,
               up != null ? up.materialInfo().tintIndex() : -1,
               sideTint,
               down != null ? down.materialInfo().tintIndex() : -1,
               false,
               cutout,
               BlockShapes.heightSixteenths(state),
               material(state, up != null && up.materialInfo().tintIndex() >= 0),
               false,
               translucent,
               -1,
               0,
               sideSprite != baseSide
            );
            BlockState plain = state.getBlock().defaultBlockState();
            if (mixed && plain != state && onlyFlags(state, plain) && uniformSides(plain)) {
               BlockLooks.Look base = this.look(Block.getId(plain));
               return new BlockLooks.Look(
                  own.top(),
                  base.side(),
                  own.bottom(),
                  own.topTint(),
                  base.sideTint(),
                  own.bottomTint(),
                  false,
                  own.cutout() || base.cutout(),
                  own.height(),
                  own.material(),
                  false,
                  own.translucent(),
                  -1,
                  0,
                  base.layered()
               );
            } else {
               return own;
            }
         }
      }
   }

   private static boolean mixedSides(List<BlockStateModelPart> parts) {
      TextureAtlasSprite first = null;
      boolean mixed = false;

      for (Direction direction : Plane.HORIZONTAL) {
         BakedQuad quad = find(parts, direction);
         if (quad == null) {
            return false;
         }

         if (first == null) {
            first = quad.materialInfo().sprite();
         } else if (quad.materialInfo().sprite() != first) {
            mixed = true;
         }
      }

      return mixed;
   }

   private static boolean uniformSides(BlockState state) {
      List<BlockStateModelPart> parts = new ArrayList<>();
      Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42L), parts);

      for (Direction direction : Plane.HORIZONTAL) {
         if (find(parts, direction) == null) {
            return false;
         }
      }

      return !mixedSides(parts);
   }

   private static boolean onlyFlags(BlockState state, BlockState plain) {
      for (Property<?> property : state.getProperties()) {
         if (!(property instanceof BooleanProperty) && !state.getValue(property).equals(plain.getValue(property))) {
            return false;
         }
      }

      return true;
   }

   private static @Nullable BakedQuad overlay(List<BlockStateModelPart> parts, BakedQuad base) {
      if (base.materialInfo().tintIndex() >= 0) {
         return null;
      }

      Direction direction = base.direction();

      for (BlockStateModelPart part : parts) {
         for (int d = 0; d < 2; d++) {
            for (BakedQuad quad : part.getQuads(d == 0 ? direction : null)) {
               if (quad != base
                  && quad.direction() == direction
                  && quad.materialInfo().tintIndex() >= 0
                  && quad.materialInfo().sprite() != base.materialInfo().sprite()) {
                  return quad;
               }
            }
         }
      }

      return null;
   }

   private static float[] cover(List<BlockStateModelPart> parts) {
      float[] cover = new float[3];

      for (BlockStateModelPart part : parts) {
         for (int d = 0; d <= DIRECTIONS.length; d++) {
            for (BakedQuad quad : part.getQuads(d == DIRECTIONS.length ? null : DIRECTIONS[d])) {
               Vector3fc p0 = quad.position(0);
               Vector3fc p1 = quad.position(1);
               Vector3fc p2 = quad.position(2);
               Vector3fc p3 = quad.position(3);
               float ax = p2.x() - p0.x();
               float ay = p2.y() - p0.y();
               float az = p2.z() - p0.z();
               float bx = p3.x() - p1.x();
               float by = p3.y() - p1.y();
               float bz = p3.z() - p1.z();
               float nx = ay * bz - az * by;
               float ny = az * bx - ax * bz;
               float nz = ax * by - ay * bx;
               float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
               if (!(length <= 1.0E-6F)) {
                  if (Math.abs(ny) > 0.7F * length) {
                     if (ny > 0.0F) {
                        cover[0] += length * 0.5F;
                        cover[2] = Math.max(cover[2], Math.max(Math.max(p0.y(), p1.y()), Math.max(p2.y(), p3.y())));
                     }
                  } else {
                     cover[1] += length * 0.5F;
                  }
               }
            }
         }
      }

      return cover;
   }

   private static boolean closed(List<BlockStateModelPart> parts) {
      for (Direction direction : DIRECTIONS) {
         boolean covered = false;

         for (BlockStateModelPart part : parts) {
            if (!part.getQuads(direction).isEmpty()) {
               covered = true;
               break;
            }
         }

         if (!covered) {
            return false;
         }
      }

      return true;
   }

   private static int material(BlockState state, boolean tintedTop) {
      if (state.is(BlockTags.LEAVES)) {
         return 1;
      }

      if (state.getLightEmission() > 0) {
         return 15;
      }

      if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
         return 3;
      }

      if (state.is(BlockTags.SNOW) || state.is(BlockTags.ICE) || state.is(Blocks.SNOW_BLOCK)) {
         return 8;
      }

      if (state.is(BlockTags.SAND)) {
         return 9;
      }

      if (state.is(BlockTags.TERRACOTTA)) {
         return 10;
      }

      if (state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.NYLIUM)) {
         return 11;
      }

      if (state.is(BlockTags.DIRT)) {
         return tintedTop ? 13 : 5;
      }

      if (!state.is(Blocks.DEEPSLATE) && !state.is(Blocks.COBBLED_DEEPSLATE)) {
         if (state.getSoundType() == SoundType.METAL) {
            return 4;
         } else {
            return !state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? 0 : 2;
         }
      } else {
         return 7;
      }
   }

   @SafeVarargs
   private static <T> @Nullable T firstNonNull(T... values) {
      for (T value : values) {
         if (value != null) {
            return value;
         }
      }

      return null;
   }

   private static @Nullable BakedQuad find(List<BlockStateModelPart> parts, Direction direction) {
      for (BlockStateModelPart part : parts) {
         List<BakedQuad> culled = part.getQuads(direction);
         if (!culled.isEmpty()) {
            return culled.getFirst();
         }
      }

      for (BlockStateModelPart part : parts) {
         for (BakedQuad quad : part.getQuads(null)) {
            if (quad.direction() == direction) {
               return quad;
            }
         }
      }

      return null;
   }

   public int tint(int stateId, int tintIndex, boolean fluid, int biomeId, Registry<Biome> biomes) {
      return this.tint(stateId, tintIndex, fluid, biomeId, biomes, 0, 0);
   }

   public int tint(int stateId, int tintIndex, boolean fluid, int biomeId, Registry<Biome> biomes, int x, int z) {
      if (tintIndex < 0) {
         return 16777215;
      }

      Biome biome = (Biome)biomes.byId(biomeId);
      if (biome != null && placeDependent(biome)) {
         return this.compute(stateId, tintIndex, fluid, biome, x, z);
      }

      long key = (long)stateId << 32 | (long)(tintIndex & 0xFF) << 16 | biomeId & 65535;
      BlockLooks.Tint[] table = this.tints;
      int slot = (int)(key * -7046029254386353131L >>> 50);
      BlockLooks.Tint known = table[slot];
      if (known != null && known.key == key) {
         return known.color;
      }

      int color = this.compute(stateId, tintIndex, fluid, biome, 0, 0);
      table[slot] = new BlockLooks.Tint(key, color);
      return color;
   }

   private static boolean placeDependent(Biome biome) {
      GrassColorModifier modifier = biome.getSpecialEffects().grassColorModifier();
      return modifier != GrassColorModifier.NONE && modifier != GrassColorModifier.DARK_FOREST;
   }

   private int compute(int stateId, int tintIndex, boolean fluid, @Nullable Biome biome, int x, int z) {
      int color = 16777215;

      try {
         BlockState state = Block.stateById(stateId);
         TintLevel level = this.tintLevels.get();
         level.set(biome, state, 0, 384);
         level.at(x, z);
         BlockPos pos = (BlockPos)(x == 0 && z == 0 ? TINT_POS : this.tintPositions.get().set(x, 64, z));
         if (fluid) {
            int c = fluids.tint(state.getFluidState(), level, pos);
            if (c != -1) {
               color = c;
            }
         } else {
            BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);
            if (source != null) {
               color = source.colorInWorld(state, level, pos);
            }
         }
      } catch (RuntimeException var12) {
      }

      return color & 16777215;
   }

   public record Look(
      int top,
      int side,
      int bottom,
      int topTint,
      int sideTint,
      int bottomTint,
      boolean fluid,
      boolean cutout,
      int height,
      int material,
      boolean plant,
      boolean translucent,
      int water,
      int segments,
      boolean layered
   ) {
   }

   private record Tint(long key, int color) {
   }
}
