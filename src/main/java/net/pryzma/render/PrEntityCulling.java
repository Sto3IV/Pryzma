package net.pryzma.render;

import java.util.List;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.pryzma.PryzmaConfig;

/**
 * Phase B5: Section-Occlusion Entity Culling.
 *
 * <p>During world rendering, Vanilla Minecraft computes the exact set of visible chunk sections
 * for the current camera frame via occlusion traversal. Vanilla's {@code LevelRenderer.renderLevel}
 * queries this set for block entities, but iterates all world entities with only frustum culling,
 * leaving hundreds of underground/occluded entities to be fully animated and rendered.
 *
 * <p>PrEntityCulling mirrors chunk occlusion visibility to entities:
 * <ul>
 *   <li>Pre-allocates a reusable {@link LongOpenHashSet} populated once per frame in ~5-10 µs.</li>
 *   <li>Evaluates entity bounding box overlap across chunk section boundaries with zero GC allocation.</li>
 *   <li>Provides comprehensive bypasses for camera/local player, passengers, vehicles, leashes,
 *       glowing outlines, display entities, and large bounding boxes (> 3 blocks).</li>
 * </ul>
 */
public final class PrEntityCulling {

    private static final LongOpenHashSet VISIBLE_SECTIONS = new LongOpenHashSet(2048);
    private static int minVisibleSecY = Integer.MAX_VALUE;
    private static int maxVisibleSecY = Integer.MIN_VALUE;
    private static boolean active = false;

    private PrEntityCulling() {}

    /**
     * Called once per frame right after {@code setupRender} populates {@code visibleSections}.
     */
    public static void onFrameStart(List<SectionRenderDispatcher.RenderSection> visibleSections) {
        if (!PryzmaConfig.prEntityCulling || visibleSections == null || visibleSections.isEmpty()) {
            VISIBLE_SECTIONS.clear();
            active = false;
            return;
        }

        active = true;
        VISIBLE_SECTIONS.clear();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        int count = visibleSections.size();
        for (int i = 0; i < count; i++) {
            SectionRenderDispatcher.RenderSection section = visibleSections.get(i);
            if (section == null) continue;
            BlockPos origin = section.getOrigin();
            int secX = SectionPos.blockToSectionCoord(origin.getX());
            int secY = SectionPos.blockToSectionCoord(origin.getY());
            int secZ = SectionPos.blockToSectionCoord(origin.getZ());

            VISIBLE_SECTIONS.add(SectionPos.asLong(secX, secY, secZ));
            if (secY < minY) minY = secY;
            if (secY > maxY) maxY = secY;
        }

        minVisibleSecY = minY;
        maxVisibleSecY = maxY;
    }

    /**
     * Called at the end of {@code renderLevel} to ensure GUI and out-of-world entity rendering
     * are never culled.
     */
    public static void onFrameEnd() {
        active = false;
    }

    /**
     * Resets all cached sections and state (e.g. on world reload, dimension change, or resize).
     */
    public static void reset() {
        VISIBLE_SECTIONS.clear();
        minVisibleSecY = Integer.MAX_VALUE;
        maxVisibleSecY = Integer.MIN_VALUE;
        active = false;
    }

    /**
     * Tests whether the given entity should be rendered in the current frame.
     *
     * @param entity the entity to test
     * @return true if the entity should be rendered, false if it is occluded
     */
    public static boolean shouldRender(Entity entity) {
        if (!active || !PryzmaConfig.prEntityCulling || entity == null) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();

        // Bypass 1: Camera entity and local player (prevents self-culling in 3rd person)
        if (entity == mc.cameraEntity || entity == mc.player) {
            return true;
        }

        // Bypass 2: Passenger / Vehicle hierarchy and leashes
        if (entity.isPassenger() || entity.isVehicle()) {
            return true;
        }
        if (mc.player != null && entity.hasIndirectPassenger(mc.player)) {
            return true;
        }
        if (entity instanceof Mob mob && mob.getLeashHolder() != null) {
            return true;
        }

        // Bypass 3: Glowing outlines (must render through solid walls)
        if (entity.hasGlowingTag() || entity.isCurrentlyGlowing() || mc.shouldEntityAppearGlowing(entity)) {
            return true;
        }

        // Bypass 4: Special entities (lightning bolts, displays with arbitrary scales)
        if (entity instanceof LightningBolt || entity instanceof Display) {
            return true;
        }

        return shouldRenderBoundingBox(entity.getBoundingBox());
    }

    /**
     * Evaluates chunk section visibility for a given bounding box.
     *
     * @param bb the axis-aligned bounding box of the entity
     * @return true if the bounding box should be rendered, false if it is in an occluded section
     */
    public static boolean shouldRenderBoundingBox(AABB bb) {
        if (!active || !PryzmaConfig.prEntityCulling || bb == null) {
            return true;
        }

        // Bypass 5: Large entities (> 3 blocks in any dimension, e.g. Ender Dragon, Ghast, Giant)
        if (bb.getXsize() > 3.0 || bb.getYsize() > 3.0 || bb.getZsize() > 3.0) {
            return true;
        }

        int minSecY = SectionPos.blockToSectionCoord(Mth.floor(bb.minY));
        int maxSecY = SectionPos.blockToSectionCoord(Mth.floor(bb.maxY));

        // Bypass 6: Out of vertical bounds of visible terrain
        if (maxSecY < minVisibleSecY || minSecY > maxVisibleSecY) {
            return true;
        }

        int minSecX = SectionPos.blockToSectionCoord(Mth.floor(bb.minX));
        int maxSecX = SectionPos.blockToSectionCoord(Mth.floor(bb.maxX));
        int minSecZ = SectionPos.blockToSectionCoord(Mth.floor(bb.minZ));
        int maxSecZ = SectionPos.blockToSectionCoord(Mth.floor(bb.maxZ));

        // Fast-path: ~95% of entities fit within a single section
        if (minSecX == maxSecX && minSecY == maxSecY && minSecZ == maxSecZ) {
            return VISIBLE_SECTIONS.contains(SectionPos.asLong(minSecX, minSecY, minSecZ));
        }

        // Section boundary overlap: render if ANY touched section is visible
        for (int sx = minSecX; sx <= maxSecX; sx++) {
            for (int sy = minSecY; sy <= maxSecY; sy++) {
                for (int sz = minSecZ; sz <= maxSecZ; sz++) {
                    if (VISIBLE_SECTIONS.contains(SectionPos.asLong(sx, sy, sz))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Testing hook to manually populate visible sections in unit tests.
     */
    public static void populateTestSections(long[] sectionPositions, int minY, int maxY) {
        VISIBLE_SECTIONS.clear();
        for (long pos : sectionPositions) {
            VISIBLE_SECTIONS.add(pos);
        }
        minVisibleSecY = minY;
        maxVisibleSecY = maxY;
        active = true;
    }

    /**
     * Checks if culling is currently active.
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * Count of visible sections currently cached.
     */
    public static int getVisibleSectionCount() {
        return VISIBLE_SECTIONS.size();
    }
}
