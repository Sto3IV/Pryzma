package net.pryzma.iris.mixin.forge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.pryzma.iris.Iris;
import net.pryzma.iris.pathways.LightningHandler;
import net.pryzma.iris.vertices.ImmediateState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Pseudo
@Mixin(targets = "mekanism.client.render.lib.effect.BillboardingEffectRenderer", remap = false)
public class MixinRenderSPS {
	@WrapOperation(method = "render(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;Ljava/util/function/Supplier;)V", at = @At(
		value = "FIELD",
		target = "Lmekanism/client/render/MekanismRenderType;SPS:Ljava/util/function/Function;"))
	private static Function<ResourceLocation, RenderType> doNotSwitchShaders(Operation<Function<ResourceLocation, RenderType>> original) {
		if (Iris.isPackInUseQuick() && ImmediateState.isRenderingLevel) {
			return LightningHandler.SPS;
		} else {
			return original.call();
		}
	}
}
