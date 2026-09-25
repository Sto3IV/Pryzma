package net.pryzma.entity.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

class PrCemTest {
    /** Records what a model emits: position, texture coordinates and normal per vertex. */
    static final class Capture implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        private float[] current;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            current = new float[] {x, y, z, 0, 0, 0, 0, 0};
            vertices.add(current);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            current[3] = u;
            current[4] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            current[5] = x;
            current[6] = y;
            current[7] = z;
            return this;
        }
    }

    private static final ResourceLocation JEM = ResourceLocation.withDefaultNamespace("optifine/cem/creeper.jem");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static PoseStack.Pose pose() {
        PoseStack stack = new PoseStack();
        stack.translate(0.25F, -1.5F, 0.75F);
        stack.mulPose(new Quaternionf().rotationXYZ(0.3F, -0.7F, 0.2F));
        return stack.last();
    }

    // ------------------------------------------------------------------ geometry

    @Test
    void textureOffsetBoxesMatchVanillaCubesVertexForVertex() {
        for (boolean mirror : new boolean[] {false, true}) {
            ModelPart.Cube vanilla = new ModelPart.Cube(3, 5, -4.0F, -8.0F, -2.0F, 8.0F, 6.0F, 4.0F, 0.5F, 0.25F, 0.0F,
                    mirror, 64.0F, 32.0F, EnumSet.allOf(Direction.class));
            Capture expected = new Capture();
            vanilla.compile(pose(), expected, 15728880, 0, -1);
            PrJem.Box box = new PrJem.Box(-4.0F, -8.0F, -2.0F, 8.0F, 6.0F, 4.0F, 0.5F, 0.25F, 0.0F, 3.0F, 5.0F, null);
            Capture actual = new Capture();
            PrCemGeometry.render(PrCemGeometry.quads(box, 64.0F, 32.0F, mirror).toArray(new PrCemGeometry.Quad[0]),
                    pose(), actual, 15728880, 0, -1);
            assertEquals(expected.vertices.size(), actual.vertices.size());
            for (int i = 0; i < expected.vertices.size(); i++) {
                assertArrayEquals(expected.vertices.get(i), actual.vertices.get(i), 1.0E-6F, "mirror=" + mirror + " vertex " + i);
            }
        }
    }

    @Test
    void faceUvBoxesMapOptifineFaceNamesToVanillaFaces() {
        // Face rectangles equal to what the texture-offset layout gives each face: the geometry must be identical.
        float u = 3;
        float v = 5;
        float w = 8;
        float h = 6;
        float d = 4;
        float[][] faces = {
                {u + d + w + w, v, u + d + w, v + d},     // uvDown: drawn on the model-space up face, reversed
                {u + d + w, v + d, u + d, v},             // uvUp: model-space down face, reversed
                {u + d, v + d, u + d + w, v + d + h},     // uvNorth
                {u + d + w + d, v + d, u + d + w + d + w, v + d + h}, // uvSouth
                {u + d + w, v + d, u + d + w + d, v + d + h},         // uvWest: model-space east face
                {u, v + d, u + d, v + d + h}};            // uvEast: model-space west face
        PrJem.Box offset = new PrJem.Box(-4, -8, -2, w, h, d, 0, 0, 0, u, v, null);
        PrJem.Box byFace = new PrJem.Box(-4, -8, -2, w, h, d, 0, 0, 0, 0, 0, faces);
        List<PrCemGeometry.Quad> a = PrCemGeometry.quads(offset, 64, 32, false);
        List<PrCemGeometry.Quad> b = PrCemGeometry.quads(byFace, 64, 32, false);
        assertEquals(6, b.size());
        for (int i = 0; i < 6; i++) {
            assertArrayEquals(a.get(i).xyz(), b.get(i).xyz(), 1.0E-6F);
            assertArrayEquals(a.get(i).uv(), b.get(i).uv(), 1.0E-6F, "face " + i);
        }
        faces[2] = null;
        assertEquals(5, PrCemGeometry.quads(byFace, 64, 32, false).size(), "a face without a rectangle is not drawn");
    }

    // ------------------------------------------------------------------ parsing

    @Test
    void parsesAClassicOptifineModel() {
        PrJem jem = PrJemParser.parse(json("""
                {
                  "texture": "creeper_custom.png",
                  "textureSize": [64, 32],
                  "shadowSize": 0.75,
                  "models": [
                    {
                      "part": "head", "id": "head", "invertAxis": "xy", "translate": [0, -18, 0],
                      "boxes": [ { "coordinates": [-4, 18, -4, 8, 8, 8], "textureOffset": [0, 0], "sizeAdd": 0.5 } ],
                      "submodels": [ { "id": "snout", "translate": [0, 2, -4], "rotate": [90, 0, 0],
                                       "boxes": [ { "coordinates": [-1, 0, -1, 2, 2, 2.9], "textureOffset": [32, 0] } ] } ],
                      "animations": [ { "head.rx": "torad(head_pitch)", "snout.rx": "sin(age * 0.1)" } ]
                    },
                    { "part": "leg1", "attach": true, "boxes": [ { "coordinates": [0, 0, 0, 1, 1, 1],
                        "uvFront": [0, 0, 1, 1], "uvDown": [1, 1, 2, 2] } ] }
                  ]
                }"""), JEM, loc -> null);
        assertEquals(ResourceLocation.withDefaultNamespace("optifine/cem/creeper_custom.png"), jem.texture(), "bare names sit next to the model");
        assertEquals(0.75F, jem.shadowSize());
        PrJem.Part head = jem.parts().get(0);
        assertEquals("head", head.part());
        assertFalse(head.attach());
        assertEquals(0.0F, head.x(), 0.0F, "negated zero is -0.0, numerically zero");
        assertEquals(18.0F, head.y(), "invertAxis negates translate");
        PrJem.Box box = head.boxes().get(0);
        assertEquals(-4.0F, box.x(), "x = -x - width");
        assertEquals(-26.0F, box.y(), "y = -y - height");
        assertEquals(-4.0F, box.z(), "z is not inverted");
        assertEquals(0.5F, box.growX());
        PrJem.Part snout = head.children().get(0);
        assertEquals((float) Math.PI / 2, snout.xRot(), 1.0E-6F, "degrees become radians");
        assertEquals(2.0F, snout.boxes().get(0).depth(), "texture-offset box sizes are whole pixels");
        assertEquals(64, snout.textureWidth(), "submodels inherit the texture size");
        assertEquals(List.of(Map.of("head.rx", "torad(head_pitch)", "snout.rx", "sin(age * 0.1)")), head.animations());
        PrJem.Part leg = jem.parts().get(1);
        assertTrue(leg.attach());
        float[][] faces = leg.boxes().get(0).faceUvs();
        assertArrayEquals(new float[] {0, 0, 1, 1}, faces[2], "uvFront fills uvNorth");
        assertArrayEquals(new float[] {1, 1, 2, 2}, faces[0]);
        assertNull(faces[1]);
    }

    @Test
    void externalPartsAndBaseIdsCopyMissingKeysOnly() {
        Map<ResourceLocation, JsonObject> files = new HashMap<>();
        files.put(ResourceLocation.withDefaultNamespace("optifine/cem/pig.jpm"), json("""
                { "textureSize": [64, 32], "translate": [1, 1, 1],
                  "boxes": [ { "coordinates": [-12, -12, 4, 8, 8, 8], "uvNorth": [8, 8, 16, 16] } ] }"""));
        PrJem jem = PrJemParser.parse(json("""
                { "models": [
                    { "id": "head", "model": "pig.jpm", "part": "head", "translate": [8, 8, -12] },
                    { "baseId": "head", "part": "body" }
                ] }"""), ResourceLocation.withDefaultNamespace("optifine/cem/pig.jem"), files::get);
        PrJem.Part head = jem.parts().get(0);
        assertEquals(8.0F, head.x(), "the jem's own translate wins over the jpm's");
        assertEquals(1, head.boxes().size(), "boxes come from the jpm");
        PrJem.Part body = jem.parts().get(1);
        assertEquals("body", body.part());
        assertEquals(1, body.boxes().size(), "baseId copies the boxes of the part with that id");
        assertNull(body.id(), "the id itself is never copied");
        assertThrows(JsonParseException.class, () -> PrJemParser.parse(json("{ \"models\": [ { \"model\": \"nope\", \"part\": \"head\" } ] }"),
                JEM, loc -> null), "a missing jpm is an error");
        assertThrows(JsonParseException.class, () -> PrJemParser.parse(json("{ \"models\": [ { \"part\": \"head\", \"boxes\": [ { \"coordinates\": [0,0,0,1,1,1] } ] } ] }"),
                JEM, loc -> null), "a box needs a texture offset or face rectangles");
    }

    @Test
    void resourcePathsFollowOptifineRules() {
        assertEquals(ResourceLocation.withDefaultNamespace("optifine/cem/tex.png"), PrJemParser.resolve(JEM, "optifine/cem", "tex", ".png"));
        assertEquals(ResourceLocation.withDefaultNamespace("optifine/cem/sub/tex.png"), PrJemParser.resolve(JEM, "optifine/cem", "./sub/tex.png", ".png"));
        assertEquals(ResourceLocation.withDefaultNamespace("optifine/other/tex.png"), PrJemParser.resolve(JEM, "optifine/cem", "~/other/tex", ".png"));
        assertEquals(ResourceLocation.withDefaultNamespace("textures/entity/pig.png"), PrJemParser.resolve(JEM, "optifine/cem", "textures/entity/pig", ".png"));
        assertEquals(ResourceLocation.fromNamespaceAndPath("mymod", "textures/a.png"), PrJemParser.resolve(JEM, "optifine/cem", "mymod:textures/a", ".png"));
        assertEquals(ResourceLocation.withDefaultNamespace("optifine/cem/pig.jpm"),
                PrJemParser.resolve(ResourceLocation.withDefaultNamespace("mcpatcher/cem/pig.jem"), "mcpatcher/cem", "./pig", ".jpm"),
                "mcpatcher paths fold onto optifine");
    }

    // ------------------------------------------------------------------ names and part table

    @Test
    void layerNamesFollowTheOptifineScheme() {
        assertEquals(List.of("creeper"), PrCemNames.of("minecraft", "creeper", "main").files());
        assertEquals(List.of("creeper_charge"), PrCemNames.of("minecraft", "creeper", "armor").files());
        assertEquals("sheep_wool", PrCemNames.of("minecraft", "sheep", "fur").mapId());
        assertEquals(List.of("boat"), PrCemNames.of("minecraft", "boat/oak", "main").files());
        assertEquals(List.of("chest_raft"), PrCemNames.of("minecraft", "chest_boat/bamboo", "main").files());
        PrCemNames slim = PrCemNames.of("minecraft", "player_slim", "main");
        assertEquals(List.of("player_slim", "player"), slim.files());
        assertEquals("player", slim.mapId());
        PrCemNames armor = PrCemNames.of("minecraft", "zombie", "inner_armor");
        assertEquals(List.of("zombie_inner_armor", "inner_armor"), armor.files());
        PrCemNames modded = PrCemNames.of("mowziesmobs", "foliaath", "main");
        assertEquals("mowziesmobs", modded.namespace());
        assertEquals(List.of("foliaath", "modded/mowziesmobs/foliaath"), modded.files());
        assertEquals("creeper2", PrCemModels.indexed("creeper", 2));
        assertEquals("cow1.2", PrCemModels.indexed("cow1", 2));
    }

    @Test
    void bundledPartTableCarriesOptifineNames() throws Exception {
        Map<String, Map<String, String>> table = new HashMap<>();
        try (var in = PrCemModels.class.getResourceAsStream("/assets/pryzma/cem/parts.txt")) {
            assertNotNull(in, "parts.txt is packaged");
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).split("\n")) {
                PrCemModels.parsePartLine(line, table);
            }
        }
        assertTrue(table.size() > 200, "models: " + table.size());
        assertEquals("right_hind_leg", table.get("creeper").get("leg1"));
        assertEquals("body0", table.get("spider").get("neck"));
        assertEquals("hat", table.get("zombie").get("headwear"));
        assertEquals("upper_mouth", table.get("horse").get("mouth"));
        assertEquals("head_saddle", table.get("horse").get("headpiece"), "1.21.1 horses still carry saddle parts");
    }

    // ------------------------------------------------------------------ tree building and animations

    /** A small vanilla-like tree: root { body { head, CUBE_EXTRA }, right_hind_leg }. */
    private static ModelPart vanillaTree() {
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, -1, -1, -1, 2, 2, 2, 0, 0, 0, false, 64, 32, EnumSet.allOf(Direction.class));
        ModelPart head = new ModelPart(List.of(cube), Map.of());
        head.setInitialPose(PartPose.offset(0, 6, 0));
        head.loadPose(head.getInitialPose());
        ModelPart extra = new ModelPart(List.of(cube), Map.of());
        Map<String, ModelPart> bodyChildren = new LinkedHashMap<>();
        bodyChildren.put("head", head);
        bodyChildren.put("decoration", extra);
        ModelPart body = new ModelPart(List.of(cube), bodyChildren);
        ModelPart leg = new ModelPart(List.of(cube), Map.of());
        leg.setInitialPose(PartPose.offset(-2, 18, 4));
        leg.loadPose(leg.getInitialPose());
        Map<String, ModelPart> rootChildren = new LinkedHashMap<>();
        rootChildren.put("body", body);
        rootChildren.put("right_hind_leg", leg);
        return new ModelPart(List.of(), rootChildren);
    }

    @Test
    void replacingAndAttachingPartsRebuildTheTree() {
        PrJem jem = PrJemParser.parse(json("""
                { "models": [
                    { "part": "body", "id": "body2", "boxes": [ { "coordinates": [0, 0, 0, 4, 4, 4], "textureOffset": [0, 0] } ] },
                    { "part": "leg1", "attach": true, "id": "leg_extra", "translate": [1, 2, 3],
                      "boxes": [ { "coordinates": [0, 0, 0, 1, 1, 1], "textureOffset": [0, 0] } ] }
                ] }"""), JEM, loc -> null);
        List<String> warnings = new ArrayList<>();
        ModelPart root = PrCemBuilder.build("test", jem, vanillaTree(),
                Map.of("leg1", "right_hind_leg", "head", "head", "body", "body"), warnings::add);
        assertInstanceOf(PrCemPart.class, root);
        assertTrue(warnings.isEmpty(), warnings.toString());
        ModelPart body = root.getChild("body");
        assertTrue(body.cubes.isEmpty(), "a replaced part loses its own boxes");
        assertTrue(body.hasChild("head"), "entity parts below it stay");
        assertFalse(body.hasChild("decoration"), "other children go, as in OptiFine");
        PrCemPart custom = (PrCemPart) body.getChild("CEM-body");
        assertEquals(6, custom.quads().length);
        ModelPart leg = root.getChild("right_hind_leg");
        assertEquals(1, leg.cubes.size(), "attach keeps the vanilla boxes");
        PrCemPart extra = (PrCemPart) leg.getChild("CEM-leg1");
        assertEquals(1.0F, extra.x);
        assertEquals(18.0F, leg.y, "vanilla poses are copied");
        assertSame(custom.model(), extra.model(), "one animation program per tree");
    }

    @Test
    void animationsReadAndWritePartValuesInOrder() {
        PrJem jem = PrJemParser.parse(json("""
                { "models": [
                    { "part": "leg1", "attach": true, "id": "tail", "translate": [0, 0, 0],
                      "boxes": [ { "coordinates": [0, 0, 0, 1, 1, 1], "textureOffset": [0, 0] } ],
                      "animations": [
                        { "leg1.rx": "leg1.rx + 0.5", "this.ty": "leg1.rx * 2", "tail.visible": "leg1.rx > 1",
                          "head.sx": "if(tail.visible, 3, 4)", "leg1:tail.tz": "7" },
                        { "part.tx": "pi" }
                      ] }
                ] }"""), JEM, loc -> null);
        List<String> warnings = new ArrayList<>();
        ModelPart root = PrCemBuilder.build("test", jem, vanillaTree(),
                Map.of("leg1", "right_hind_leg", "head", "head"), warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
        ModelPart leg = root.getChild("right_hind_leg");
        ModelPart tail = leg.getChild("CEM-leg1");
        ModelPart head = root.getChild("body").getChild("head");
        leg.xRot = 0.25F;
        ((PrCemPart) tail).model().run();
        assertEquals(0.75F, leg.xRot, 1.0E-6F);
        assertEquals(1.5F, tail.y, 1.0E-6F, "later assignments see earlier results");
        assertFalse(tail.visible);
        assertEquals(4.0F, head.xScale);
        assertEquals(7.0F, tail.z, "part:id paths reach custom parts");
        assertEquals((float) Math.PI, leg.x, 1.0E-6F, "'part' is the entity part the model attaches to");
        ((PrCemPart) tail).model().run();
        assertTrue(tail.visible, "second run: 1.25 > 1");
        assertEquals(3.0F, head.xScale);
    }

    @Test
    void badAnimationsAreSkippedNotFatal() {
        PrJem jem = PrJemParser.parse(json("""
                { "models": [
                    { "part": "head", "attach": true, "boxes": [ { "coordinates": [0, 0, 0, 1, 1, 1], "textureOffset": [0, 0] } ],
                      "animations": [ { "head.rx": "nosuch(1)", "head.ry": "0.5", "nothing.rz": "1" } ] }
                ] }"""), JEM, loc -> null);
        List<String> warnings = new ArrayList<>();
        ModelPart root = PrCemBuilder.build("test", jem, vanillaTree(), Map.of("head", "head"), warnings::add);
        assertEquals(2, warnings.size(), warnings.toString());
        ModelPart head = root.getChild("body").getChild("head");
        ((PrCemPart) head).model().run();
        assertEquals(0.5F, head.yRot);
        assertEquals(1, ((PrCemPart) head).model().size());
    }

    @Test
    void unknownPartsLeaveTheVanillaTree() {
        PrJem jem = PrJemParser.parse(json("{ \"models\": [ { \"part\": \"wing\", \"boxes\": [] } ] }"), JEM, loc -> null);
        ModelPart vanilla = vanillaTree();
        List<String> warnings = new ArrayList<>();
        assertSame(vanilla, PrCemBuilder.build("test", jem, vanilla, Map.of(), warnings::add));
        assertEquals(1, warnings.size());
    }
}
