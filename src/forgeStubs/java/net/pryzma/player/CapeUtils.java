package net.pryzma.player;

/*
 * Ranni: this code is so bad that my eyes are bleeding.
 */

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.pryzma.Config;

public class CapeUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final Pattern PATTERN_USERNAME = Pattern.compile("[a-zA-Z0-9_]+");

    private static final Map<UUID, ResourceLocation> CAPE_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING_REQUESTS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> NO_CAPE_PLAYERS = ConcurrentHashMap.newKeySet();

    private static final ExecutorService CAPE_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "OptiFine-Cape-Downloader");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    public static void downloadCape(AbstractClientPlayer player) {
        if (player == null) {
            return;
        }
        downloadCape(player.getGameProfile(), null);
    }

    public static void downloadCape(GameProfile profile, Consumer<ResourceLocation> callback) {
        if (profile == null || profile.getName() == null || profile.getId() == null) {
            return;
        }
        String username = profile.getName();
        if (username.isEmpty() || !PATTERN_USERNAME.matcher(username).matches()) {
            return;
        }
        UUID uuid = profile.getId();
        ResourceLocation cached = CAPE_CACHE.get(uuid);
        if (cached != null) {
            if (callback != null) {
                callback.accept(cached);
            }
            return;
        }
        if (NO_CAPE_PLAYERS.contains(uuid) || !PENDING_REQUESTS.add(uuid)) {
            return;
        }

        CAPE_EXECUTOR.submit(() -> {
            try {
                String url = "http://s.optifine.net/capes/" + username + ".png";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                PENDING_REQUESTS.remove(uuid);

                if (response.statusCode() == 200) {
                    try (InputStream in = response.body()) {
                        NativeImage rawImg = NativeImage.read(in);
                        NativeImage paddedImg = resizeCape(rawImg);
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> {
                            try {
                                DynamicTexture dyn = new DynamicTexture(paddedImg);
                                ResourceLocation loc = mc.getTextureManager().register("optifine-cape-" + uuid.toString().replace("-", ""), dyn);
                                CAPE_CACHE.put(uuid, loc);
                                if (callback != null) {
                                    callback.accept(loc);
                                }
                            } catch (Throwable t) {
                                LOGGER.warn("Failed to register OptiFine cape for {}: {}", username, t.getMessage());
                            }
                        });
                    }
                } else {
                    NO_CAPE_PLAYERS.add(uuid);
                }
            } catch (Throwable t) {
                PENDING_REQUESTS.remove(uuid);
                NO_CAPE_PLAYERS.add(uuid);
            }
        });
    }

    public static NativeImage parseCape(NativeImage img) {
        return resizeCape(img);
    }

    public static NativeImage resizeCape(NativeImage image) {
        int imageWidth = 64;
        int imageHeight = 32;
        int srcWidth = image.getWidth();
        int srcHeight = image.getHeight();
        if (srcWidth <= 0 || srcHeight <= 0) {
            return image;
        }
        while (imageWidth < srcWidth || imageHeight < srcHeight) {
            imageWidth *= 2;
            imageHeight *= 2;
        }
        NativeImage imgNew = new NativeImage(imageWidth, imageHeight, true);
        for (int x = 0; x < srcWidth; x++) {
            for (int y = 0; y < srcHeight; y++) {
                imgNew.setPixelRGBA(x, y, image.getPixelRGBA(x, y));
            }
        }
        image.close();
        return imgNew;
    }

    public static boolean isElytraCape(NativeImage img, NativeImage elytraImg) {
        return false;
    }

    public static void reloadCape(AbstractClientPlayer player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getGameProfile().getId();
        if (uuid != null) {
            reloadCape(uuid);
            downloadCape(player);
        }
    }

    public static void reloadCape(UUID uuid) {
        if (uuid == null) {
            return;
        }
        CAPE_CACHE.remove(uuid);
        NO_CAPE_PLAYERS.remove(uuid);
        PENDING_REQUESTS.remove(uuid);
    }

    public static ResourceLocation getCape(UUID uuid) {
        return uuid != null ? CAPE_CACHE.get(uuid) : null;
    }

    public static PlayerSkin patchPlayerSkin(GameProfile profile, PlayerSkin original) {
        if (profile == null || !Config.isShowCapes()) {
            return original;
        }
        UUID uuid = profile.getId();
        if (uuid == null) {
            return original;
        }
        ResourceLocation ofCape = CAPE_CACHE.get(uuid);
        if (ofCape == null) {
            if (!NO_CAPE_PLAYERS.contains(uuid) && !PENDING_REQUESTS.contains(uuid)) {
                downloadCape(profile, null);
            }
            return original;
        }
        return new PlayerSkin(
                original.texture(),
                original.textureUrl(),
                ofCape,
                original.elytraTexture(),
                original.model(),
                original.secure());
    }
}
