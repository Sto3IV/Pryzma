package net.pryzma.shader.mixin.texture;

import net.pryzma.shader.pbr.format.TextureFormatLoader;
import net.pryzma.shader.pbr.texture.PBRTextureManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(TextureManager.class)
public class MixinTextureManager {
	@Inject(method = "reload", at = @At("RETURN"), require = 0)
	private void pryzma$onReload(PreparableReloadListener.SharedState sharedState, Executor backgroundExecutor, PreparableReloadListener.PreparationBarrier barrier, Executor gameExecutor, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		cir.getReturnValue().thenRunAsync(() -> {
			TextureFormatLoader.reload(sharedState.resourceManager());
			PBRTextureManager.INSTANCE.clear();
		}, gameExecutor);
	}

	@Inject(method = "dumpAllSheets(Ljava/nio/file/Path;)V", at = @At("RETURN"), require = 0)
	private void pryzma$onInnerDumpTextures(Path path, CallbackInfo ci) {
		PBRTextureManager.INSTANCE.dumpTextures(path);
	}

	@Inject(method = "close()V", at = @At("TAIL"), remap = false, require = 0)
	private void pryzma$onTailClose(CallbackInfo ci) {
		PBRTextureManager.INSTANCE.close();
	}
}
