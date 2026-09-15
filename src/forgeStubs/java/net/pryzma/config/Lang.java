package net.pryzma.config;

import net.minecraft.network.chat.MutableComponent;

/**
 * Compatibility forwarder. Two patched option-label branches in
 * {@code Options.getKeyBindingPryzma} invoke {@code net.pryzma.config.Lang}
 * instead of {@code net.pryzma.Lang}. The GAME overlay copies every
 * {@code srg/net/pryzma/**} class, so this stub is loadable on the same
 * module as the real translator.
 */
public final class Lang {

    private Lang() {}

    public static String get(String key) {
        return net.pryzma.Lang.get(key);
    }

    public static String get(String key, String def) {
        return net.pryzma.Lang.get(key, def);
    }

    public static MutableComponent getComponent(String key) {
        return net.pryzma.Lang.getComponent(key);
    }

    public static String getOn() {
        return net.pryzma.Lang.getOn();
    }

    public static String getOff() {
        return net.pryzma.Lang.getOff();
    }

    public static String getFast() {
        return net.pryzma.Lang.getFast();
    }

    public static String getFancy() {
        return net.pryzma.Lang.getFancy();
    }

    public static String getDefault() {
        return net.pryzma.Lang.getDefault();
    }
}
