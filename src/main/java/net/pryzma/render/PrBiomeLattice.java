package net.pryzma.render;

import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;

/**
 * Exact, zero-allocation replacement for {@link CubicSampler#gaussianSampleVec3(Vec3, CubicSampler.Vec3Fetcher)}
 * over a memoized 6x6x6 quart lattice. Render thread only.
 *
 * <p>Vanilla evaluates the {@code fetcher} 216 times per call, constructing 650–860 temporary {@link Vec3}
 * instances. Since the fetcher produces identical colours for any quart cell sharing the same biome,
 * this lattice queries the fetcher exactly once per unique biome in the 6x6x6 window (typically 1–4 biomes),
 * and computes the Gaussian weighted sum directly using primitive double accumulators.
 *
 * <p>The result is bit-identical to vanilla's calculation.
 */
public final class PrBiomeLattice {
    private static final double[] K = {0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0}; // CubicSampler.GAUSSIAN_SAMPLE_KERNEL

    /** Quart-cell biome provider. BiomeManager::getNoiseBiomeAtQuart in-game, or synthetic table in tests. */
    @FunctionalInterface
    public interface Source {
        Object biomeAt(int qx, int qy, int qz);
    }

    private final short[] cell = new short[216];
    private final Object[] distinct = new Object[216];
    private final short[] firstCell = new short[216];
    private final double[] r = new double[216];
    private final double[] g = new double[216];
    private final double[] b = new double[216];
    private int count;
    private int ox, oy, oz;
    private int stamp; // 0 = uninitialized
    private Object owner;
    private BiomeManager manager;
    private final Source viaManager = (x, y, z) -> manager.getNoiseBiomeAtQuart(x, y, z);

    public boolean matches(Vec3 v, int epoch) {
        int i = Mth.floor(v.x());
        int j = Mth.floor(v.y());
        int k = Mth.floor(v.z());
        return stamp == epoch && i == ox && j == oy && k == oz;
    }

    public Vec3 sample(Vec3 v, CubicSampler.Vec3Fetcher fetcher, BiomeManager bm, int epoch) {
        this.manager = bm;
        return sample(v, fetcher, bm, this.viaManager, epoch);
    }

    /** Test seam and core evaluation. */
    public Vec3 sample(Vec3 v, CubicSampler.Vec3Fetcher fetcher, Object owner, Source source, int epoch) {
        int i = Mth.floor(v.x());
        int j = Mth.floor(v.y());
        int k = Mth.floor(v.z());
        if (stamp != epoch || owner != this.owner || i != ox || j != oy || k != oz) {
            rebuild(source, i, j, k);
            this.owner = owner;
            this.ox = i;
            this.oy = j;
            this.oz = k;
            this.stamp = epoch;
        }
        for (int d = 0; d < count; d++) {
            int c = firstCell[d];
            Vec3 f = fetcher.fetch(i - 2 + c / 36, j - 2 + (c / 6) % 6, k - 2 + c % 6);
            r[d] = f.x;
            g[d] = f.y;
            b[d] = f.z;
        }
        double d0 = v.x() - (double) i;
        double d1 = v.y() - (double) j;
        double d2 = v.z() - (double) k;
        double sum = 0.0;
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (int l = 0, c = 0; l < 6; l++) {
            double d4 = Mth.lerp(d0, K[l + 1], K[l]);
            for (int m = 0; m < 6; m++) {
                double d5 = Mth.lerp(d1, K[m + 1], K[m]);
                for (int n = 0; n < 6; n++, c++) {
                    double d6 = Mth.lerp(d2, K[n + 1], K[n]);
                    double w = d4 * d5 * d6;
                    sum += w;
                    int d = cell[c];
                    x = x + r[d] * w;
                    y = y + g[d] * w;
                    z = z + b[d] * w;
                }
            }
        }
        double s = 1.0 / sum;
        return new Vec3(x * s, y * s, z * s);
    }

    private void rebuild(Source source, int i, int j, int k) {
        count = 0;
        for (int c = 0; c < 216; c++) {
            Object biome = source.biomeAt(i - 2 + c / 36, j - 2 + (c / 6) % 6, k - 2 + c % 6);
            int d = 0;
            while (d < count && distinct[d] != biome) {
                d++;
            }
            if (d == count) {
                distinct[d] = biome;
                firstCell[d] = (short) c;
                count++;
            }
            cell[c] = (short) d;
        }
    }
}
