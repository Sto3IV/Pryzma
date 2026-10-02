package net.pryzma.shader.shadows;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ShadowSectionOrderTest {
    private record Item(int key, int serial) {
    }

    @Test
    void sortsByKeyDescendingAndKeepsInputOrderWithinAKey() {
        Random random = new Random(20260927L);
        for (int round = 0; round < 50; round++) {
            int count = 1 + random.nextInt(400);
            Object[] items = new Object[count];
            int[] keys = new int[count];
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                int key = random.nextInt(41) - 20;
                items[i] = new Item(key, i);
                keys[i] = key;
                min = Math.min(min, key);
                max = Math.max(max, key);
            }
            Object[] out = new Object[count];
            ShadowSectionOrder.sortDescending(items, keys, count, min, max, new int[0], out);
            Item[] expected = Arrays.stream(items).map(o -> (Item) o)
                    .sorted(Comparator.comparingInt(Item::key).reversed().thenComparingInt(Item::serial)).toArray(Item[]::new);
            assertArrayEquals(expected, out, "round " + round);
        }
    }

    @Test
    void reusesScratchThatIsLongEnoughAndClearsIt() {
        int[] scratch = new int[16];
        Arrays.fill(scratch, 99);
        Object[] items = {"a", "b", "c"};
        int[] keys = {1, 3, 2};
        Object[] out = new Object[3];
        int[] used = ShadowSectionOrder.sortDescending(items, keys, 3, 1, 3, scratch, out);
        assertSame(scratch, used);
        assertArrayEquals(new Object[] {"b", "c", "a"}, out);
    }

    @Test
    void growsScratchForAWiderKeyRange() {
        int[] used = ShadowSectionOrder.sortDescending(new Object[] {"x", "y"}, new int[] {-100, 100}, 2, -100, 100, new int[4], new Object[2]);
        assertTrue(used.length >= 202);
    }

    @Test
    void onlyTheFirstCountItemsAreSorted() {
        Object[] items = {"a", "b", "stale"};
        int[] keys = {0, 5, 99};
        Object[] out = new Object[3];
        ShadowSectionOrder.sortDescending(items, keys, 2, 0, 5, new int[0], out);
        assertArrayEquals(new Object[] {"b", "a", null}, out);
    }
}
