package net.pryzma.shader.mixinterface;

public interface ShadowRenderRegion {
	void swapToRegularRenderList();

	void swapToShadowRenderList();

	void iris$forceClearAllBatches();
}
