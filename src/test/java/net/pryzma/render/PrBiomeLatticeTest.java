package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;

class PrBiomeLatticeTest {

    @Test
    void bitIdenticalToVanillaGaussianSampleAcrossSingleBiome() {
        PrBiomeLattice lattice = new PrBiomeLattice();
        PrBiomeLattice.Source source = (qx, qy, qz) -> "Plains";
        Vec3 testColor = new Vec3(0.35, 0.65, 0.85);
        CubicSampler.Vec3Fetcher fetcher = (x, y, z) -> testColor;

        Random rng = new Random(42);
        for (int i = 0; i < 2000; i++) {
            double vx = (rng.nextDouble() - 0.5) * 20000.0;
            double vy = (rng.nextDouble() - 0.5) * 500.0;
            double vz = (rng.nextDouble() - 0.5) * 20000.0;
            Vec3 pos = new Vec3(vx, vy, vz);

            Vec3 expected = CubicSampler.gaussianSampleVec3(pos, fetcher);
            Vec3 actual = lattice.sample(pos, fetcher, "Owner1", source, 1);

            assertEquals(Double.doubleToRawLongBits(expected.x), Double.doubleToRawLongBits(actual.x),
                    "X mismatch at " + pos);
            assertEquals(Double.doubleToRawLongBits(expected.y), Double.doubleToRawLongBits(actual.y),
                    "Y mismatch at " + pos);
            assertEquals(Double.doubleToRawLongBits(expected.z), Double.doubleToRawLongBits(actual.z),
                    "Z mismatch at " + pos);
        }
    }

    @Test
    void bitIdenticalToVanillaGaussianSampleAcrossDiverseMultiBiomeGrid() {
        PrBiomeLattice lattice = new PrBiomeLattice();
        String[] biomes = {"Plains", "Desert", "Forest", "Ocean", "Swamp", "Savanna", "Taiga", "Jungle"};
        Vec3[] colors = {
                new Vec3(0.5, 0.7, 0.9),
                new Vec3(0.8, 0.6, 0.2),
                new Vec3(0.2, 0.8, 0.3),
                new Vec3(0.1, 0.3, 0.9),
                new Vec3(0.3, 0.5, 0.4),
                new Vec3(0.7, 0.7, 0.3),
                new Vec3(0.4, 0.6, 0.7),
                new Vec3(0.3, 0.9, 0.2)
        };

        // Biome is a function of quart coordinates
        PrBiomeLattice.Source source = (qx, qy, qz) -> {
            int hash = Math.floorMod(qx * 31 + qy * 17 + qz * 13, biomes.length);
            return biomes[hash];
        };

        CubicSampler.Vec3Fetcher fetcher = (x, y, z) -> {
            int hash = Math.floorMod(x * 31 + y * 17 + z * 13, colors.length);
            return colors[hash];
        };

        Random rng = new Random(1337);
        for (int i = 0; i < 5000; i++) {
            double vx = (rng.nextDouble() - 0.5) * 1000.0;
            double vy = (rng.nextDouble() - 0.5) * 200.0;
            double vz = (rng.nextDouble() - 0.5) * 1000.0;
            Vec3 pos = new Vec3(vx, vy, vz);

            Vec3 expected = CubicSampler.gaussianSampleVec3(pos, fetcher);
            Vec3 actual = lattice.sample(pos, fetcher, "Owner1", source, 1);

            assertEquals(Double.doubleToRawLongBits(expected.x), Double.doubleToRawLongBits(actual.x),
                    "X bit mismatch in multi-biome grid at " + pos);
            assertEquals(Double.doubleToRawLongBits(expected.y), Double.doubleToRawLongBits(actual.y),
                    "Y bit mismatch in multi-biome grid at " + pos);
            assertEquals(Double.doubleToRawLongBits(expected.z), Double.doubleToRawLongBits(actual.z),
                    "Z bit mismatch in multi-biome grid at " + pos);
        }
    }

    @Test
    void bitIdenticalWhenEveryQuartIsUniqueBiome() {
        // Worst case: 216 distinct biomes
        PrBiomeLattice lattice = new PrBiomeLattice();
        PrBiomeLattice.Source source = (qx, qy, qz) -> "Biome_" + qx + "_" + qy + "_" + qz;
        CubicSampler.Vec3Fetcher fetcher = (x, y, z) -> new Vec3((x & 0xFF) / 255.0, (y & 0xFF) / 255.0, (z & 0xFF) / 255.0);

        Random rng = new Random(999);
        for (int i = 0; i < 1000; i++) {
            double vx = (rng.nextDouble() - 0.5) * 500.0;
            double vy = (rng.nextDouble() - 0.5) * 100.0;
            double vz = (rng.nextDouble() - 0.5) * 500.0;
            Vec3 pos = new Vec3(vx, vy, vz);

            Vec3 expected = CubicSampler.gaussianSampleVec3(pos, fetcher);
            Vec3 actual = lattice.sample(pos, fetcher, "Owner1", source, 1);

            assertEquals(Double.doubleToRawLongBits(expected.x), Double.doubleToRawLongBits(actual.x),
                    "216 distinct biomes X mismatch at " + pos);
            assertEquals(Double.doubleToRawLongBits(expected.y), Double.doubleToRawLongBits(actual.y),
                    "216 distinct biomes Y mismatch at " + pos);
            assertEquals(Double.doubleToRawLongBits(expected.z), Double.doubleToRawLongBits(actual.z),
                    "216 distinct biomes Z mismatch at " + pos);
        }
    }

    @Test
    void matchesOriginAndEpoch() {
        PrBiomeLattice lattice = new PrBiomeLattice();
        PrBiomeLattice.Source source = (qx, qy, qz) -> "Plains";
        CubicSampler.Vec3Fetcher fetcher = (x, y, z) -> new Vec3(0.1, 0.2, 0.3);

        Vec3 pos1 = new Vec3(10.2, 64.5, -20.1);
        lattice.sample(pos1, fetcher, "Owner", source, 5);

        // Within same integer quart block and epoch -> matches
        assertTrue(lattice.matches(new Vec3(10.8, 64.1, -20.9), 5));

        // Different epoch -> miss
        assertTrue(!lattice.matches(pos1, 6));

        // Different integer coordinate -> miss
        assertTrue(!lattice.matches(new Vec3(11.0, 64.5, -20.1), 5));
    }
}
