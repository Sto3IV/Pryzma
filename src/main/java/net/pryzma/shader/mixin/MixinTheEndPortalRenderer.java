package net.pryzma.shader.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.pryzma.shader.PryzmaShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TheEndPortalRenderer.class)
public class MixinTheEndPortalRenderer {
	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/blockentity/state/EndPortalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;endPortal()Lnet/minecraft/client/renderer/rendertype/RenderType;")
	)
	private RenderType pryzma$replaceEndPortalRenderType(Operation<RenderType> original) {
		if (PryzmaShaders.getCurrentPack().isPresent()) {
			return RenderTypes.entitySolid(AbstractEndPortalRenderer.END_PORTAL_LOCATION);
		}
		return original.call();
	}
}
