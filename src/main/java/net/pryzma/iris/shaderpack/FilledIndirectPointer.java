package net.pryzma.iris.shaderpack;

import net.pryzma.iris.gl.buffer.ShaderStorageBufferHolder;
import net.pryzma.iris.shaderpack.properties.IndirectPointer;

public record FilledIndirectPointer(int buffer, long offset) {
	public static FilledIndirectPointer basedOff(ShaderStorageBufferHolder holder, IndirectPointer pointer) {
		if (pointer == null || holder == null) return null;

		return new FilledIndirectPointer(holder.getBufferIndex(pointer.buffer()), pointer.offset());
	}
}
