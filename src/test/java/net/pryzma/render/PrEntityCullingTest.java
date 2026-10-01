package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.pryzma.PryzmaConfig;

class PrEntityCullingTest {

    @BeforeEach
    void setUp() {
        PryzmaConfig.prEntityCulling = true;
        PrEntityCulling.reset();
    }

    @AfterEach
    void tearDown() {
        PrEntityCulling.reset();
    }

    @Test
    void testInactiveOrConfigDisabledReturnsTrue() {
        AABB bb = new AABB(0, 64, 0, 1, 66, 1);

        // Inactive: should return true
        assertTrue(PrEntityCulling.shouldRenderBoundingBox(bb));

        // Active with sections, but config disabled
        long sec1 = SectionPos.asLong(0, 4, 0);
        PrEntityCulling.populateTestSections(new long[]{sec1}, 4, 4);
        PryzmaConfig.prEntityCulling = false;
        assertTrue(PrEntityCulling.shouldRenderBoundingBox(bb));
    }

    @Test
    void testSingleSectionEntityCulling() {
        // Chunk section (0, 4, 0) corresponds to X: [0..15], Y: [64..79], Z: [0..15]
        long secVisible = SectionPos.asLong(0, 4, 0);
        PrEntityCulling.populateTestSections(new long[]{secVisible}, 4, 4);

        // Inside visible section: (5, 65, 5) -> returns true
        AABB visibleEntity = new AABB(5, 65, 5, 6, 67, 6);
        assertTrue(PrEntityCulling.shouldRenderBoundingBox(visibleEntity));

        // Inside non-visible section: (25, 65, 5) which is secX=1 -> returns false
        AABB hiddenEntity = new AABB(25, 65, 5, 26, 67, 6);
        assertFalse(PrEntityCulling.shouldRenderBoundingBox(hiddenEntity));
    }

    @Test
    void testBoundaryOverlapRendersIfAnySectionVisible() {
        // Only section (0, 4, 0) is visible, section (1, 4, 0) is occluded
        long sec0 = SectionPos.asLong(0, 4, 0);
        PrEntityCulling.populateTestSections(new long[]{sec0}, 4, 4);

        // Entity straddling the boundary at X = 16: minX = 15.5 (in sec 0), maxX = 16.5 (in sec 1)
        AABB straddlingEntity = new AABB(15.5, 65.0, 5.0, 16.5, 67.0, 6.0);
        assertTrue(PrEntityCulling.shouldRenderBoundingBox(straddlingEntity),
                "Entity touching at least one visible section must not be culled");

        // Entity entirely in section 1: minX = 16.5, maxX = 17.5
        AABB outsideEntity = new AABB(16.5, 65.0, 5.0, 17.5, 67.0, 6.0);
        assertFalse(PrEntityCulling.shouldRenderBoundingBox(outsideEntity));
    }

    @Test
    void testLargeBoundingBoxBypassesCulling() {
        // Visible section is (0, 4, 0)
        long sec0 = SectionPos.asLong(0, 4, 0);
        PrEntityCulling.populateTestSections(new long[]{sec0}, 4, 4);

        // Huge entity (e.g. Ender Dragon or Ghast) in hidden section (5, 4, 5) with width = 4.0
        AABB hugeEntity = new AABB(80.0, 65.0, 80.0, 84.5, 69.0, 84.5);
        assertTrue(PrEntityCulling.shouldRenderBoundingBox(hugeEntity),
                "Entities larger than 3 blocks must bypass occlusion culling");
    }

    @Test
    void testVerticalOutOfBoundsBypassesCulling() {
        // Visible sections are between Y sections 4 and 8 (Y = 64..143)
        long sec4 = SectionPos.asLong(0, 4, 0);
        long sec8 = SectionPos.asLong(0, 8, 0);
        PrEntityCulling.populateTestSections(new long[]{sec4, sec8}, 4, 8);

        // Entity high up in the sky at Y = 250 (secY = 15 > maxVisibleSecY 8)
        AABB skyEntity = new AABB(0, 250, 0, 1, 252, 1);
        assertTrue(PrEntityCulling.shouldRenderBoundingBox(skyEntity),
                "Entities above the highest visible section must not be culled");

        // Entity below minimum visible section at Y = -30 (secY = -2 < minVisibleSecY 4)
        AABB voidEntity = new AABB(0, -30, 0, 1, -28, 1);
        assertTrue(PrEntityCulling.shouldRenderBoundingBox(voidEntity),
                "Entities below the lowest visible section must not be culled");
    }

    @Test
    void testResetClearsState() {
        long sec0 = SectionPos.asLong(0, 4, 0);
        PrEntityCulling.populateTestSections(new long[]{sec0}, 4, 4);
        assertTrue(PrEntityCulling.isActive());
        assertEquals(1, PrEntityCulling.getVisibleSectionCount());

        PrEntityCulling.reset();
        assertFalse(PrEntityCulling.isActive());
        assertEquals(0, PrEntityCulling.getVisibleSectionCount());

        AABB bb = new AABB(5, 65, 5, 6, 67, 6);
        assertTrue(PrEntityCulling.shouldRenderBoundingBox(bb));
    }

    @Test
    void testOnFrameEndDeactivates() {
        long sec0 = SectionPos.asLong(0, 4, 0);
        PrEntityCulling.populateTestSections(new long[]{sec0}, 4, 4);
        assertTrue(PrEntityCulling.isActive());

        PrEntityCulling.onFrameEnd();
        assertFalse(PrEntityCulling.isActive());

        // Inactive frame end means GUI / out of world entity rendering is not culled
        AABB bbHidden = new AABB(50, 65, 50, 51, 67, 51);
        assertTrue(PrEntityCulling.shouldRenderBoundingBox(bbHidden));
    }
}
