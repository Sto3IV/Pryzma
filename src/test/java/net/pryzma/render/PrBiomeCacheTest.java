package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

class PrBiomeCacheTest {

    @Test
    void singleThreadedRandomSourceIsBitIdenticalToLegacyRandomSource() {
        SingleThreadedRandomSource reusable = new SingleThreadedRandomSource(0L);
        Random rng = new Random(777);

        for (int i = 0; i < 5000; i++) {
            long seed = rng.nextLong();
            RandomSource vanilla = RandomSource.create(seed);
            reusable.setSeed(seed);

            for (int step = 0; step < 10; step++) {
                assertEquals(vanilla.nextInt(), reusable.nextInt(), "nextInt mismatch at seed " + seed);
                assertEquals(vanilla.nextFloat(), reusable.nextFloat(), "nextFloat mismatch at seed " + seed);
                assertEquals(vanilla.nextDouble(), reusable.nextDouble(), "nextDouble mismatch at seed " + seed);
                assertEquals(vanilla.nextGaussian(), reusable.nextGaussian(), "nextGaussian mismatch at seed " + seed);
            }
        }
    }
}
