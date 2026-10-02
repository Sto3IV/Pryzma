package net.pryzma.shader.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.pryzma.shader.gl.texture.TextureType;
import net.pryzma.shader.helpers.Tri;
import net.pryzma.shader.pipeline.transform.Patch;
import net.pryzma.shader.shaderpack.texture.TextureStage;

public class DHParameters extends Parameters {
	public DHParameters(Patch patch, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		super(patch, textureMap);
	}

	@Override
	public TextureStage getTextureStage() {
		return TextureStage.GBUFFERS_AND_SHADOW;
	}
}
