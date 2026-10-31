package net.pryzma.item;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlatItemBakedModelTest {

    @AfterEach
    void tearDown() {
        FlatItemBakedModel.clearCache();
    }

    private static class StubBakedModel implements BakedModel {
        private final List<BakedQuad> quads;

        StubBakedModel(List<BakedQuad> quads) {
            this.quads = quads;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
            if (side == null) {
                return quads;
            }
            return Collections.emptyList();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return null;
        }

        @Override
        public ItemTransforms getTransforms() {
            return ItemTransforms.NO_TRANSFORMS;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }

    private BakedQuad createQuad(Direction direction, float z) {
        int[] vertices = new int[32];
        int rawZ = Float.floatToRawIntBits(z);
        for (int v = 0; v < 4; v++) {
            vertices[v * 8 + 0] = Float.floatToRawIntBits(0.0F); // X
            vertices[v * 8 + 1] = Float.floatToRawIntBits(0.0F); // Y
            vertices[v * 8 + 2] = rawZ;                          // Z
            vertices[v * 8 + 7] = (direction == Direction.SOUTH ? 1 : (direction == Direction.NORTH ? -1 : 0)) << 16;
        }
        return new BakedQuad(vertices, -1, direction, null, true, false);
    }

    @Test
    @DisplayName("FlatItemBakedModel filters out side quads and collapses Z of front/back faces to 0.5F")
    void testFlattenQuads() {
        List<BakedQuad> originalQuads = new ArrayList<>();

        BakedQuad southQuad = createQuad(Direction.SOUTH, 0.53125F); // Front face (Z = 8.5/16)
        BakedQuad northQuad = createQuad(Direction.NORTH, 0.46875F); // Back face (Z = 7.5/16)
        BakedQuad upQuad = createQuad(Direction.UP, 0.5F);           // Side top edge
        BakedQuad downQuad = createQuad(Direction.DOWN, 0.5F);       // Side bottom edge
        BakedQuad westQuad = createQuad(Direction.WEST, 0.5F);       // Side left edge
        BakedQuad eastQuad = createQuad(Direction.EAST, 0.5F);       // Side right edge

        originalQuads.add(southQuad);
        originalQuads.add(northQuad);
        originalQuads.add(upQuad);
        originalQuads.add(downQuad);
        originalQuads.add(westQuad);
        originalQuads.add(eastQuad);

        StubBakedModel stubOriginal = new StubBakedModel(originalQuads);
        BakedModel flatModel = FlatItemBakedModel.of(stubOriginal);
        assertNotNull(flatModel);

        List<BakedQuad> flattened = flatModel.getQuads(null, null, RandomSource.create());
        // Only SOUTH and NORTH quads should remain (UP, DOWN, WEST, EAST must be stripped)
        assertEquals(2, flattened.size(), "Only front and back faces should be kept");

        for (BakedQuad quad : flattened) {
            assertTrue(quad.getDirection() == Direction.SOUTH || quad.getDirection() == Direction.NORTH,
                    "Expected only Z-axis quads, got: " + quad.getDirection());
            int[] v = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                float z = Float.intBitsToFloat(v[i * 8 + 2]);
                assertEquals(0.5F, z, 0.0001F, "Vertex Z must be flattened to 0.5F (0px local thickness)");
            }
        }
    }

    @Test
    @DisplayName("FlatItemBakedModel returns empty list for directional side face queries")
    void testSideFaceQueriesReturnEmpty() {
        StubBakedModel stubOriginal = new StubBakedModel(Collections.emptyList());
        BakedModel flatModel = FlatItemBakedModel.of(stubOriginal);

        assertTrue(flatModel.getQuads(null, Direction.UP, RandomSource.create()).isEmpty());
        assertTrue(flatModel.getQuads(null, Direction.DOWN, RandomSource.create()).isEmpty());
        assertTrue(flatModel.getQuads(null, Direction.WEST, RandomSource.create()).isEmpty());
        assertTrue(flatModel.getQuads(null, Direction.EAST, RandomSource.create()).isEmpty());
    }

    @Test
    @DisplayName("FlatItemBakedModel caching returns singleton per original model")
    void testModelCache() {
        StubBakedModel stubOriginal = new StubBakedModel(Collections.emptyList());
        BakedModel flat1 = FlatItemBakedModel.of(stubOriginal);
        BakedModel flat2 = FlatItemBakedModel.of(stubOriginal);
        assertSame(flat1, flat2, "FlatItemBakedModel.of must cache wrappers");
        assertSame(flat1, FlatItemBakedModel.of(flat1), "Wrapping an already flat model must return itself");
        assertNull(FlatItemBakedModel.of(null));
    }
}
