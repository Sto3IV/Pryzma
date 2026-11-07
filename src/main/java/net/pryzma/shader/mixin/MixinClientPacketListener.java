package net.pryzma.shader.mixin;

import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gl.shader.ShaderCompileException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
	@Inject(method = "handleLogin", at = @At("TAIL"))
	private void pryzma$showUpdateMessage(ClientboundLoginPacket a, CallbackInfo ci) {
		if (Minecraft.getInstance().player == null) {
			return;
		}

		PryzmaShaders.getStoredError().ifPresent(e ->
			Minecraft.getInstance().player.sendSystemMessage(Component.translatable(e instanceof ShaderCompileException ? "pryzma.load.failure.shader" : "pryzma.load.failure.generic").append(Component.literal("Copy Info").withStyle(arg -> arg.withUnderlined(true).withColor(ChatFormatting.BLUE).withClickEvent(new ClickEvent.CopyToClipboard(e.getMessage())).withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click")))))));

		if (PryzmaShaders.loadedIncompatiblePack()) {
			Minecraft.getInstance().gui.hud.setTimes(10, 70, 140);
			PryzmaShaders.logger.warn("Incompatible pack for DH!");
			Minecraft.getInstance().player.sendSystemMessage(Component.literal("This pack doesn't have DH support.").withStyle(ChatFormatting.BOLD, ChatFormatting.RED));
			Minecraft.getInstance().player.sendSystemMessage(Component.literal("Distant Horizons (DH) chunks won't show up. This isn't a bug, get another shader.").withStyle(ChatFormatting.RED));
		}
	}
}
