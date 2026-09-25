package net.pryzma.core.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class PrPropertiesTest {
    private static Map<String, String> parse(String text) {
        return PrProperties.parse(ResourceLocation.withDefaultNamespace("optifine/x.properties"), text).asMap();
    }

    @Test
    void keysRunningIntoEqualsKeepTheirColons() {
        Map<String, String> p = parse("""
                components.minecraft:fireworks.flight_duration=1
                components.minecraft:fireworks.explosions.0.shape = burst
                """);
        assertEquals(Map.of("components.minecraft:fireworks.flight_duration", "1",
                "components.minecraft:fireworks.explosions.0.shape", "burst"), p);
    }

    @Test
    void everythingElseReadsAsJavaProperties() {
        assertEquals(Map.of("texture", "foo"), parse("texture:foo\n"), "a colon still separates a line without =");
        assertEquals(Map.of("name", "Bob = x"), parse("name: Bob = x\n"), "a colon ending the key token separates");
        assertEquals(Map.of("a:b", "c"), parse("a\\:b=c\n"), "escaped colons stay escaped");
        assertEquals(Map.of("a", "xb:c=d"), parse("a=x\\\n  b:c=d\n"), "continuation lines are values");
        assertEquals(Map.of("k", "v"), parse("# x:y=z\n! p:q=r\nk=v\n"), "comments are skipped");
        assertEquals(Map.of("a", "1"), parse("﻿a=1\n"), "byte order mark");
        assertEquals(Map.of("a", "1"), parse("ï»¿a=1\n"), "byte order mark read as ISO-8859-1");
    }

    @Test
    void utf8IsDetectedAndLatin1StaysTheFallback() {
        String cyrillic = "name.1=Гоша";
        assertEquals(cyrillic, PrResources.decode(cyrillic.getBytes(StandardCharsets.UTF_8)));
        byte[] latin1 = {'n', '=', (byte) 0xE9};
        assertEquals("n=é", PrResources.decode(latin1));
        assertFalse(parse(PrResources.decode(cyrillic.getBytes(StandardCharsets.UTF_8))).isEmpty());
    }
}
