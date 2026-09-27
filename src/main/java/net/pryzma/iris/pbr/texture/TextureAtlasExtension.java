package net.pryzma.iris.pbr.texture;

import org.jetbrains.annotations.Nullable;

public interface TextureAtlasExtension {
	@Nullable
	PBRAtlasHolder getPBRHolder();

	PBRAtlasHolder getOrCreatePBRHolder();
}
