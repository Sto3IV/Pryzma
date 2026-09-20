package net.pryzma.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

/**
 * Restores Epic Fight's target-outline recolor without a compile-time {@code yesman.epicfight}
 * dependency. Invoked at HEAD of {@code EntityRenderDispatcher.render}; no-ops when the mod is
 * absent, the entity is null, or any resolution/runtime call fails (the hook then disables itself).
 */
public final class EpicFightOutline {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final String MOD_ID = "epicfight";
    private static final String CAMERA_API = "yesman.epicfight.api.client.camera.EpicFightCameraAPI";
    private static final String CLIENT_CONFIG = "yesman.epicfight.config.ClientConfig";
    private static final String SHOULD_HIGHLIGHT = "shouldHighlightTarget";
    private static final String PACKED_COLOR = "packedTargetOutlineColor";

    private static final Object LOCK = new Object();

    private static volatile boolean disabled;
    private static volatile boolean resolved;
    private static volatile boolean present;
    private static volatile Boolean epicFightLoaded;

    private static MethodHandle getInstance;
    private static MethodHandle shouldHighlightTarget;
    private static MethodHandle packedTargetOutlineColor;

    private EpicFightOutline() {}

    public static void onRenderEntity(Entity entity) {
        if (disabled || entity == null) {
            return;
        }
        if (!epicFightPresent()) {
            return;
        }
        try {
            Object api = getInstance.invoke();
            if (api == null) {
                return;
            }
            boolean highlight = (boolean) shouldHighlightTarget.invoke(api, entity);
            if (!highlight) {
                return;
            }
            int c = (int) packedTargetOutlineColor.invoke();
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            mc.renderBuffers().outlineBufferSource().setColor(
                    (c >> 16) & 255, (c >> 8) & 255, c & 255, 255);
        } catch (Throwable t) {
            disable("runtime", t);
        }
    }

    private static boolean epicFightPresent() {
        if (disabled) {
            return false;
        }
        if (!resolved) {
            resolve();
        }
        return present && !disabled;
    }

    private static void resolve() {
        synchronized (LOCK) {
            if (resolved) {
                return;
            }
            resolved = true;
            if (!isEpicFightLoaded()) {
                present = false;
                return;
            }
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Class<?> camera = Class.forName(CAMERA_API);
                Class<?> config = Class.forName(CLIENT_CONFIG);
                getInstance = lookup.findStatic(camera, "getInstance", MethodType.methodType(camera));
                shouldHighlightTarget = lookup.findVirtual(
                        camera, SHOULD_HIGHLIGHT, MethodType.methodType(boolean.class, Entity.class));
                packedTargetOutlineColor = lookup.findStaticGetter(config, PACKED_COLOR, int.class);
                present = true;
            } catch (Throwable t) {
                present = false;
                disable("resolve", t);
            }
        }
    }

    static boolean isEpicFightLoaded() {
        Boolean cached = epicFightLoaded;
        if (cached != null) {
            return cached;
        }
        try {
            ModList list = ModList.get();
            if (list != null) {
                boolean loaded = list.isLoaded(MOD_ID);
                epicFightLoaded = loaded;
                return loaded;
            }
        } catch (Throwable ignored) {
        }
        try {
            var loading = FMLLoader.getLoadingModList();
            if (loading != null) {
                boolean loaded = loading.getModFileById(MOD_ID) != null;
                epicFightLoaded = loaded;
                return loaded;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void disable(String phase, Throwable t) {
        disabled = true;
        present = false;
        LOGGER.warn("EpicFightOutline {} failed; disabling hook: {}", phase, String.valueOf(t));
    }
}
