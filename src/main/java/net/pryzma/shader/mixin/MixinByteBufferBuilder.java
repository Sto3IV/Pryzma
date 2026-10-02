package net.pryzma.shader.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.pryzma.shader.vertices.MojangBufferAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ByteBufferBuilder.class)
public class MixinByteBufferBuilder implements MojangBufferAccessor {
	@Shadow
	long pointer;

	@Override
	public long getPointer() {
		return this.pointer;
	}
}
