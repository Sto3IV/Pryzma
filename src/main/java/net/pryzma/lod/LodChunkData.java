package net.pryzma.lod;

import java.util.Arrays;

/**
 * Farlands LOD: the surface of one chunk as 16 x 16 columns.
 *
 * <p>Each column has up to three layers, top down:
 * <ul>
 * <li><b>Canopy</b>: the foliage of trees, a slab from {@link #canopyTop} down to {@link #canopyBottom}, kept apart
 * from the ground so a forest is meshed as crowns above the forest floor rather than as a solid block of leaves.</li>
 * <li><b>Water</b>: a translucent fluid surface ({@link #heightFluid}), meshed in its own pass above the floor.</li>
 * <li><b>Ground</b>: the solid surface ({@link #heightSolid}): the land, the floor below water, or the forest floor
 * below a canopy. Opaque fluids (lava) are ground.</li>
 * </ul>
 * Heights are block-top coordinates: a grass block at y = 63 has its surface at 64, the value vanilla's
 * {@code MOTION_BLOCKING} heightmap stores. Index layout: {@code z * 16 + x} with chunk-local coordinates, see
 * {@link #index}.
 *
 * <p>The arrays are filled by {@link LodWorldScanner} before the instance is published. Once an instance is in a
 * {@link LodDataStore} it must not change: meshing threads read it without locks. A rescan or a re-tint publishes a
 * new instance; {@link #sequence()} orders captures of the same chunk so an older one never replaces a newer one.
 */
public final class LodChunkData {
    public static final int SIZE = 16;
    public static final int AREA = SIZE * SIZE;
    /** {@link #heightSolid} of a column without a solid surface. */
    public static final short NO_SURFACE = Short.MIN_VALUE;
    /** {@link #heightFluid} of a column without a fluid surface. */
    public static final short NO_FLUID = Short.MIN_VALUE;
    /** {@link #canopyTop} of a column without a canopy. */
    public static final short NO_CANOPY = Short.MIN_VALUE;
    /** Tint of a surface that has none: white, the identity of a multiply. */
    public static final int NO_TINT = 0xFFFFFF;
    /** {@link #biomeId} of a column whose biome is unknown. */
    public static final int NO_BIOME = -1;

    /** Top of the solid surface (-64..320 in the overworld); under a fluid the floor, under a canopy the forest floor. */
    public final short[] heightSolid = new short[AREA];
    /** Top of the translucent fluid surface, {@link #NO_FLUID} where the ground is open to the sky or the canopy. */
    public final short[] heightFluid = new short[AREA];
    /** Block state id ({@code Block.getId}) of the solid surface block; 0 (air) where none is known. */
    public final int[] blockStateId = new int[AREA];
    /** Block state id of the fluid surface block (water); 0 without a fluid. */
    public final int[] fluidStateId = new int[AREA];
    /** Biome tint (RGB) of the solid surface block, {@link #NO_TINT} for untinted blocks. */
    public final int[] solidTint = new int[AREA];
    /** Biome tint (RGB) of the fluid surface, {@link #NO_TINT} for untinted fluids. */
    public final int[] fluidTint = new int[AREA];
    /** Sky light (0..15) in the air block right above the ground, or above the fluid surface where there is one. */
    public final byte[] skyLight = new byte[AREA];
    /** Block light (0..15) in the air block right above the ground, or above the fluid surface where there is one. */
    public final byte[] blockLight = new byte[AREA];

    /** Top of the canopy (the highest foliage block's top), {@link #NO_CANOPY} for a column without one. */
    public final short[] canopyTop = new short[AREA];
    /** Bottom of the canopy: the lowest foliage block of the column above the ground (its bottom face). */
    public final short[] canopyBottom = new short[AREA];
    /** Block state id of the canopy's top block. */
    public final int[] canopyStateId = new int[AREA];
    /** Biome tint (RGB) of the canopy, {@link #NO_TINT} for untinted foliage. */
    public final int[] canopyTint = new int[AREA];
    /** Light right above the canopy, packed as {@code blockLight << 4 | skyLight}. */
    public final byte[] canopyLight = new byte[AREA];
    /** Registry id of the column's biome at its surface, {@link #NO_BIOME} if unknown; tints are recomputed from it. */
    public final int[] biomeId = new int[AREA];

    private final int chunkX;
    private final int chunkZ;
    private final long sequence;

    /**
     * @param sequence capture order: higher values are newer. Saved-world reads use 0, so a capture of the
     *                 live chunk always wins over them.
     */
    public LodChunkData(int chunkX, int chunkZ, long sequence) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sequence = sequence;
        Arrays.fill(heightSolid, NO_SURFACE);
        Arrays.fill(heightFluid, NO_FLUID);
        Arrays.fill(canopyTop, NO_CANOPY);
        Arrays.fill(canopyBottom, NO_CANOPY);
        Arrays.fill(solidTint, NO_TINT);
        Arrays.fill(fluidTint, NO_TINT);
        Arrays.fill(canopyTint, NO_TINT);
        Arrays.fill(biomeId, NO_BIOME);
    }

    /** Column index of chunk-local {@code (x, z)}, both 0..15. */
    public static int index(int x, int z) {
        return z << 4 | x;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public long sequence() {
        return sequence;
    }

    /** {@link LodDataStore#key} of this chunk. */
    public long key() {
        return LodDataStore.key(chunkX, chunkZ);
    }

    /** Whether column {@code i} has any surface at all; void columns and unscanned ones have none. */
    public boolean hasSurface(int i) {
        return heightSolid[i] != NO_SURFACE || heightFluid[i] != NO_FLUID;
    }

    public boolean hasFluid(int i) {
        return heightFluid[i] != NO_FLUID;
    }

    public boolean hasCanopy(int i) {
        return canopyTop[i] != NO_CANOPY;
    }

    /** Height of the visible top below any canopy: the fluid surface where there is one, the solid surface elsewhere. */
    public int topHeight(int i) {
        return heightFluid[i] != NO_FLUID ? heightFluid[i] : heightSolid[i];
    }

    /** Fluid above the solid surface, in blocks; 0 for columns without a fluid. */
    public int fluidDepth(int i) {
        if (heightFluid[i] == NO_FLUID) {
            return 0;
        }
        return heightSolid[i] == NO_SURFACE ? Integer.MAX_VALUE : Math.max(0, heightFluid[i] - heightSolid[i]);
    }

    /** Block state id of the visible top below any canopy: the fluid where there is one, the solid block elsewhere. */
    public int topStateId(int i) {
        return heightFluid[i] != NO_FLUID ? fluidStateId[i] : blockStateId[i];
    }

    /** Biome tint of the visible top below any canopy. */
    public int topTint(int i) {
        return heightFluid[i] != NO_FLUID ? fluidTint[i] : solidTint[i];
    }

    /** Sets the geometry, block states and light of column {@code i}; heights use the NO_ constants for none. */
    public void setColumn(int i, int heightSolid, int blockStateId, int heightFluid, int fluidStateId, int skyLight, int blockLight) {
        this.heightSolid[i] = (short) heightSolid;
        this.blockStateId[i] = blockStateId;
        this.heightFluid[i] = (short) heightFluid;
        this.fluidStateId[i] = fluidStateId;
        this.skyLight[i] = (byte) (skyLight & 15);
        this.blockLight[i] = (byte) (blockLight & 15);
    }

    /** Sets the biome tints of column {@code i}. */
    public void setTints(int i, int solidTint, int fluidTint) {
        this.solidTint[i] = solidTint & 0xFFFFFF;
        this.fluidTint[i] = fluidTint & 0xFFFFFF;
    }

    /**
     * Sets the canopy of column {@code i}: foliage from {@code bottom} (the lowest foliage block's bottom face) up to
     * {@code top}, with the light above it packed as {@code blockLight << 4 | skyLight}.
     */
    public void setCanopy(int i, int top, int bottom, int stateId, int tint, int light) {
        this.canopyTop[i] = (short) top;
        this.canopyBottom[i] = (short) Math.min(bottom, top);
        this.canopyStateId[i] = stateId;
        this.canopyTint[i] = tint & 0xFFFFFF;
        this.canopyLight[i] = (byte) (light & 0xFF);
    }

    /** Light of column {@code i} packed as {@code blockLight << 4 | skyLight}. */
    public int packedLight(int i) {
        return (blockLight[i] & 15) << 4 | skyLight[i] & 15;
    }

    /** Light above the canopy of column {@code i}, packed as {@code blockLight << 4 | skyLight}. */
    public int packedCanopyLight(int i) {
        return canopyLight[i] & 0xFF;
    }

    /** A copy of this chunk's columns with the same position and sequence; the copy's arrays are its own. */
    public LodChunkData copy() {
        LodChunkData out = new LodChunkData(chunkX, chunkZ, sequence);
        System.arraycopy(heightSolid, 0, out.heightSolid, 0, AREA);
        System.arraycopy(heightFluid, 0, out.heightFluid, 0, AREA);
        System.arraycopy(blockStateId, 0, out.blockStateId, 0, AREA);
        System.arraycopy(fluidStateId, 0, out.fluidStateId, 0, AREA);
        System.arraycopy(solidTint, 0, out.solidTint, 0, AREA);
        System.arraycopy(fluidTint, 0, out.fluidTint, 0, AREA);
        System.arraycopy(skyLight, 0, out.skyLight, 0, AREA);
        System.arraycopy(blockLight, 0, out.blockLight, 0, AREA);
        System.arraycopy(canopyTop, 0, out.canopyTop, 0, AREA);
        System.arraycopy(canopyBottom, 0, out.canopyBottom, 0, AREA);
        System.arraycopy(canopyStateId, 0, out.canopyStateId, 0, AREA);
        System.arraycopy(canopyTint, 0, out.canopyTint, 0, AREA);
        System.arraycopy(canopyLight, 0, out.canopyLight, 0, AREA);
        System.arraycopy(biomeId, 0, out.biomeId, 0, AREA);
        return out;
    }

    @Override
    public String toString() {
        return "LodChunkData[" + chunkX + ", " + chunkZ + " #" + sequence + "]";
    }
}
