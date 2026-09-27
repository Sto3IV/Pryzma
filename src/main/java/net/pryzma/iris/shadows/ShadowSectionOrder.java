package net.pryzma.iris.shadows;

import java.util.Arrays;

/** Ordering of the shadow pass's sections: nearest the light first, linear time. */
final class ShadowSectionOrder {
	private ShadowSectionOrder() {
	}

	/**
	 * Stable counting sort of {@code items[0, count)} by {@code keys}, largest key first, into
	 * {@code out[0, count)}. Keys lie in {@code [minKey, maxKey]} (16-block view-space depth buckets, so the range
	 * spans the shadow distance in sections).
	 *
	 * @param buckets scratch counts, reused when long enough
	 * @return the scratch array actually used, to keep for the next call
	 */
	static int[] sortDescending(Object[] items, int[] keys, int count, int minKey, int maxKey, int[] buckets, Object[] out) {
		int bucketCount = maxKey - minKey + 1;
		if (buckets.length < bucketCount + 1) {
			buckets = new int[bucketCount + 1];
		} else {
			Arrays.fill(buckets, 0, bucketCount + 1, 0);
		}
		// Bucket b holds key maxKey - b; buckets[b + 1] counts it, then prefix sums turn counts into starts.
		for (int i = 0; i < count; i++) {
			buckets[maxKey - keys[i] + 1]++;
		}
		for (int b = 1; b <= bucketCount; b++) {
			buckets[b] += buckets[b - 1];
		}
		for (int i = 0; i < count; i++) {
			out[buckets[maxKey - keys[i]]++] = items[i];
		}
		return buckets;
	}
}
