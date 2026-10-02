package net.pryzma.shader.mixin;

import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gui.option.ShaderVideoSettings;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.List;
import java.util.Objects;

@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {
	@Unique
	private static final List<BufferPoolMXBean> iris$pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);

	@Unique
	private static final BufferPoolMXBean iris$directPool;

	static {
		BufferPoolMXBean found = null;

		for (BufferPoolMXBean pool : iris$pools) {
			if (pool.getName().equals("direct")) {
				found = pool;
				break;
			}
		}

		iris$directPool = Objects.requireNonNull(found);
	}

	// stackoverflow.com/a/3758880
	@Unique
	private static String iris$humanReadableByteCountBin(long bytes) {
		long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
		if (absB < 1024) {
			return bytes + " B";
		}
		long value = absB;
		CharacterIterator ci = new StringCharacterIterator("KMGTPE");
		for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
			value >>= 10;
			ci.next();
		}
		value *= Long.signum(bytes);
		return String.format("%.3f %ciB", value / 1024.0, ci.current());
	}

	// From Sodium
	@Unique
	private static long iris$getNativeMemoryUsage() {
		return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
	}

	@Inject(method = "getSystemInformation", at = @At("RETURN"))
	private void iris$appendShaderPackText(CallbackInfoReturnable<List<String>> cir) {
		List<String> messages = cir.getReturnValue();

		messages.add("");
		messages.add("[" + PryzmaShaders.MODNAME + "] Version: " + PryzmaShaders.getFormattedVersion());
		messages.add("");

		if (PryzmaShaders.getShaderConfig().areShadersEnabled()) {
			messages.add("[" + PryzmaShaders.MODNAME + "] Shaderpack: " + PryzmaShaders.getCurrentPackName() + (PryzmaShaders.isFallback() ? " (fallback)" : ""));
			PryzmaShaders.getCurrentPack().ifPresent(pack -> messages.add("[" + PryzmaShaders.MODNAME + "] " + pack.getProfileInfo()));
			messages.add("[" + PryzmaShaders.MODNAME + "] Color space: " + ShaderVideoSettings.colorSpace.name());
		} else {
			messages.add("[" + PryzmaShaders.MODNAME + "] Shaders are disabled");
		}

		messages.add(3, "Direct Buffers: +" + iris$humanReadableByteCountBin(iris$directPool.getMemoryUsed()));

		//if (!PryzmaShaders.isSodiumInstalled()) {
		//	messages.add(3, "Native Memory: +" + iris$humanReadableByteCountBin(iris$getNativeMemoryUsage()));
		//}
	}

	@Inject(method = "getGameInformation", at = @At("RETURN"))
	private void iris$appendShadowDebugText(CallbackInfoReturnable<List<String>> cir) {
		List<String> messages = cir.getReturnValue();

		//if (!PryzmaShaders.isSodiumInstalled() && PryzmaShaders.getCurrentPack().isPresent()) {
		//	messages.add(1, ChatFormatting.YELLOW + "[" + PryzmaShaders.MODNAME + "] Sodium isn't installed; you will have poor performance.");
		//	messages.add(2, ChatFormatting.YELLOW + "[" + PryzmaShaders.MODNAME + "] Install Sodium if you want to run benchmarks or get higher FPS!");
		//}

		PryzmaShaders.getPipelineManager().getPipeline().ifPresent(pipeline -> pipeline.addDebugText(messages));
	}
}
