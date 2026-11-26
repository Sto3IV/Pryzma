package net.pryzma.farlands.lod;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockShapes {
   public static final int SKIP = -1;
   private static final ConcurrentHashMap<BlockState, Integer> HEIGHTS = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<BlockState, Integer> STORED = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<Integer, Boolean> LOOSE = new ConcurrentHashMap<>();

   private BlockShapes() {
   }

   public static int stored(BlockState state) {
      Integer known = STORED.get(state);
      if (known != null) {
         return known;
      }

      int id = computeStored(state);
      STORED.put(state, id);
      return id;
   }

   private static int computeStored(BlockState state) {
      if (state.isAir()) {
         return -1;
      }

      if (state.is(BlockTags.LEAVES)) {
         return Block.getId(state);
      }

      FluidState fluid = state.getFluidState();
      if (!fluid.isEmpty() && state.getBlock() == fluid.createLegacyBlock().getBlock()) {
         return Block.getId(state);
      }

      int fallback = fluid.isEmpty() ? -1 : Block.getId(fluid.createLegacyBlock());

      try {
         if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return fallback;
         } else {
            boolean keepsSoft = state.getBlock() instanceof SnowLayerBlock || state.is(Blocks.POWDER_SNOW);
            if (!keepsSoft && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
               return state.getRenderShape() == RenderShape.MODEL ? Block.getId(state) : fallback;
            } else {
               VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
               if (shape.isEmpty()) {
                  return fallback;
               } else {
                  boolean fullX = shape.min(Axis.X) <= 0.01 && shape.max(Axis.X) >= 0.99;
                  boolean fullZ = shape.min(Axis.Z) <= 0.01 && shape.max(Axis.Z) >= 0.99;
                  if (!fullX && !fullZ) {
                     return shape.max(Axis.X) - shape.min(Axis.X) >= 0.49 && shape.max(Axis.Z) - shape.min(Axis.Z) >= 0.49 ? Block.getId(state) : fallback;
                  } else {
                     return Block.getId(state);
                  }
               }
            }
         }
      } catch (RuntimeException e) {
         return Block.getId(state);
      }
   }

   public static boolean loose(int stateId) {
      Boolean known = LOOSE.get(stateId);
      if (known != null) {
         return known;
      }

      boolean loose;
      try {
         BlockState state = Block.stateById(stateId);
         loose = state.getFluidState().isEmpty()
            && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
            && !Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
      } catch (RuntimeException e) {
         loose = false;
      }

      LOOSE.put(stateId, loose);
      return loose;
   }

   public static int heightSixteenths(BlockState state) {
      Integer known = HEIGHTS.get(state);
      if (known != null) {
         return known;
      }

      int h = 16;

      try {
         if (state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES)) {
            VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (!shape.isEmpty() && shape.min(Axis.Y) <= 0.01) {
               double max = shape.max(Axis.Y);
               if (max < 0.99) {
                  h = Math.max(1, (int)Math.round(max * 16.0));
               }
            }
         }
      } catch (RuntimeException var6) {
      }

      HEIGHTS.put(state, h);
      return h;
   }
}
