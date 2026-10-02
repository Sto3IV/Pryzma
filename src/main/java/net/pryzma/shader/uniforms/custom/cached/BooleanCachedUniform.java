package net.pryzma.shader.uniforms.custom.cached;

import net.pryzma.shader.stareval.function.FunctionReturn;
import net.pryzma.shader.stareval.function.Type;
import net.pryzma.shader.gl.uniform.UniformUpdateFrequency;
import org.lwjgl.opengl.GL21;

import java.util.function.BooleanSupplier;

public class BooleanCachedUniform extends CachedUniform {

	final private BooleanSupplier supplier;
	private boolean cached;

	public BooleanCachedUniform(String name, UniformUpdateFrequency updateFrequency, BooleanSupplier supplier) {
		super(name, updateFrequency);
		this.supplier = supplier;
	}

	@Override
	protected boolean doUpdate() {
		boolean prev = this.cached;
		this.cached = this.supplier.getAsBoolean();
		return prev != cached;
	}

	@Override
	public void push(int location) {
		GL21.glUniform1i(location, this.cached ? 1 : 0);
	}

	@Override
	public void writeTo(FunctionReturn functionReturn) {
		functionReturn.booleanReturn = this.cached;
	}

	@Override
	public Type getType() {
		return Type.Boolean;
	}
}
