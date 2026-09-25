package net.pryzma.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.pryzma.core.match.PrRangeList;
import net.pryzma.core.res.PrProperties;

class PrCitRuleTest {
    /** An item stack as a rule reads it. */
    record Stack(ResourceLocation item, int damage, int maxDamage, int count, List<ResourceLocation> ids,
            List<Integer> levels, CompoundTag all, boolean offHand) implements PrCitItem {
        static Stack of(String id) {
            return new Stack(mc(id), 0, 0, 1, List.of(), List.of(), new CompoundTag(), false);
        }

        Stack with(String component, Tag value) {
            CompoundTag copy = all.copy();
            copy.put(component, value);
            return new Stack(item, damage, maxDamage, count, ids, levels, copy, offHand);
        }

        /** A custom name as the game stores it: the component's JSON text in a string tag. */
        Stack named(String json) {
            return with("minecraft:custom_name", StringTag.valueOf(json));
        }

        Stack damaged(int value, int max) {
            return new Stack(item, value, max, count, ids, levels, all, offHand);
        }

        Stack enchanted(String id, int level) {
            List<ResourceLocation> i = new ArrayList<>(ids);
            List<Integer> l = new ArrayList<>(levels);
            i.add(mc(id));
            l.add(level);
            return new Stack(item, damage, maxDamage, count, i, l, all, offHand);
        }

        Stack inOffHand() {
            return new Stack(item, damage, maxDamage, count, ids, levels, all, true);
        }

        @Override
        public int enchantments() {
            return ids.size();
        }

        @Override
        public ResourceLocation enchantment(int index) {
            return ids.get(index);
        }

        @Override
        public int enchantmentLevel(int index) {
            return levels.get(index);
        }

        @Override
        public Tag components(String id) {
            if (id.equals("*")) {
                return all.copy();
            }
            CompoundTag root = new CompoundTag();
            if (all.contains(id)) {
                root.put(id, all.get(id));
            }
            return root;
        }
    }

    private final List<String> warnings = new ArrayList<>();

    private static ResourceLocation mc(String id) {
        return ResourceLocation.parse(id);
    }

    private PrCitRule parse(String path, String text) {
        return PrCitRule.parse(PrProperties.parse(mc("optifine/cit/" + path), text), warnings::add);
    }

    @Test
    void namedModelRuleOfTheTwilightPack() {
        PrCitRule rule = parse("weapons/amethyst_greatblade.properties", """
                type=item
                matchItems=iron_sword wooden_sword
                nbt.display.Name=ipattern:*amethyst greatblade*
                model=./amethyst_greatblade
                weight=2
                """);
        assertNotNull(rule, warnings::toString);
        assertEquals(List.of(mc("iron_sword"), mc("wooden_sword")), rule.items);
        assertEquals(mc("optifine/cit/weapons/amethyst_greatblade"), rule.model);
        assertNull(rule.texture, "a rule with a model takes no texture from its file name");
        assertEquals(2, rule.weight);

        Stack sword = Stack.of("iron_sword");
        assertTrue(rule.matches(sword.named("\"The Amethyst Greatblade\"")), "plain name, any case");
        assertTrue(rule.matches(sword.named("{\"italic\":false,\"text\":\"Amethyst GREATBLADE\"}")), "styled name");
        assertFalse(rule.matches(sword.named("\"Ruby Blade\"")));
        assertFalse(rule.matches(sword), "an unnamed item has no custom_name component");
    }

    @Test
    void subModelsSubTexturesAndEnchantments() {
        PrCitRule shield = parse("shields/azure_sabre_shield.properties", """
                matchItems=minecraft:shield
                nbt.display.Name=ipattern:*azure sabre*
                model=./azure_sabre
                model.shield_blocking=./azure_sabre_blocking
                """);
        assertEquals(Map.of("shield_blocking", mc("optifine/cit/shields/azure_sabre_blocking")), shield.models);

        PrCitRule rod = parse("0_fishing_rod/properties/lure.properties", """
                items=fishing_rod
                texture=optifine/cit/0_fishing_rod/lure
                texture.fishing_rod_cast=optifine/cit/0_fishing_rod/lure_cast
                enchantments=lure
                weight=-9
                """);
        assertEquals(mc("optifine/cit/0_fishing_rod/lure.png"), rod.texture, "a rooted name is not taken relative");
        assertEquals(mc("optifine/cit/0_fishing_rod/lure_cast.png"), rod.textures.get("fishing_rod_cast"));
        assertEquals(mc("optifine/cit/0_fishing_rod/lure"), PrCitRule.spriteOf(rod.texture));
        assertEquals(-9, rod.weight);
        assertEquals(Set.of(mc("lure")), rod.enchantments);
        assertTrue(rod.matches(Stack.of("fishing_rod").enchanted("lure", 2)));
        assertFalse(rod.matches(Stack.of("fishing_rod").enchanted("luck_of_the_sea", 3)));
    }

    @Test
    void componentKeysKeepTheirColonAndTextureComesFromTheFileName() {
        PrCitRule rule = parse("burst/rocket1.properties", """
                type=item
                items=firework_rocket
                components.minecraft:fireworks.flight_duration=1
                components.minecraft:fireworks.explosions.0.shape=burst
                """);
        assertNotNull(rule, warnings::toString);
        assertEquals(2, rule.components.length, "both conditions survive the colon in their keys");
        assertEquals(mc("optifine/cit/burst/rocket1.png"), rule.texture);

        Stack rocket = Stack.of("firework_rocket").with("minecraft:fireworks", fireworks(1, "burst"));
        assertTrue(rule.matches(rocket));
        assertFalse(rule.matches(Stack.of("firework_rocket").with("minecraft:fireworks", fireworks(2, "burst"))));
        assertFalse(rule.matches(Stack.of("firework_rocket").with("minecraft:fireworks", fireworks(1, "star"))));

        PrCitRule byName = parse("tools/diamond_sword.properties", "texture=./shiny\n");
        assertEquals(List.of(mc("diamond_sword")), byName.items, "items default to the file name");
        assertEquals(mc("optifine/cit/tools/shiny.png"), byName.texture);
    }

    private static CompoundTag fireworks(int flight, String shape) {
        CompoundTag explosion = new CompoundTag();
        explosion.putString("shape", shape);
        ListTag explosions = new ListTag();
        explosions.add(explosion);
        CompoundTag tag = new CompoundTag();
        tag.put("flight_duration", ByteTag.valueOf((byte) flight));
        tag.put("explosions", explosions);
        return tag;
    }

    @Test
    void rangesFollowOptiFine() {
        PrRangeList upTo = PrCitRule.parseRanges("-5", warnings::add);
        assertTrue(upTo.contains(0) && upTo.contains(5) && !upTo.contains(6), "-5 is 0..5");
        PrRangeList from = PrCitRule.parseRanges("5-", warnings::add);
        assertTrue(from.contains(65535) && !from.contains(65536) && !from.contains(4), "5- is 5..65535");
        assertTrue(PrCitRule.parseRanges("3-1", warnings::add).contains(2), "reversed bounds swap");
        assertTrue(warnings.isEmpty());
        assertNull(PrCitRule.parseRanges("1,2", warnings::add), "only spaces separate");
        assertNull(PrCitRule.parseRanges("1-2-3", warnings::add));
        assertEquals(2, warnings.size());

        PrCitRule percent = parse("a/pick.properties", "items=iron_pickaxe\ntexture=x\ndamage=50%-100%\n");
        assertTrue(percent.matches(Stack.of("iron_pickaxe").damaged(125, 250)));
        assertFalse(percent.matches(Stack.of("iron_pickaxe").damaged(100, 250)));
        PrCitRule masked = parse("a/masked.properties", "items=iron_pickaxe\ntexture=x\ndamage=2\ndamageMask=3\n");
        assertTrue(masked.matches(Stack.of("iron_pickaxe").damaged(6, 250)), "6 & 3 is 2");
    }

    @Test
    void enchantmentsAcceptLegacyIdsAndLevelsCheckEveryEnchantment() {
        PrCitRule rule = parse("a/sharp.properties", "items=iron_sword\ntexture=x\nenchantmentIDs=16 mending\nenchantmentLevels=3-\n");
        assertEquals(Set.of(mc("sharpness"), mc("mending")), rule.enchantments);
        // OptiFine compares the levels of all enchantments, so unbreaking III satisfies 3- here.
        assertTrue(rule.matches(Stack.of("iron_sword").enchanted("sharpness", 1).enchanted("unbreaking", 3)));
        assertFalse(rule.matches(Stack.of("iron_sword").enchanted("sharpness", 1)));
        assertFalse(rule.matches(Stack.of("iron_sword").enchanted("unbreaking", 3)), "no named enchantment");
    }

    @Test
    void handAndInvalidRules() {
        PrCitRule off = parse("a/off.properties", "items=torch\ntexture=x\nhand=off\n");
        assertTrue(off.matches(Stack.of("torch").inOffHand()));
        assertFalse(off.matches(Stack.of("torch")));

        assertRejected("type=bogus\nitems=torch\ntexture=x\n", "Unknown method");
        assertRejected("items=torch\ntexture=x\nnbt.Foo=bar\n", "Invalid NBT check");
        assertRejected("type=armor\nitems=diamond_chestplate\n", "No texture or model");
        assertRejected("type=enchantment\ntexture=x\n", "No enchantmentIDs");

        PrCitRule both = parse("a/both.properties", "items=torch\ntexture=x\ncomponents.~custom_name=a\nnbt.Foo=bar\n");
        assertNotNull(both, "components win and nbt keys are then ignored");
        assertEquals("minecraft:custom_name", both.components[0].head());
        PrCitRule lore = parse("a/lore.properties", "items=torch\ntexture=x\nnbt.display.Lore.0=pattern:*old*\n");
        assertEquals("minecraft:lore", lore.components[0].head());
    }

    private void assertRejected(String text, String reason) {
        warnings.clear();
        assertNull(parse("a/rejected.properties", text), text);
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith(reason)), () -> reason + " not in " + warnings);
    }

    @Test
    void rulesSortByLayerThenWeightThenPath() {
        PrCitRule a = parse("a/a.properties", "items=torch\ntexture=x\nweight=1\n");
        PrCitRule b = parse("a/b.properties", "items=torch\ntexture=x\nweight=5\n");
        PrCitRule c = parse("a/c.properties", "items=torch\ntexture=x\nweight=100\nlayer=1\n");
        PrCitRule d = parse("a/d.properties", "items=torch\ntexture=x\nweight=1\n");
        List<PrCitRule> rules = new ArrayList<>(List.of(c, d, a, b));
        rules.sort(PrCitRule.ORDER);
        assertEquals(List.of(b, a, d, c), rules);
    }

    @Test
    void legacyPotionValuesAndImageRules() {
        assertEquals(8229, PrCitPotions.damage("minecraft:strong_healing", false));
        assertEquals(8229 | PrCitPotions.SPLASH, PrCitPotions.damage("minecraft:strong_healing", true));
        assertEquals(0, PrCitPotions.damage("minecraft:water", false));
        assertEquals(0, PrCitPotions.damage(null, false));
        assertEquals(-1, PrCitPotions.damage("somemod:tea", false));

        PrProperties image = PrCitPotions.imageRule(mc("optifine/cit/potion/splash/heal.png"), "splash");
        PrCitRule rule = PrCitRule.parse(image, warnings::add);
        assertNotNull(rule, warnings::toString);
        assertEquals(List.of(mc("splash_potion")), rule.items);
        assertTrue(rule.matches(Stack.of("splash_potion").damaged(PrCitPotions.damage("minecraft:strong_healing", true), 0)));
        assertFalse(rule.matches(Stack.of("splash_potion").damaged(PrCitPotions.damage("minecraft:poison", true), 0)));
        assertEquals(List.of(mc("item/potion_overlay"), mc("optifine/cit/potion/splash/heal")), PrCitModels.layers(rule),
                "the tinted overlay stays under the custom bottle");

        assertNull(PrCitPotions.imageRule(mc("optifine/cit/potion/normal/heal_n.png"), "normal"));
        assertNull(PrCitPotions.imageRule(mc("optifine/cit/potion/normal/unknown.png"), "normal"));
        PrCitRule bottle = PrCitRule.parse(PrCitPotions.imageRule(mc("optifine/cit/potion/normal/empty.png"), "normal"), warnings::add);
        assertEquals(List.of(mc("glass_bottle")), bottle.items);

        PrCitRule leather = parse("armor/red_cap.properties", "items=leather_helmet\ntexture=./red\n");
        assertEquals(List.of(mc("optifine/cit/armor/red"), mc("item/leather_helmet_overlay")), PrCitModels.layers(leather));
    }

    @Test
    void modelReferencesResolveBesideTheModel() {
        JsonObject json = JsonParser.parseString("""
                {"parent": "./ender_golem_q",
                 "textures": {"0": "./item/wooden_tonfa", "1": "#0", "2": "minecraft:block/dragon",
                              "3": "mcpatcher/cit/x/y.png", "4": "textures/item/stick"},
                 "overrides": [{"predicate": {"pulling": 1}, "model": "./bow_pulling_0"}]}
                """).getAsJsonObject();
        PrCitModelJson.resolve(json, "optifine/cit/weapons");
        JsonObject textures = json.getAsJsonObject("textures");
        assertEquals("optifine/cit/weapons/ender_golem_q", json.get("parent").getAsString());
        assertEquals("optifine/cit/weapons/item/wooden_tonfa", textures.get("0").getAsString());
        assertEquals("#0", textures.get("1").getAsString());
        assertEquals("minecraft:block/dragon", textures.get("2").getAsString());
        assertEquals("optifine/cit/x/y", textures.get("3").getAsString());
        assertEquals("item/stick", textures.get("4").getAsString());
        assertEquals(List.of(mc("optifine/cit/weapons/ender_golem_q"), mc("optifine/cit/weapons/bow_pulling_0")),
                PrCitModelJson.dependencies(json));
        assertEquals(4, PrCitModelJson.textures(json).size());
    }

    @Test
    void enchantmentLayersOnePerLayerByWeightAndItem() {
        PrCitEntry weak = glint("weak", "enchantmentIDs=sharpness\nlayer=0\nweight=1\n");
        PrCitEntry strong = glint("strong", "enchantmentIDs=sharpness\nlayer=0\nweight=5\n");
        PrCitEntry tough = glint("tough", "enchantmentIDs=unbreaking\nlayer=1\n");
        PrCitEntry diamondOnly = glint("diamond", "enchantmentIDs=mending\nlayer=2\nitems=diamond_sword\n");
        Map<ResourceLocation, PrCitEntry[]> byEnchantment = Map.of(
                mc("sharpness"), new PrCitEntry[] {strong, weak},
                mc("unbreaking"), new PrCitEntry[] {tough},
                mc("mending"), new PrCitEntry[] {diamondOnly});
        Stack sword = Stack.of("iron_sword").enchanted("sharpness", 5).enchanted("unbreaking", 3).enchanted("mending", 1);

        PrCitGlint.Effects effects = PrCit.selectEffects(index(byEnchantment, PrCitGlobal.DEFAULT), sword);
        assertEquals(List.of(strong, tough), List.of(effects.entries()), "one per layer, the heavier wins; mending is diamond only");
        float[] average = effects.strengths(PrCitGlobal.DEFAULT);
        assertEquals(5 / 8.0F, average[0], 1e-6F);
        assertEquals(3 / 8.0F, average[1], 1e-6F);
        PrCitGlobal layered = new PrCitGlobal(true, PrCitGlobal.Method.LAYERED, Integer.MAX_VALUE, 0.5F);
        assertEquals(1.0F, effects.strengths(layered)[1]);

        PrCitGlobal capped = new PrCitGlobal(true, PrCitGlobal.Method.AVERAGE, 1, 0.5F);
        assertEquals(List.of(strong), List.of(PrCit.selectEffects(index(byEnchantment, capped), sword).entries()));
        assertTrue(PrCit.selectEffects(index(byEnchantment, PrCitGlobal.DEFAULT), Stack.of("iron_sword")).isEmpty());
    }

    private PrCitEntry glint(String name, String text) {
        PrCitRule rule = parse("glint/" + name + ".properties", "type=enchantment\ntexture=./" + name + "\n" + text);
        assertNotNull(rule, warnings::toString);
        return PrCitEntry.plain(rule, rule.texture, Map.of(), 16);
    }

    private static PrCitIndex index(Map<ResourceLocation, PrCitEntry[]> enchantments, PrCitGlobal global) {
        return new PrCitIndex(Map.of(), enchantments, global, enchantments.size());
    }
}
