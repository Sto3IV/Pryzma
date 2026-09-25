package net.pryzma.core.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

class PrNbtMatcherTest {
    private static CompoundTag item() {
        CompoundTag display = new CompoundTag();
        display.putString("Name", "{\"text\":\"Azure \",\"extra\":[{\"text\":\"Sabre\"}]}");
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf("\"first line\""));
        lore.add(StringTag.valueOf("\"second line\""));
        display.put("Lore", lore);
        display.putInt("color", 0x1A2B3C);
        CompoundTag root = new CompoundTag();
        root.put("display", display);
        root.putInt("Damage", 12);
        root.putString("Owner", "Steve");
        return root;
    }

    @Test
    void exactTextFlattensJsonComponents() {
        assertTrue(PrNbtMatcher.parse("display.Name", "Azure Sabre").matches(item()), "text of all \"text\" fields");
        assertFalse(PrNbtMatcher.parse("display.Name", "azure sabre").matches(item()), "exact is case sensitive");
        assertTrue(PrNbtMatcher.parse("Owner", "Steve").matches(item()));
    }

    @Test
    void wildcardsAndRegexes() {
        assertTrue(PrNbtMatcher.parse("display.Name", "pattern:Azure*").matches(item()));
        assertTrue(PrNbtMatcher.parse("display.Name", "ipattern:*SABRE").matches(item()));
        assertTrue(PrNbtMatcher.parse("display.Name", "ipattern:?zure sab?e").matches(item()));
        assertFalse(PrNbtMatcher.parse("display.Name", "pattern:*sabre").matches(item()));
        assertTrue(PrNbtMatcher.parse("display.Name", "regex:Az.* S\\w+").matches(item()));
        assertTrue(PrNbtMatcher.parse("display.Name", "iregex:azure sabre").matches(item()));
        assertFalse(PrNbtMatcher.parse("display.Name", "regex:Azure").matches(item()), "regex must match the whole text");
    }

    @Test
    void rangesExistenceAndNegation() {
        assertTrue(PrNbtMatcher.parse("Damage", "range:10-20").matches(item()));
        assertFalse(PrNbtMatcher.parse("Damage", "range:13-20").matches(item()));
        assertTrue(PrNbtMatcher.parse("Damage", "!range:13-20").matches(item()));
        assertTrue(PrNbtMatcher.parse("display.Lore", "exists:true").matches(item()));
        assertTrue(PrNbtMatcher.parse("display.Missing", "exists:false").matches(item()));
        assertFalse(PrNbtMatcher.parse("display.Missing", "pattern:*").matches(item()), "pattern:* means exists");
        assertFalse(PrNbtMatcher.parse("Damage", "exists:maybe").isValid());
    }

    @Test
    void listsByIndexAnyChildAndCount() {
        assertTrue(PrNbtMatcher.parse("display.Lore.1", "second line").matches(item()));
        assertFalse(PrNbtMatcher.parse("display.Lore.2", "exists:true").matches(item()));
        assertTrue(PrNbtMatcher.parse("display.Lore.*", "first line").matches(item()));
        assertTrue(PrNbtMatcher.parse("display.Lore.count", "2").matches(item()));
        assertTrue(PrNbtMatcher.parse("*.Name", "ipattern:azure*").matches(item()), "* walks compound children");
    }

    @Test
    void hexColorsAndRawValues() {
        assertTrue(PrNbtMatcher.parse("display.color", "#1a2b3c").matches(item()), "int shown as #rrggbb");
        assertTrue(PrNbtMatcher.parse("display.color", String.valueOf(0x1A2B3C)).matches(item()));
        assertTrue(PrNbtMatcher.parse("Damage", "raw:12").matches(item()), "raw compares the SNBT text");
        CompoundTag withString = new CompoundTag();
        withString.putString("s", "a\"b");
        assertTrue(PrNbtMatcher.parse("s", "a\\\"b").matches(withString), "values are Java-unescaped");
    }

    @Test
    void plainValueMatchingForEntityNames() {
        PrNbtMatcher name = PrNbtMatcher.parse("name", "ipattern:*dinnerbone*");
        assertTrue(name.matchesValue("Dinnerbone"));
        assertFalse(name.matchesValue(null));
        assertEquals("", PrNbtMatcher.mergedJsonText("{\"color\":\"red\"}"));
        assertEquals("a\nb", PrNbtMatcher.mergedJsonText("{\"text\":\"a\\nb\"}"));
        assertTrue(PrNbtMatcher.wildcard("abcabc", "*bc*c"));
        assertFalse(PrNbtMatcher.wildcard("abc", "a?"));
        assertTrue(PrNbtMatcher.wildcard("", "*"));
    }
}
