package net.pryzma.iris.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.pryzma.iris.gl.texture.TextureType;
import net.pryzma.iris.helpers.Tri;
import net.pryzma.iris.pipeline.transform.Patch;
import net.pryzma.iris.shaderpack.texture.TextureStage;

public class ComputeParameters extends TextureStageParameters {
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public ComputeParameters(Patch patch, TextureStage stage,
							 Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		super(patch, stage, textureMap);
	}

	// since this class has no fields, hashCode() and equals() are inherited from
	// TextureStageParameters
}
