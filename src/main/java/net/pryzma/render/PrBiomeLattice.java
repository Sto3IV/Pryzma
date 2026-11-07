package net.pryzma.render;

import net.minecraft.util.Mth;
import net.minecraft.world.attribute.GaussianSampler;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;

/**
 * Exact, zero-allocation replacement for GaussianSampler over a memoized 6x6x6 quart lattice.
 * Render thread only.
 */
public final class PrBiomeLattice {
    private static final double[] K = {0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0};

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

    public Vec3 sample(Vec3 v, GaussianSampler.Sampler<Vec3> fetcher, BiomeManager bm, int epoch) {
        this.manager = bm;
        return sample(v, fetcher, bm, this.viaManager, epoch);
    }

    /** Test seam and core evaluation. */
    public Vec3 sample(Vec3 v, GaussianSampler.Sampler<Vec3> fetcher, Object owner, Source source, int epoch) {
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
            Vec3 f = fetcher.get(i - 2 + c / 36, j - 2 + (c / 6) % 6, k - 2 + c % 6);
            r[d] = f.x;
            g[d] = f.y;
            b[d] = f.z;
        }
        double fx = v.x() - (double) i;
        double fy = v.y() - (double) j;
        double fz = v.z() - (double) k;
        double sumR = 0.0;
        double sumG = 0.0;
        double sumB = 0.0;
        int idx = 0;
        for (int x = 0; x < 6; x++) {
            double wx = Mth.lerp(fx, K[x + 1], K[x]);
            for (int y = 0; y < 6; y++) {
                double wxy = wx * Mth.lerp(fy, K[y + 1], K[y]);
                for (int z = 0; z < 6; z++) {
                    double w = wxy * Mth.lerp(fz, K[z + 1], K[z]);
                    int d = cell[idx++];
                    sumR += w * r[d];
                    sumG += w * g[d];
                    sumB += w * b[d];
                }
            }
        }
        return new Vec3(sumR * 0.001953125, sumG * 0.001953125, sumB * 0.001953125);
    }

    private void rebuild(Source source, int ox, int oy, int oz) {
        count = 0;
        int idx = 0;
        for (int x = 0; x < 6; x++) {
            int qx = ox - 2 + x;
            for (int y = 0; y < 6; y++) {
                int qy = oy - 2 + y;
                for (int z = 0; z < 6; z++) {
                    int qz = oz - 2 + z;
                    Object biome = source.biomeAt(qx, qy, qz);
                    int found = -1;
                    for (int d = 0; d < count; d++) {
                        if (distinct[d] == biome) {
                            found = d;
                            break;
                        }
                    }
                    if (found < 0) {
                        found = count++;
                        distinct[found] = biome;
                        firstCell[found] = (short) idx;
                    }
                    cell[idx++] = (short) found;
                }
            }
        }
    }
}
