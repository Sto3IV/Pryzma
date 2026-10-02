package net.pryzma.shader.api.v0;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ShaderApiInternal {
	static final ShaderApi INSTANCE;

	static {
		try {
			INSTANCE = (ShaderApi) Class.forName("net.pryzma.shader.apiimpl.ShaderApiV0Impl").getField("INSTANCE").get(null);
		} catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
