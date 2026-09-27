package net.pryzma.iris.mixinterface;

public interface ShadowRenderRegion {
	void swapToRegularRenderList();

	void swapToShadowRenderList();

	void iris$forceClearAllBatches();
}
