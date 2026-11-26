package net.pryzma.farlands.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public interface FluidLooks {
   FluidLooks VANILLA = new FluidLooks() {
      @Override
      public @Nullable TextureAtlasSprite still(FluidState fluid, BlockAndTintGetter level, BlockPos pos) {
         FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid);
         return model == null ? null : model.stillMaterial().sprite();
      }

      @Override
      public int tint(FluidState fluid, BlockAndTintGetter level, BlockPos pos) {
         FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid);
         if (model == null) {
            return -1;
         }

         BlockTintSource source = model.tintSource();
         return source == null ? -1 : source.colorInWorld(fluid.createLegacyBlock(), level, pos);
      }
   };

   @Nullable TextureAtlasSprite still(FluidState var1, BlockAndTintGetter var2, BlockPos var3);

   int tint(FluidState var1, BlockAndTintGetter var2, BlockPos var3);
}
