package net.pryzma.shader.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pathways.HandRenderer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class MixinItemInHandRenderer {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void pryzma$skipTranslucentHands(PlayerRenderState playerRenderState, FirstPersonHandsAndItemsRenderState firstPersonState, float f, float g, InteractionHand interactionHand, float h, ItemStack itemStack, float i, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int j, CallbackInfo ci) {
		if (PryzmaShaders.isPackInUseQuick() && HandRenderer.INSTANCE.isActive()) {
			if (HandRenderer.INSTANCE.isRenderingSolid() == HandRenderer.INSTANCE.isHandTranslucent(itemStack)) {
				ci.cancel();
			}
		}
	}
}
