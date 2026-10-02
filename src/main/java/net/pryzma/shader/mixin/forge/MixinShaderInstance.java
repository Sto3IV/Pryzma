package net.pryzma.shader.mixin.forge;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.compat.SkipList;
import net.pryzma.shader.mixinterface.ShaderInstanceInterface;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static net.pryzma.shader.compat.SkipList.ALWAYS;
import static net.pryzma.shader.compat.SkipList.shouldSkipList;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance implements ShaderInstanceInterface {
	@Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V", require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/GsonHelper;parse(Ljava/io/Reader;)Lcom/google/gson/JsonObject;"))
	public void pryzma$setupGeometryShader(ResourceProvider resourceProvider, ResourceLocation shaderLocation, VertexFormat p_173338_, CallbackInfo ci) {
		try {
			this.pryzma$createExtraShaders(resourceProvider, shaderLocation.getPath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V", at = @At("TAIL"), require = 0)
	private void pryzma$storeSkipNeo(ResourceProvider resourceProvider, ResourceLocation string, VertexFormat vertexFormat, CallbackInfo ci) {
		MethodHandle shouldSkip = shouldSkipList.computeIfAbsent(getClass(), x -> {
			try {
				MethodHandle pryzma$skipDraw = MethodHandles.lookup().findVirtual(x, "pryzma$skipDraw", MethodType.methodType(boolean.class));
				PryzmaShaders.logger.warn("Class " + x.getName() + " has opted out of being rendered with shaders.");
				return pryzma$skipDraw;
			} catch (NoSuchMethodException | IllegalAccessException e) {
				return SkipList.NONE;
			}
		});


		if (PryzmaShaders.getShaderConfig().shouldSkip(string)) {
			shouldSkip = ALWAYS;
		}

		((ShaderInstanceInterface) this).setShouldSkip(shouldSkip);
	}
}
