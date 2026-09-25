package net.pryzma.ctm;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;

/**
 * Block atlas source ({@code "type": "pryzma:ctm"} in {@code atlases/blocks.json}) that adds every
 * CTM tile of the current packs, wherever it lives ({@code optifine/}, {@code mcpatcher/} or
 * {@code textures/}). Parsing happens here, inside the atlas load, so rules and tiles always come
 * from the same resource state; the rules are handed to {@link PryzmaCtm} for model baking.
 */
public final class PrCtmSpriteSource implements SpriteSource {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Pryzma.MODID, "ctm");
    public static final PrCtmSpriteSource INSTANCE = new PrCtmSpriteSource();
    public static final SpriteSourceType TYPE = new SpriteSourceType(MapCodec.unit(INSTANCE));

    private PrCtmSpriteSource() {
    }

    @Override
    public void run(ResourceManager manager, Output output) {
        if (!PryzmaConfig.isConnectedTextures()) {
            PryzmaCtm.setPending(PrCtmLoader.Result.EMPTY);
            return;
        }
        PrCtmLoader.Result result = PrCtmLoader.load(manager);
        PryzmaCtm.setPending(result);
        result.sprites().forEach(output::add);
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }
}
