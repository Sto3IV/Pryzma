package net.pryzma.entity.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.resources.ResourceLocation;
import net.pryzma.core.res.PrPaths;

/**
 * Parser of OptiFine {@code .jem} / {@code .jpm} custom entity models, following OptiFine's
 * {@code CustomEntityModelParser} and {@code PlayerItemParser}:
 *
 * <ul>
 *   <li>per top-level entry: {@code baseId} copies the keys of an earlier entry with that
 *       {@code id}, then {@code model} copies the keys of an external {@code .jpm}; keys already
 *       present win, {@code id} is never copied</li>
 *   <li>{@code invertAxis} negates the named axes of {@code translate} and {@code rotate} and
 *       mirrors box coordinates ({@code x = -x - width})</li>
 *   <li>a box uses {@code textureOffset} (sizes truncated to whole pixels, as OptiFine does) or
 *       face rectangles {@code uvDown uvUp uvNorth uvSouth uvWest uvEast} (legacy
 *       {@code uvFront uvBack uvLeft uvRight} fill missing north, south, west, east)</li>
 *   <li>EMF extensions: per-axis growth {@code sizeAddX/Y/Z} or {@code sizesAdd}</li>
 * </ul>
 *
 * Resource paths: a bare name is next to the model, {@code ./x} likewise, {@code ~/x} is under
 * {@code optifine/}, anything else is a path from the namespace root.
 */
public final class PrJemParser {
    private static final int DEFAULT_TEXTURE_WIDTH = 64;
    private static final int DEFAULT_TEXTURE_HEIGHT = 32;

    private PrJemParser() {
    }

    /**
     * @param location the {@code .jem} location, the base of relative paths
     * @param loader   reads another model file ({@code .jpm}); {@code null} when missing
     */
    public static PrJem parse(JsonObject jem, ResourceLocation location, Function<ResourceLocation, JsonObject> loader) {
        String base = PrPaths.parent(location.getPath());
        String textureText = string(jem, "texture");
        ResourceLocation texture = textureText == null || textureText.isBlank() ? null : resolve(location, base, textureText, ".png");
        int[] size = intArray(jem.get("textureSize"), 2);
        int width = size == null ? DEFAULT_TEXTURE_WIDTH : size[0];
        int height = size == null ? DEFAULT_TEXTURE_HEIGHT : size[1];
        float shadow = number(jem, "shadowSize", number(jem, "shadow_size", -1.0F));
        JsonArray models = jem.getAsJsonArray("models");
        if (models == null) {
            throw new JsonParseException("Missing models");
        }
        Map<String, JsonObject> byId = new HashMap<>();
        List<PrJem.Part> parts = new ArrayList<>();
        for (JsonElement e : models) {
            JsonObject entry = e.getAsJsonObject().deepCopy();
            String baseId = string(entry, "baseId");
            if (baseId != null && byId.containsKey(baseId)) {
                copyMissing(byId.get(baseId), entry);
            }
            String model = string(entry, "model");
            if (model != null) {
                JsonObject external = loader.apply(resolve(location, base, model, ".jpm"));
                if (external == null) {
                    throw new JsonParseException("Model not found: " + model);
                }
                copyMissing(external, entry);
            }
            String id = string(entry, "id");
            if (id != null && !id.isEmpty()) {
                byId.putIfAbsent(id, entry);
            }
            String part = string(entry, "part");
            if (part == null) {
                throw new JsonParseException("Model part not specified, missing 'part'");
            }
            parts.add(part(entry, part, location, base, width, height, true));
        }
        return new PrJem(location, texture, width, height, shadow, List.copyOf(parts));
    }

    private static PrJem.Part part(JsonObject o, String part, ResourceLocation location, String base,
            int parentWidth, int parentHeight, boolean topLevel) {
        int[] ownSize = intArray(o.get("textureSize"), 2);
        int width = ownSize == null ? parentWidth : ownSize[0];
        int height = ownSize == null ? parentHeight : ownSize[1];
        String textureText = string(o, "texture");
        ResourceLocation texture = textureText == null || textureText.isBlank() ? null : resolve(location, base, textureText, ".png");
        String invert = string(o, "invertAxis", "").toLowerCase(Locale.ROOT);
        boolean ix = invert.contains("x");
        boolean iy = invert.contains("y");
        boolean iz = invert.contains("z");
        float[] t = floatArray(o.get("translate"), 3, new float[3]);
        float[] r = floatArray(o.get("rotate"), 3, new float[3]);
        String mirror = string(o, "mirrorTexture", "").toLowerCase(Locale.ROOT);
        List<PrJem.Box> boxes = new ArrayList<>();
        JsonArray boxArray = o.getAsJsonArray("boxes");
        if (boxArray != null) {
            for (JsonElement b : boxArray) {
                boxes.add(box(b.getAsJsonObject(), ix, iy, iz));
            }
        }
        List<PrJem.Part> children = new ArrayList<>();
        JsonObject submodel = o.has("submodel") && o.get("submodel").isJsonObject() ? o.getAsJsonObject("submodel") : null;
        if (submodel != null) {
            children.add(part(submodel, null, location, base, width, height, false));
        }
        JsonArray submodels = o.getAsJsonArray("submodels");
        if (submodels != null) {
            for (JsonElement s : submodels) {
                children.add(part(s.getAsJsonObject(), null, location, base, width, height, false));
            }
        }
        List<Map<String, String>> animations = new ArrayList<>();
        JsonArray animArray = topLevel ? o.getAsJsonArray("animations") : null;
        if (animArray != null) {
            for (JsonElement a : animArray) {
                Map<String, String> assignments = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : a.getAsJsonObject().entrySet()) {
                    JsonElement v = e.getValue();
                    assignments.put(e.getKey().trim(), v.isJsonPrimitive() ? v.getAsString() : v.toString());
                }
                animations.add(assignments);
            }
        }
        float toRad = (float) Math.PI / 180.0F;
        return new PrJem.Part(part, topLevel && bool(o, "attach", false), string(o, "id"), texture, width, height,
                ix ? -t[0] : t[0], iy ? -t[1] : t[1], iz ? -t[2] : t[2],
                (ix ? -r[0] : r[0]) * toRad, (iy ? -r[1] : r[1]) * toRad, (iz ? -r[2] : r[2]) * toRad,
                number(o, "scale", 1.0F), mirror.contains("u"), mirror.contains("v"),
                List.copyOf(boxes), List.copyOf(children), List.copyOf(animations));
    }

    private static PrJem.Box box(JsonObject b, boolean ix, boolean iy, boolean iz) {
        float[] offset = floatArray(b.get("textureOffset"), 2, null);
        float[][] faces = faceUvs(b);
        if (offset == null && faces == null) {
            throw new JsonParseException("Texture offset not specified");
        }
        float[] c = floatArray(b.get("coordinates"), 6, null);
        if (c == null) {
            throw new JsonParseException("Coordinates not specified");
        }
        if (ix) {
            c[0] = -c[0] - c[3];
        }
        if (iy) {
            c[1] = -c[1] - c[4];
        }
        if (iz) {
            c[2] = -c[2] - c[5];
        }
        float grow = number(b, "sizeAdd", 0.0F);
        float gx = grow;
        float gy = grow;
        float gz = grow;
        float[] sizes = floatArray(b.get("sizesAdd"), 3, null);
        if (sizes != null) {
            gx = sizes[0];
            gy = sizes[1];
            gz = sizes[2];
        } else if (b.has("sizeAddX") || b.has("sizeAddY") || b.has("sizeAddZ")) {
            gx = number(b, "sizeAddX", 0.0F);
            gy = number(b, "sizeAddY", 0.0F);
            gz = number(b, "sizeAddZ", 0.0F);
        }
        if (faces != null) {
            return new PrJem.Box(c[0], c[1], c[2], c[3], c[4], c[5], gx, gy, gz, 0.0F, 0.0F, faces);
        }
        // OptiFine truncates the sizes of texture-offset boxes to whole pixels.
        return new PrJem.Box(c[0], c[1], c[2], (int) c[3], (int) c[4], (int) c[5], gx, gy, gz, offset[0], offset[1], null);
    }

    private static float[][] faceUvs(JsonObject b) {
        float[][] uvs = {
                floatArray(b.get("uvDown"), 4, null), floatArray(b.get("uvUp"), 4, null),
                floatArray(b.get("uvNorth"), 4, null), floatArray(b.get("uvSouth"), 4, null),
                floatArray(b.get("uvWest"), 4, null), floatArray(b.get("uvEast"), 4, null)};
        String[] legacy = {null, null, "uvFront", "uvBack", "uvLeft", "uvRight"};
        boolean any = false;
        for (int i = 0; i < uvs.length; i++) {
            if (uvs[i] == null && legacy[i] != null) {
                uvs[i] = floatArray(b.get(legacy[i]), 4, null);
            }
            any |= uvs[i] != null;
        }
        return any ? uvs : null;
    }

    /** OptiFine {@code CustomEntityModelParser.getResourceLocation}, in the model's namespace. */
    static ResourceLocation resolve(ResourceLocation model, String base, String path, String extension) {
        String p = path.trim();
        if (!p.endsWith(extension)) {
            p += extension;
        }
        if (p.indexOf(':') < 0) {
            if (p.indexOf('/') < 0) {
                p = base + "/" + p;
            } else if (p.startsWith("./")) {
                p = base + "/" + p.substring(2);
            } else if (p.startsWith("~/")) {
                p = PrPaths.OPTIFINE + p.substring(2);
            }
            ResourceLocation loc = ResourceLocation.tryBuild(model.getNamespace(), p);
            if (loc == null) {
                throw new JsonParseException("Invalid path: " + path);
            }
            return PrPaths.logical(loc);
        }
        ResourceLocation loc = ResourceLocation.tryParse(p);
        if (loc == null) {
            throw new JsonParseException("Invalid path: " + path);
        }
        return PrPaths.logical(loc);
    }

    private static void copyMissing(JsonObject from, JsonObject to) {
        for (Map.Entry<String, JsonElement> e : from.entrySet()) {
            if (!e.getKey().equals("id") && !to.has(e.getKey())) {
                to.add(e.getKey(), e.getValue().deepCopy());
            }
        }
    }

    // ------------------------------------------------------------------ JSON helpers

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String string(JsonObject o, String key, String def) {
        String s = string(o, key);
        return s == null ? def : s;
    }

    private static float number(JsonObject o, String key, float def) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsFloat();
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsBoolean();
    }

    private static float[] floatArray(JsonElement e, int length, float[] def) {
        if (e == null || e.isJsonNull()) {
            return def;
        }
        JsonArray a = e.getAsJsonArray();
        if (a.size() != length) {
            throw new JsonParseException("Wrong array length: " + a.size() + ", should be: " + length + ", array: " + a);
        }
        float[] out = new float[length];
        for (int i = 0; i < length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    private static int[] intArray(JsonElement e, int length) {
        float[] f = floatArray(e, length, null);
        if (f == null) {
            return null;
        }
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            out[i] = (int) f[i];
        }
        return out;
    }
}
