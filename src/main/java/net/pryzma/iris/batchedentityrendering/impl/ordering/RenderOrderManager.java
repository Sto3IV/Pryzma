package net.pryzma.iris.batchedentityrendering.impl.ordering;

import net.pryzma.iris.batchedentityrendering.impl.TransparencyType;
import net.minecraft.client.renderer.RenderType;

import java.util.List;

public interface RenderOrderManager {
	void begin(RenderType type);

	void startGroup();

	boolean maybeStartGroup();

	boolean isInGroup();

	void endGroup();

	void reset();

	void resetType(TransparencyType type);

	List<RenderType> getRenderOrder();
}
