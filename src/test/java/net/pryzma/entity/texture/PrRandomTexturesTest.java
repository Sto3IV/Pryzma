package net.pryzma.entity.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class PrRandomTexturesTest {
    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    @Test
    void randomFilesMapBackToTheirTexture() {
        assertEquals(mc("textures/entity/creeper/creeper.png"), PrRandomTextures.baseTexture(mc("optifine/random/entity/creeper/creeper2.png")));
        assertEquals(mc("textures/entity/creeper/creeper.png"), PrRandomTextures.baseTexture(mc("optifine/random/entity/creeper/creeper.properties")));
        assertEquals(mc("textures/entity/villager/villager1.png"), PrRandomTextures.baseTexture(mc("optifine/random/entity/villager/villager1.3.png")),
                "a name ending in a digit uses a dot before the variant number");
        assertEquals(mc("textures/entity/creeper/creeper.png"), PrRandomTextures.baseTexture(mc("optifine/mob/creeper/creeper3.png")),
                "optifine/mob/ is the MCPatcher layout under textures/entity/");
        assertEquals(mc("textures/entity/creeper/creeper.png"), PrRandomTextures.baseTexture(mc("mcpatcher/mob/creeper/creeper3.png")),
                "mcpatcher/ is read as optifine/");
        assertEquals(ResourceLocation.fromNamespaceAndPath("mowziesmobs", "textures/entity/foliaath.png"),
                PrRandomTextures.baseTexture(ResourceLocation.fromNamespaceAndPath("mowziesmobs", "optifine/random/entity/foliaath3.png")));
        assertNull(PrRandomTextures.baseTexture(mc("optifine/cit/sword.png")));
    }

    @Test
    void variantsAreNumberedLikeOptifine() {
        assertEquals(mc("optifine/random/entity/a2.png"), PrRandomTextures.indexed(mc("optifine/random/entity/a.png"), 2));
        assertEquals(mc("optifine/random/entity/a1.2.png"), PrRandomTextures.indexed(mc("optifine/random/entity/a1.png"), 2));
        assertEquals(mc("optifine/random/entity/creeper/creeper.png"),
                PrRandomTextures.randomLocation(mc("textures/entity/creeper/creeper.png"), false));
        assertEquals(mc("optifine/mob/creeper/creeper.png"), PrRandomTextures.randomLocation(mc("textures/entity/creeper/creeper.png"), true));
        assertEquals(mc("optifine/random/painting/kebab.png"), PrRandomTextures.randomLocation(mc("textures/painting/kebab.png"), false),
                "the modern layout covers every texture, not only entities");
        assertNull(PrRandomTextures.randomLocation(mc("textures/painting/kebab.png"), true));
    }
}
