package net.pryzma.shader.mixin;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.compat.SkipList;
import net.pryzma.shader.gl.GLDebug;
import net.pryzma.shader.gl.blending.DepthColorStorage;
import net.pryzma.shader.mixinterface.ShaderInstanceInterface;
import net.pryzma.shader.pipeline.ShadedWorldRenderingPipeline;
import net.pryzma.shader.pipeline.ShaderRenderingPipeline;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.pryzma.shader.pipeline.programs.ExtendedShader;
import net.pryzma.shader.pipeline.programs.FallbackShader;
import net.pryzma.shader.shadows.ShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

import static net.pryzma.shader.compat.SkipList.*;
import static net.pryzma.shader.compat.SkipList.shouldSkipList;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance implements ShaderInstanceInterface {
	@Unique
	private static final ImmutableSet<String> ATTRIBUTE_LIST = ImmutableSet.of("Position", "Color", "Normal", "UV0", "UV1", "UV2");
	@Shadow
	private static ShaderInstance lastAppliedShader;
	@Shadow
	@Final
	private int programId;
	@Shadow
	@Final
	private Program vertexProgram;
	@Shadow
	@Final
	private Program fragmentProgram;

	@Unique
	private MethodHandle shouldSkip;

	static {
		shouldSkipList.put(ExtendedShader.class, NONE);
		shouldSkipList.put(FallbackShader.class, NONE);
	}

	@Override
	public void setShouldSkip(MethodHandle s) {
		shouldSkip = s;
	}


	public boolean pryzma$shouldSkipThis() {
		if (PryzmaShaders.getShaderConfig().shouldAllowUnknownShaders()) {
			if (ShadowRenderer.ACTIVE) return true;

			if (!shouldOverrideShaders()) return false;

			if (shouldSkip == NONE) return false;
			if (shouldSkip == ALWAYS) return true;

			try {
				return (boolean) shouldSkip.invoke(((ShaderInstance) (Object) this));
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		} else {
			return !(((Object) this) instanceof ExtendedShader || ((Object) this) instanceof FallbackShader || !shouldOverrideShaders());
		}
	}

	@Unique
	private static boolean shouldOverrideShaders() {
		WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

		if (pipeline instanceof ShaderRenderingPipeline) {
			return ((ShaderRenderingPipeline) pipeline).shouldOverrideShaders();
		} else {
			return false;
		}
	}

	@Shadow
	public abstract int getId();

	@Redirect(method = "updateLocations",
		at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap = false))
	private void pryzma$redirectLogSpam(Logger logger, String message, Object arg1, Object arg2) {
		if (((Object) this) instanceof ExtendedShader || ((Object) this) instanceof FallbackShader) {
			return;
		}

		logger.warn(message, arg1, arg2);
	}

	@Redirect(method = "<init>*", require = 1, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;glBindAttribLocation(IILjava/lang/CharSequence;)V"))
	public void pryzma$redirectBindAttributeLocation(int i, int j, CharSequence charSequence) {
		if (((Object) this) instanceof ExtendedShader && ATTRIBUTE_LIST.contains(charSequence)) {
			Uniform.glBindAttribLocation(i, j, "iris_" + charSequence);
		} else {
			Uniform.glBindAttribLocation(i, j, charSequence);
		}
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void name(ResourceProvider resourceProvider, String string, VertexFormat vertexFormat, CallbackInfo ci) {
		GLDebug.nameObject(KHRDebug.GL_PROGRAM, this.programId, string);
		GLDebug.nameObject(KHRDebug.GL_SHADER, this.vertexProgram.getId(), string);
		GLDebug.nameObject(KHRDebug.GL_SHADER, this.fragmentProgram.getId(), string);
	}

	@Inject(method = "apply", at = @At("HEAD"))
	private void pryzma$lockDepthColorState(CallbackInfo ci) {
		if (lastAppliedShader != null) {
			lastAppliedShader.clear();
			lastAppliedShader = null;
		}
	}

	@Inject(method = "apply", at = @At("TAIL"))
	private void onTail(CallbackInfo ci) {
		if (!pryzma$shouldSkipThis()) {
			if (!isKnownShader() && shouldOverrideShaders()) {
				WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

				if (pipeline instanceof ShadedWorldRenderingPipeline) {
					if (ShadowRenderer.ACTIVE) {
						// ((ShadedWorldRenderingPipeline) pipeline).bindDefaultShadow(); don't rn
					} else {
						((ShadedWorldRenderingPipeline) pipeline).bindDefault();
					}
				}
			}

			return;
		}

		DepthColorStorage.disableDepthColor();
	}

	private boolean isKnownShader() {
		return ((Object) this) instanceof ExtendedShader || ((Object) this) instanceof FallbackShader;
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void pryzma$unlockDepthColorState(CallbackInfo ci) {
		if (!pryzma$shouldSkipThis()) {
			if (!isKnownShader() && shouldOverrideShaders()) {
				WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

				if (pipeline instanceof ShadedWorldRenderingPipeline) {
					Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
				}
			}

			return;
		}

		DepthColorStorage.unlockDepthColor();
	}

	@Override
	public void pryzma$createExtraShaders(ResourceProvider provider, String name) {
		//no-op, used for ExtendedShader to call before the super constructor
	}
}
