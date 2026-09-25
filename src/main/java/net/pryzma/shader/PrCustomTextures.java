package net.pryzma.shader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Textures a pack binds in place of a sampler ({@code texture.<stage>.<sampler>=<path>}, and
 * {@code texture.noise} for {@code noisetex} everywhere): an image of the pack, relative to
 * {@code shaders/} with an optional {@code .mcmeta} for blur and clamp, or a game texture named
 * {@code namespace:path}.
 */
final class PrCustomTextures {
    private final Map<String, Integer> textures = new HashMap<>();
    private final List<Integer> owned = new ArrayList<>();

    static PrCustomTextures load(PrShaderPack pack, PrShaderProperties properties, Consumer<String> warn) {
        PrCustomTextures out = new PrCustomTextures();
        properties.withPrefix("texture.").forEach((key, value) -> {
            String slot = slot(key);
            if (slot == null) {
                warn.accept("Invalid texture." + key);
                return;
            }
            String[] parts = value.trim().split("\\s+");
            if (parts.length > 1) {
                warn.accept("texture." + key + ": raw textures are not supported");
                return;
            }
            int id = out.texture(pack, parts[0], warn);
            if (id > 0) {
                out.bind(slot, id);
            }
        });
        return out;
    }

    /** The slot a {@code texture.<key>} setting fills, as {@code stage:sampler}, or {@code null} when malformed. */
    static String slot(String key) {
        if (key.equals("noise")) {
            return "*:noisetex";
        }
        int dot = key.indexOf('.');
        return dot <= 0 || dot == key.length() - 1 ? null : key.substring(0, dot) + ":" + canonical(key.substring(dot + 1));
    }

    void bind(String slot, int texture) {
        textures.put(slot, texture);
    }

    /** {@code gaux1} → {@code colortex4}: buffers by their numbered name, other samplers as written. */
    static String canonical(String sampler) {
        int buffer = PrShaderPipeline.bufferIndex(sampler);
        return buffer >= 0 ? "colortex" + buffer : sampler;
    }

    /** The texture bound for {@code sampler} in {@code stage} ({@code gbuffers}, {@code composite}...), or -1. */
    int lookup(String stage, String sampler) {
        String name = canonical(sampler);
        Integer id = textures.get(stage + ":" + name);
        if (id == null) {
            id = textures.get("*:" + name);
        }
        return id == null ? -1 : id;
    }

    boolean isEmpty() {
        return textures.isEmpty();
    }

    private int texture(PrShaderPack pack, String path, Consumer<String> warn) {
        if (path.indexOf(':') > 0) {
            ResourceLocation location = ResourceLocation.tryParse(path);
            if (location == null) {
                warn.accept("Invalid texture " + path);
                return -1;
            }
            return Minecraft.getInstance().getTextureManager().getTexture(location).getId();
        }
        String file = "shaders/" + (path.startsWith("/") ? path.substring(1) : path);
        boolean blur = false;
        boolean clamp = false;
        String meta = pack.read(file + ".mcmeta");
        if (meta != null) {
            try {
                JsonElement json = JsonParser.parseString(meta);
                if (json.isJsonObject() && json.getAsJsonObject().has("texture")) {
                    JsonObject t = json.getAsJsonObject().getAsJsonObject("texture");
                    blur = t.has("blur") && t.get("blur").getAsBoolean();
                    clamp = t.has("clamp") && t.get("clamp").getAsBoolean();
                }
            } catch (RuntimeException e) {
                warn.accept("Invalid " + file + ".mcmeta: " + e.getMessage());
            }
        }
        // The stream overload decodes from a malloc'd buffer; the byte[] one copies the file onto
        // LWJGL's 64 KiB MemoryStack and throws OutOfMemoryError for any larger image.
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(pack.readBytes(file)))) {
            int id = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(id, image.getWidth(), image.getHeight());
            image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), blur, clamp, false, false);
            owned.add(id);
            return id;
        } catch (IOException | RuntimeException e) {
            warn.accept("Cannot load texture " + file + ": " + e.getMessage());
            return -1;
        }
    }

    void release() {
        owned.forEach(GlStateManager::_deleteTexture);
        owned.clear();
        textures.clear();
    }
}
