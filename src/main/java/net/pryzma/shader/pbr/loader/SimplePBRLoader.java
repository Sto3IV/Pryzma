package net.pryzma.shader.pbr.loader;

import net.pryzma.shader.mixin.texture.SimpleTextureAccessor;
import net.pryzma.shader.pbr.texture.PBRType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class SimplePBRLoader implements PBRTextureLoader<SimpleTexture> {
	@Override
	public void load(SimpleTexture texture, ResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer) {
		Identifier location = ((SimpleTextureAccessor) texture).getLocation();

		AbstractTexture normalTexture = createPBRTexture(location, resourceManager, PBRType.NORMAL);
		AbstractTexture specularTexture = createPBRTexture(location, resourceManager, PBRType.SPECULAR);

		if (normalTexture != null) {
			pbrTextureConsumer.acceptNormalTexture(normalTexture);
		}
		if (specularTexture != null) {
			pbrTextureConsumer.acceptSpecularTexture(specularTexture);
		}
	}

	@Nullable
	protected AbstractTexture createPBRTexture(Identifier imageLocation, ResourceManager resourceManager, PBRType pbrType) {
		Identifier pbrImageLocation = imageLocation.withPath(pbrType::appendSuffix);

		SimpleTexture pbrTexture = new SimpleTexture(pbrImageLocation);
		try {
			var contents = pbrTexture.loadContents(resourceManager);
			pbrTexture.apply(contents);
		} catch (IOException e) {
			return null;
		}

		return pbrTexture;
	}
}
