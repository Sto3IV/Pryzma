package net.pryzma.shader;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.pryzma.core.res.PrProperties;

/**
 * Labels a shader pack provides in {@code shaders/lang/<language>.lang} (OptiFine's keys:
 * {@code option.X}, {@code option.X.comment}, {@code value.X.V}, {@code screen.X},
 * {@code profile.X}, {@code prefix.X}, {@code suffix.X}), in the game's language with English as
 * the fallback.
 */
public final class PrShaderLang {
    private static PrShaderPack cachedPack;
    private static String cachedLanguage;
    private static Map<String, String> strings = Map.of();
    private static Map<String, String> english = Map.of();

    private PrShaderLang() {
    }

    private static void ensure(PrShaderPack pack) {
        String language = Minecraft.getInstance().options.languageCode;
        if (pack == cachedPack && language.equals(cachedLanguage)) {
            return;
        }
        cachedPack = pack;
        cachedLanguage = language;
        strings = read(pack, language);
        english = language.equals("en_us") ? strings : read(pack, "en_us");
    }

    private static Map<String, String> read(PrShaderPack pack, String language) {
        if (pack == null) {
            return Map.of();
        }
        String text = pack.read("shaders/lang/" + language + ".lang");
        return text == null ? Map.of()
                : PrProperties.parse(ResourceLocation.withDefaultNamespace("shaders/lang/" + language + ".lang"), text).asMap();
    }

    /** The pack's text for {@code key}, or {@code fallback}. */
    public static String get(PrShaderPack pack, String key, String fallback) {
        ensure(pack);
        String s = strings.get(key);
        if (s == null) {
            s = english.get(key);
        }
        return s == null ? fallback : s;
    }

    /** The shown value of an option: switches as ON/OFF, others by {@code value.X.V}, with prefix and suffix. */
    public static String value(PrShaderPack pack, PrShaderOption option, String value) {
        if (option.isBoolean()) {
            return get(pack, "value." + option.name() + "." + value, "true".equals(value) ? "§aON" : "§cOFF");
        }
        return get(pack, "prefix." + option.name(), "") + get(pack, "value." + option.name() + "." + value, value)
                + get(pack, "suffix." + option.name(), "");
    }
}
