package net.pryzma.shader.mixin.fabulous;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostPass;
import net.pryzma.PryzmaConfig;
import net.pryzma.util.AmdGpuDetector;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PostPass.class)
public abstract class MixinAmdFabulousFix {
    @Shadow
    @Final
    public RenderTarget outTarget;

    @Shadow
    public abstract String getName();

    /**
     * On AMD Radeon GPUs running Windows with the proprietary OpenGL ICD,
     * the Fabulous graphics transparency blit pass inadvertently inverts the vertical
     * coordinates ($Y$-flip) when copying the composite buffer to the main render target.
     * Inverting the orthographic projection matrix for this blit pass restores correct orientation
     * without affecting non-AMD systems, shaders, or other post chains.
     */
    @ModifyArg(
        method = "process",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/AbstractUniform;set(Lorg/joml/Matrix4f;)V")
    )
    private Matrix4f pryzma$fixAmdFabulousMatrix(Matrix4f matrix) {
        if (PryzmaConfig.prFixAmdFabulous && AmdGpuDetector.isAmdWindows()) {
            if ("blit".equals(this.getName()) && this.outTarget == Minecraft.getInstance().getMainRenderTarget()) {
                return new Matrix4f().setOrtho(0.0F, (float)this.outTarget.width, (float)this.outTarget.height, 0.0F, 0.1F, 1000.0F);
            }
        }
        return matrix;
    }
}
