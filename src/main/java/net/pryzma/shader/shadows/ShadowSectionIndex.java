package net.pryzma.shader.shadows;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

/**
 * Which view area sections can matter to the shadow pass: compiled with geometry or with renderable block
 * entities. One byte per {@link SectionRenderDispatcher.RenderSection#index}, written whenever a section's
 * compiled state is published or reset, so the shadow pass scans a small contiguous array instead of chasing
 * section, compiled-state and layer-set pointers for every section of the view area (most are air or were
 * never compiled).
 *
 * <p>Writers are the render thread and the chunk workers ({@code setCompiled} runs on a worker for sections
 * without layers to upload). Byte writes are atomic; a reader may see a flag one frame late, and a set flag
 * is always re-checked against the section's compiled state before use.
 */
public final class ShadowSectionIndex {
	private static volatile byte[] flags = new byte[0];

	private ShadowSectionIndex() {
	}

	/** The section's flag after its compiled state became {@code compiled}. */
	public static void update(int index, SectionRenderDispatcher.CompiledSection compiled) {
		byte[] current = flags;
		if (index >= current.length) {
			current = grow(index + 1);
		}
		current[index] = matters(compiled) ? (byte) 1 : (byte) 0;
	}

	/** Flags indexed by section index; the array may be longer than the current view area. */
	public static byte[] flags() {
		return flags;
	}

	static boolean matters(SectionRenderDispatcher.CompiledSection compiled) {
		return compiled != SectionRenderDispatcher.CompiledSection.UNCOMPILED
			&& (!compiled.hasNoRenderableLayers() || !compiled.getRenderableBlockEntities().isEmpty());
	}

	/** Sections are created on the render thread before any worker compiles them, so growth does not race writes. */
	private static synchronized byte[] grow(int minLength) {
		byte[] current = flags;
		if (current.length >= minLength) {
			return current;
		}
		byte[] grown = new byte[Math.max(minLength, current.length + (current.length >> 1))];
		System.arraycopy(current, 0, grown, 0, current.length);
		flags = grown;
		return grown;
	}
}
