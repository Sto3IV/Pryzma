package net.pryzma.render;

/**
 * Duck interface mixed into {@code VertexBuffer}. A region-managed buffer owns no GL objects of its
 * own: its vertices live in a {@link VboRegion} and its draws are queued into that region.
 */
public interface IPrVertexBuffer {
    /** Moves this buffer to {@code region}, releasing its range in the previous one; {@code null} detaches. */
    void pryzma$setVboRegion(VboRegion region);

    VboRegion pryzma$getVboRegion();

    /** The slice of the region holding this buffer's vertices; {@code null} before the first upload. */
    VboRange pryzma$getVboRange();

    /**
     * Makes this buffer region-managed: {@code layer} indexes {@link PrRenderRegionManager}'s region
     * layers and {@code key} names the region its next upload goes to.
     */
    void pryzma$setRegionSlot(int layer, long key);

    boolean pryzma$isRegionManaged();
}
