package net.pryzma.item;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrResources;

/**
 * CIT model files. A model named by a rule may live beside the properties
 * ({@code optifine/cit/.../x.json}, read as is) or be a regular model ({@code item/x}, read from
 * {@code models/}). Pack-local models refer to their neighbours with OptiFine paths
 * ({@code "parent": "./x"}, {@code "layer0": "./item/x"}); those are resolved here, in the JSON,
 * so the vanilla model code sees ordinary locations.
 */
final class PrCitModelJson {
    private PrCitModelJson() {
    }

    /** Whether the model's JSON lives in the pack's OptiFine tree rather than under {@code models/}. */
    static boolean isLocal(ResourceLocation model) {
        return PrPaths.isOptifine(model);
    }

    static ResourceLocation file(ResourceLocation model) {
        return isLocal(model) ? model.withSuffix(".json") : model.withPath("models/" + model.getPath() + ".json");
    }

    /** The model's JSON with pack-relative references resolved; {@code null} when missing or malformed. */
    static JsonObject read(PrResources res, ResourceLocation model, Consumer<String> warn) {
        Optional<Resource> resource = res.find(file(model));
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) {
                text = text.substring(1);
            }
            JsonElement json = JsonParser.parseString(text);
            if (!json.isJsonObject()) {
                warn.accept("Model is not a JSON object: " + model);
                return null;
            }
            JsonObject object = json.getAsJsonObject();
            if (isLocal(model)) {
                resolve(object, PrPaths.parent(model.getPath()));
            }
            return object;
        } catch (IOException | RuntimeException e) {
            warn.accept("Cannot read model " + model + ": " + e.getMessage());
            return null;
        }
    }

    /** Rewrites parent, texture and override references relative to {@code dir}. */
    static void resolve(JsonObject json, String dir) {
        if (json.has("parent") && json.get("parent").isJsonPrimitive()) {
            json.addProperty("parent", modelRef(json.get("parent").getAsString(), dir));
        }
        if (json.has("textures") && json.get("textures").isJsonObject()) {
            JsonObject textures = json.getAsJsonObject("textures");
            for (String key : List.copyOf(textures.keySet())) {
                JsonElement value = textures.get(key);
                if (value.isJsonPrimitive() && !value.getAsString().startsWith("#")) {
                    textures.addProperty(key, textureRef(value.getAsString(), dir));
                }
            }
        }
        if (json.has("overrides") && json.get("overrides").isJsonArray()) {
            for (JsonElement o : json.getAsJsonArray("overrides")) {
                if (o.isJsonObject() && o.getAsJsonObject().has("model") && o.getAsJsonObject().get("model").isJsonPrimitive()) {
                    o.getAsJsonObject().addProperty("model", modelRef(o.getAsJsonObject().get("model").getAsString(), dir));
                }
            }
        }
    }

    static String modelRef(String value, String dir) {
        return fold(PrPaths.stripExtension(PrPaths.resolve(value, dir), ".json"));
    }

    /** A texture reference as a block atlas sprite name: no {@code textures/} prefix, no {@code .png}. */
    static String textureRef(String value, String dir) {
        String s = fold(PrPaths.stripExtension(PrPaths.resolve(value, dir), ".png"));
        int colon = s.indexOf(':');
        String path = s.substring(colon + 1);
        return path.startsWith("textures/") ? s.substring(0, colon + 1) + path.substring("textures/".length()) : s;
    }

    /** {@code mcpatcher/} spelled references mean the logical {@code optifine/} tree. */
    private static String fold(String s) {
        int colon = s.indexOf(':');
        String path = s.substring(colon + 1);
        return path.startsWith(PrPaths.MCPATCHER)
                ? s.substring(0, colon + 1) + PrPaths.OPTIFINE + path.substring(PrPaths.MCPATCHER.length())
                : s;
    }

    /** Models a JSON depends on: its parent and its override models. */
    static List<ResourceLocation> dependencies(JsonObject json) {
        List<ResourceLocation> out = new ArrayList<>();
        if (json.has("parent") && json.get("parent").isJsonPrimitive()) {
            add(out, json.get("parent").getAsString());
        }
        if (json.has("overrides") && json.get("overrides").isJsonArray()) {
            for (JsonElement o : json.getAsJsonArray("overrides")) {
                if (o.isJsonObject() && o.getAsJsonObject().has("model") && o.getAsJsonObject().get("model").isJsonPrimitive()) {
                    add(out, o.getAsJsonObject().get("model").getAsString());
                }
            }
        }
        return out;
    }

    /** Sprite names a JSON uses directly (texture variables excluded). */
    static List<ResourceLocation> textures(JsonObject json) {
        List<ResourceLocation> out = new ArrayList<>();
        if (json.has("textures") && json.get("textures").isJsonObject()) {
            for (var e : json.getAsJsonObject("textures").entrySet()) {
                if (e.getValue().isJsonPrimitive() && !e.getValue().getAsString().startsWith("#")) {
                    add(out, e.getValue().getAsString());
                }
            }
        }
        return out;
    }

    private static void add(List<ResourceLocation> out, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null) {
            out.add(location);
        }
    }
}
