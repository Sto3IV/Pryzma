package net.pryzma.iris.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.pryzma.iris.gl.texture.TextureType;
import net.pryzma.iris.helpers.Tri;
import net.pryzma.iris.pipeline.transform.Patch;
import net.pryzma.iris.shaderpack.texture.TextureStage;

public class DHParameters extends Parameters {
	public DHParameters(Patch patch, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		super(patch, textureMap);
	}

	@Override
	public TextureStage getTextureStage() {
		return TextureStage.GBUFFERS_AND_SHADOW;
	}
}
