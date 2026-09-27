package net.pryzma.iris.helpers;

import net.pryzma.iris.gl.shader.ShaderCompileException;
import net.minecraft.server.ChainedJsonException;

public class FakeChainedJsonException extends ChainedJsonException {
	private final ShaderCompileException trueException;

	public FakeChainedJsonException(ShaderCompileException e) {
		super("", e);
		this.trueException = e;
	}

	public ShaderCompileException getTrueException() {
		return trueException;
	}
}
