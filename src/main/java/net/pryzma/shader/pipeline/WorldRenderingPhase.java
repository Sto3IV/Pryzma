package net.pryzma.shader.pipeline;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;

public enum WorldRenderingPhase {
	NONE,
	SKY,
	SUNSET,
	CUSTOM_SKY,
	SUN,
	MOON,
	STARS,
	VOID,
	TERRAIN_SOLID,
	TERRAIN_CUTOUT_MIPPED,
	TERRAIN_CUTOUT,
	ENTITIES,
	BLOCK_ENTITIES,
	DESTROY,
	OUTLINE,
	DEBUG,
	HAND_SOLID,
	TERRAIN_TRANSLUCENT,
	TRIPWIRE,
	PARTICLES,
	CLOUDS,
	RAIN_SNOW,
	WORLD_BORDER,
	HAND_TRANSLUCENT;

	public static WorldRenderingPhase fromTerrainLayer(ChunkSectionLayer layer) {
		if (layer == ChunkSectionLayer.SOLID) {
			return WorldRenderingPhase.TERRAIN_SOLID;
		} else if (layer == ChunkSectionLayer.CUTOUT) {
			return WorldRenderingPhase.TERRAIN_CUTOUT;
		} else if (layer == ChunkSectionLayer.TRANSLUCENT) {
			return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
		} else {
			return WorldRenderingPhase.TERRAIN_SOLID;
		}
	}
}
