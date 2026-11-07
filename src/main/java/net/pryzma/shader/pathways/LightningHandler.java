package net.pryzma.shader.pathways;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

public class LightningHandler {
	public static final RenderType IRIS_LIGHTNING = RenderTypes.lightning();
	public static final Function<Identifier, RenderType> MEKANISM_FLAME = id -> RenderTypes.lightning();
	public static final RenderType MEKASUIT = RenderTypes.lightning();
	public static final Function<Identifier, RenderType> SPS = id -> RenderTypes.lightning();
}
