package net.pryzma.farlands.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

final class TintLevel implements BlockAndTintGetter {
   private @Nullable Biome biome;
   private BlockState state = Blocks.AIR.defaultBlockState();
   private int minY;
   private int height = 384;
   private int x;
   private int z;

   void at(int x, int z) {
      this.x = x;
      this.z = z;
   }

   void set(@Nullable Biome biome, BlockState state, int minY, int height) {
      this.x = 0;
      this.z = 0;
      this.biome = biome;
      this.state = state;
      this.minY = minY;
      this.height = height;
   }

   public CardinalLighting cardinalLighting() {
      return CardinalLighting.DEFAULT;
   }

   public LevelLightEngine getLightEngine() {
      return LevelLightEngine.EMPTY;
   }

   public int getBlockTint(BlockPos pos, ColorResolver color) {
      return this.biome == null ? -1 : color.getColor(this.biome, pos.getX(), pos.getZ());
   }

   public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
      return null;
   }

   public BlockState getBlockState(BlockPos pos) {
      return pos.getX() == this.x && pos.getZ() == this.z && pos.getY() == 64 ? this.state : Blocks.AIR.defaultBlockState();
   }

   public FluidState getFluidState(BlockPos pos) {
      return pos.getX() == this.x && pos.getZ() == this.z && pos.getY() == 64 ? this.state.getFluidState() : Fluids.EMPTY.defaultFluidState();
   }

   public int getHeight() {
      return this.height;
   }

   public int getMinY() {
      return this.minY;
   }
}
