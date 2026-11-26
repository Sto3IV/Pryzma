package net.pryzma.farlands.mixin.client;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.pryzma.farlands.client.SkyFog;
import net.pryzma.farlands.client.FarlandsClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
   @ModifyArg(
      method = "render",
      require = 0,
      index = 2,
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V"
      )
   )
   private GpuBufferSlice prFarlands$skyFog(FrameGraphBuilder frame, CameraRenderState camera, GpuBufferSlice fog) {
      return SkyFog.swap(fog, camera.fogData);
   }

   @Inject(method = "invalidateCompiledGeometry", at = @At("HEAD"), require = 0)
   private void prFarlands$vanillaReset(CallbackInfo ci) {
      FarlandsClient client = FarlandsClient.peek();
      if (client != null) {
         client.onVanillaReset();
      }
   }
}
