package net.pryzma.iris.mixin.forge;

import net.pryzma.iris.Iris;
import net.pryzma.iris.api.v0.IrisApi;
import net.pryzma.iris.pipeline.programs.FallbackShader;
import net.pryzma.iris.pipeline.programs.ShaderAccess;
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
		if (!Iris.isPackInUseQuick()) {
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
