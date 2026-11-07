package net.pryzma.shader.mixin.vertices;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.pryzma.shader.vertices.MojangBufferAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ByteBufferBuilder.class)
public interface MixinByteBufferBuilder extends MojangBufferAccessor {
	@Override
	@Accessor("pointer")
	long getPointer();
}
