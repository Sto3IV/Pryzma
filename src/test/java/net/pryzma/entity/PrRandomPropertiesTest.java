package net.pryzma.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.core.res.PrProperties;

class PrRandomPropertiesTest {
    record Mob(int randomId, BlockPos spawnPos, ResourceLocation spawnBiome, String name, int health, int maxHealth,
            ResourceLocation profession, int professionLevel, DyeColor color, Boolean baby, int size, CompoundTag nbt,
            BlockState blockState) implements PrEntityInfo {
        /** A full-health adult nitwit: a profession no test rule lists, so profession rules apply and fail. */
        static Mob at(int id, String biome, int y) {
            return new Mob(id, new BlockPos(0, y, 0), ResourceLocation.withDefaultNamespace(biome), null, 20, 20,
                    ResourceLocation.withDefaultNamespace("nitwit"), 1, null, false, -1, new CompoundTag(), null);
        }

        Mob named(String n) {
            return new Mob(randomId, spawnPos, spawnBiome, n, health, maxHealth, profession, professionLevel, color, baby, size, nbt, blockState);
        }

        Mob asBaby() {
            return new Mob(randomId, spawnPos, spawnBiome, name, health, maxHealth, profession, professionLevel, color, true, size, nbt, blockState);
        }

        Mob villager(String prof, int level) {
            return new Mob(randomId, spawnPos, spawnBiome, name, health, maxHealth, ResourceLocation.withDefaultNamespace(prof), level,
                    color, baby, size, nbt, blockState);
        }

        Mob hurt(int hp) {
            return new Mob(randomId, spawnPos, spawnBiome, name, hp, maxHealth, profession, professionLevel, color, baby, size, nbt, blockState);
        }
    }

    record World(int moonPhase, int dayTime, Weather weather) implements PrWorldInfo {
    }

    private static final World NOON = new World(0, 6000, PrWorldInfo.Weather.CLEAR);
    private final List<String> warnings = new ArrayList<>();

    private PrRandomProperties<String> parse(String text) {
        PrProperties props = PrProperties.parse(ResourceLocation.withDefaultNamespace("optifine/random/entity/test.properties"), text);
        return PrRandomProperties.parse(props, new String[] {"textures", "skins"},
                i -> i >= 1 && i <= 10 ? "tex" + i : null, id -> true, warnings::add);
    }

    @Test
    void weightedRuleWithBiomeAndHeight() {
        PrRandomProperties<String> p = parse("""
                textures.1=2-4
                weights.1=10 5 1
                biomes.1=plains
                heights.1=60-80
                skins.2=5
                name.2=ipattern:*bob*
                textures.12=6
                baby.12=true
                textures.23=7
                """);
        assertNotNull(p, warnings.toString());
        assertEquals("tex2", p.select(Mob.at(0, "plains", 70), NOON, "base", null), "id 0 falls in weight 10");
        assertEquals("tex3", p.select(Mob.at(12, "plains", 70), NOON, "base", null), "12 of 16 falls in the second weight");
        assertEquals("tex4", p.select(Mob.at(15, "plains", 70), NOON, "base", null));
        assertEquals("tex4", p.select(Mob.at(31, "plains", 70), NOON, "base", null), "ids wrap modulo the weight sum");
        assertEquals("base", p.select(Mob.at(0, "plains", 90), NOON, "base", null), "spawn height outside 60-80");
        assertEquals("tex5", p.select(Mob.at(0, "desert", 70).named("Bobby"), NOON, "base", null), "skins.N is an alias of textures.N");
        int[] rule = new int[1];
        assertEquals("tex6", p.select(Mob.at(0, "desert", 70).asBaby(), NOON, "base", rule), "rule 12 is within ten of rule 2");
        assertEquals(12, rule[0]);
        assertEquals("base", p.select(Mob.at(0, "desert", 70), NOON, "base", rule), "rule 23 is more than ten past rule 12");
        assertEquals(0, rule[0]);
    }

    @Test
    void numberedVariantsPickUniformlyById() {
        PrRandomProperties<String> p = PrRandomProperties.ofVariants(new String[] {"a", "b", "c"});
        assertEquals("b", p.select(Mob.at(4, "plains", 64), NOON, "x", null));
        assertEquals("a", p.select(Mob.at(9, "plains", 64), NOON, "x", null));
    }

    @Test
    void invalidRulesDiscardTheFile() {
        assertNull(parse("textures.1=2 99\n"), "a missing variant invalidates the file, as in OptiFine");
        assertNull(parse("colors.1=red\n"), "no resources at all");
        assertNull(parse("textures.1=2\ncolors.1=reddish\n"), "unknown dye color");
    }

    @Test
    void missingWeightsArePaddedWithTheAverage() {
        PrRandomProperties<String> p = parse("textures.1=1 2 3\nweights.1=4\n");
        assertNotNull(p);
        // weights 4 4 4: ids 0-3 pick tex1, 4-7 tex2, 8-11 tex3
        assertEquals("tex1", p.select(Mob.at(3, "plains", 64), NOON, "base", null));
        assertEquals("tex2", p.select(Mob.at(4, "plains", 64), NOON, "base", null));
        assertEquals("tex3", p.select(Mob.at(11, "plains", 64), NOON, "base", null));
    }

    @Test
    void worldAndEntityConditions() {
        PrRandomProperties<String> p = parse("""
                textures.1=2
                professions.1=librarian:1,3-4 farmer
                textures.2=3
                health.2=0-50%
                textures.3=4
                moonPhase.3=4
                weather.3=rain thunder
                textures.4=5
                minHeight.4=100
                """);
        assertNotNull(p, warnings.toString());
        assertEquals("tex2", p.select(Mob.at(0, "plains", 64).villager("librarian", 3), NOON, "base", null));
        assertEquals("tex2", p.select(Mob.at(0, "plains", 64).villager("farmer", 2), NOON, "base", null));
        assertEquals("tex3", p.select(Mob.at(0, "plains", 64).hurt(8), NOON, "base", null), "8 of 20 is 40 percent");
        assertEquals("base", p.select(Mob.at(0, "plains", 64).villager("librarian", 2), NOON, "base", null),
                "level 2 is not listed and the villager is at full health");
        Mob noProfession = new Mob(0, new BlockPos(0, 64, 0), ResourceLocation.withDefaultNamespace("plains"), null, 20, 20,
                null, 0, null, false, -1, new CompoundTag(), null);
        assertEquals("tex2", p.select(noProfession, NOON, "base", null), "a profession rule does not apply to non-villagers");
        assertEquals("tex4", p.select(Mob.at(0, "plains", 64), new World(4, 18000, PrWorldInfo.Weather.RAIN), "base", null));
        assertEquals("base", p.select(Mob.at(0, "plains", 64), new World(4, 18000, PrWorldInfo.Weather.CLEAR), "base", null));
        assertEquals("tex5", p.select(Mob.at(0, "plains", 120), NOON, "base", null), "minHeight alone is 100-256");
    }

    @Test
    void intListsExpandIntervals() {
        assertEquals(List.of(1, 2, 3, 7, 9, 10), boxed(PrRandomProperties.parseIntList("1-3 7,9-10", warnings::add)));
        assertEquals(List.of(4), boxed(PrRandomProperties.parseIntList("4 5-2", warnings::add)));
        assertEquals(1, warnings.size(), "the descending interval is reported and skipped");
    }

    private static List<Integer> boxed(int[] values) {
        List<Integer> out = new ArrayList<>();
        for (int v : values) {
            out.add(v);
        }
        return out;
    }
}
