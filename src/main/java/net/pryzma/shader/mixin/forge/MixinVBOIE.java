package net.pryzma.shader.mixin.forge;

import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.api.v0.ShaderApi;
import net.pryzma.shader.pipeline.programs.FallbackShader;
import net.pryzma.shader.pipeline.programs.ShaderAccess;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "blusunrize/immersiveengineering/client/utils/IEGLShaders", remap = false)
public class MixinVBOIE {
	@Shadow
	private static ShaderInstance vboShader;

	@Overwrite
	public static ShaderInstance getVboShader() {
		if (!PryzmaShaders.isPackInUseQuick()) {
			return vboShader;
		} else {
			ShaderInstance shader = ShaderAccess.getIEVBOShader();
			if (shader == null || shader instanceof FallbackShader) {
				return vboShader;
			} else {
				return shader;
			}
		}
	}
}
