package net.pryzma.shader.shaderpack;

import net.pryzma.shader.gl.buffer.ShaderStorageBufferHolder;
import net.pryzma.shader.shaderpack.properties.IndirectPointer;

public record FilledIndirectPointer(int buffer, long offset) {
	public static FilledIndirectPointer basedOff(ShaderStorageBufferHolder holder, IndirectPointer pointer) {
		if (pointer == null || holder == null) return null;

		return new FilledIndirectPointer(holder.getBufferIndex(pointer.buffer()), pointer.offset());
	}
}
