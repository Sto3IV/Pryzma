package net.pryzma.shader.mixin.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.pryzma.shader.shaderpack.materialmap.NamespacedId;
import net.pryzma.shader.shaderpack.materialmap.WorldRenderingSettings;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class MixinHumanoidArmorLayer {
	@Inject(method = "renderArmorPiece", at = @At("HEAD"))
	private void changeIdF(PoseStack poseStack, SubmitNodeCollector collector, ItemStack itemStack, EquipmentSlot slot, int light, HumanoidRenderState entityRenderState, CallbackInfo ci) {
		if (WorldRenderingSettings.INSTANCE.getItemIds() == null || itemStack == null || itemStack.isEmpty()) return;

		Identifier location = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
		CapturedRenderingState.INSTANCE.setCurrentRenderedItem(WorldRenderingSettings.INSTANCE.getItemIds().applyAsInt(new NamespacedId(location.getNamespace(), location.getPath())));
	}
}
