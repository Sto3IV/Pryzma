package net.pryzma.item;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;

/**
 * Block atlas source ({@code "type": "pryzma:cit"} in {@code atlases/blocks.json}) that reads the
 * CIT rules of the current packs and adds the sprites their item textures and models use. The
 * rules are handed to {@link PrCitModels}, which bakes them once the atlas is stitched, so rules
 * and sprites always come from the same resource state.
 */
public final class PrCitSpriteSource implements SpriteSource {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Pryzma.MODID, "cit");
    public static final PrCitSpriteSource INSTANCE = new PrCitSpriteSource();
    public static final SpriteSourceType TYPE = new SpriteSourceType(MapCodec.unit(INSTANCE));

    private PrCitSpriteSource() {
    }

    @Override
    public void run(ResourceManager manager, Output output) {
        PrCitLoader.Result result = PryzmaConfig.prCustomItems ? PrCitLoader.load(manager) : PrCitLoader.Result.EMPTY;
        PrCitModels.setPending(result);
        result.sprites().forEach(output::add);
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }
}
