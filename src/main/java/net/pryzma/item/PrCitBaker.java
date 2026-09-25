package net.pryzma.item;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.gson.JsonObject;
import com.mojang.math.Transformation;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Bakes CIT models outside the vanilla bakery, which only knows models under {@code models/}.
 * Pack-local models come from the loader's resolved JSON, other models from {@code models/};
 * {@code builtin/generated} items go through the item model generator exactly as the vanilla
 * baker does. Instances live for one bake.
 */
final class PrCitBaker implements ModelBaker {
    private record Key(ResourceLocation id, Transformation rotation, boolean uvLocked) {
    }

    private final PrCitLoader.Result source;
    private final Function<Material, TextureAtlasSprite> sprites;
    private final Consumer<String> warn;
    private final Map<ResourceLocation, UnbakedModel> unbaked = new HashMap<>();
    private final Map<Key, BakedModel> baked = new HashMap<>();
    private final ItemModelGenerator generator = new ItemModelGenerator();

    PrCitBaker(PrCitLoader.Result source, Function<Material, TextureAtlasSprite> sprites, Consumer<String> warn) {
        this.source = source;
        this.sprites = sprites;
        this.warn = warn;
    }

    /** The baked model at {@code id}, or {@code null} when it cannot be read. */
    BakedModel bake(ResourceLocation id) {
        if (!exists(id)) {
            warn.accept("Model not found: " + id);
            return null;
        }
        return bake(id, BlockModelRotation.X0_Y0, sprites);
    }

    /** A generated item model with one layer per sprite, as {@code item/generated} would give. */
    BakedModel generated(List<ResourceLocation> layers) {
        JsonObject textures = new JsonObject();
        for (int i = 0; i < layers.size(); i++) {
            textures.addProperty("layer" + i, layers.get(i).toString());
        }
        JsonObject json = new JsonObject();
        json.addProperty("parent", "builtin/generated");
        json.add("textures", textures);
        BlockModel model = BlockModel.fromString(json.toString());
        model.name = "pryzma:cit_generated";
        model.resolveParents(this::getModel);
        return bakeUncached(model, BlockModelRotation.X0_Y0, sprites);
    }

    private boolean exists(ResourceLocation id) {
        return PrCitModelJson.isLocal(id) ? source.models().containsKey(id)
                : id.getPath().startsWith("builtin/") || source.resources().find(PrCitModelJson.file(id)).isPresent();
    }

    @Override
    public UnbakedModel getModel(ResourceLocation id) {
        UnbakedModel model = unbaked.get(id);
        if (model == null) {
            model = load(id);
            unbaked.put(id, model);
        }
        return model;
    }

    private UnbakedModel load(ResourceLocation id) {
        String path = id.getPath();
        if (path.equals("builtin/generated")) {
            return ModelBakery.GENERATION_MARKER;
        }
        if (path.equals("builtin/entity")) {
            return ModelBakery.BLOCK_ENTITY_MARKER;
        }
        try {
            BlockModel model = null;
            if (PrCitModelJson.isLocal(id)) {
                JsonObject json = source.models().get(id);
                model = json == null ? null : BlockModel.fromString(json.toString());
            } else if (!path.startsWith("builtin/")) {
                Optional<Resource> resource = source.resources().find(PrCitModelJson.file(id));
                if (resource.isPresent()) {
                    try (Reader reader = resource.get().openAsReader()) {
                        model = BlockModel.fromStream(reader);
                    }
                }
            }
            if (model != null) {
                model.name = id.toString();
                return model;
            }
            if (!id.equals(ModelBakery.MISSING_MODEL_LOCATION)) {
                warn.accept("Model not found: " + id);
            }
        } catch (IOException | RuntimeException e) {
            warn.accept("Cannot read model " + id + ": " + e.getMessage());
        }
        BlockModel missing = BlockModel.fromString(ModelBakery.MISSING_MODEL_MESH);
        missing.name = ModelBakery.MISSING_MODEL_LOCATION.toString();
        return missing;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BakedModel bake(ResourceLocation id, ModelState state) {
        return bake(id, state, sprites);
    }

    @Override
    public BakedModel bake(ResourceLocation id, ModelState state, Function<Material, TextureAtlasSprite> getter) {
        Key key = new Key(id, state.getRotation(), state.isUvLocked());
        if (baked.containsKey(key)) {
            return baked.get(key);
        }
        UnbakedModel model = getModel(id);
        model.resolveParents(this::getModel);
        BakedModel result = bakeUncached(model, state, getter);
        baked.put(key, result);
        return result;
    }

    @Override
    public BakedModel bakeUncached(UnbakedModel model, ModelState state, Function<Material, TextureAtlasSprite> getter) {
        if (model instanceof BlockModel block && block.getRootModel() == ModelBakery.GENERATION_MARKER) {
            return generator.generateBlockModel(getter, block).bake(this, block, getter, state, false);
        }
        return model.bake(this, getter, state);
    }

    @Override
    public UnbakedModel getTopLevelModel(ModelResourceLocation location) {
        return null;
    }

    @Override
    public Function<Material, TextureAtlasSprite> getModelTextureGetter() {
        return sprites;
    }
}
