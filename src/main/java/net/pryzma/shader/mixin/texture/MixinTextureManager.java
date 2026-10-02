package net.pryzma.shader.mixin.texture;

import net.pryzma.shader.pbr.format.TextureFormatLoader;
import net.pryzma.shader.pbr.texture.PBRTextureManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(TextureManager.class)
public class MixinTextureManager {
	@SuppressWarnings("UnresolvedMixinReference")
	@Inject(method = {
		"method_18167",
		"lambda$reload$5"
	}, at = @At("TAIL"), require = 1)
	private void pryzma$onTailReloadLambda(ResourceManager resourceManager, Executor applyExecutor, CompletableFuture<?> future, Void void1, CallbackInfo ci) {
		TextureFormatLoader.reload(resourceManager);
		PBRTextureManager.INSTANCE.clear();
	}

	@Inject(method = "_dumpAllSheets(Ljava/nio/file/Path;)V", at = @At("RETURN"))
	private void pryzma$onInnerDumpTextures(Path path, CallbackInfo ci) {
		PBRTextureManager.INSTANCE.dumpTextures(path);
	}

	@Inject(method = "close()V", at = @At("TAIL"), remap = false)
	private void pryzma$onTailClose(CallbackInfo ci) {
		PBRTextureManager.INSTANCE.close();
	}
}
