package net.pryzma.shader.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ShaderConfigLegacyTest {
    private static Properties parse(String text) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }

    @Test
    void selectedPackCarriesOver() throws IOException {
        // The Prism test instance's optionsshaders.txt, abridged.
        Properties legacy = parse("antialiasingLevel=0\nshaderPack=ComplementaryUnbound_r5.9.zip\nshadowResMul=1.0\n");
        assertEquals(Optional.of("ComplementaryUnbound_r5.9.zip"), ShaderConfig.legacyPackSelection(legacy));
    }

    @Test
    void offInternalBlankAndMissingMeanNoPack() throws IOException {
        assertEquals(Optional.empty(), ShaderConfig.legacyPackSelection(parse("shaderPack=OFF\n")));
        assertEquals(Optional.empty(), ShaderConfig.legacyPackSelection(parse("shaderPack=(internal)\n")));
        assertEquals(Optional.empty(), ShaderConfig.legacyPackSelection(parse("shaderPack=\n")));
        assertEquals(Optional.empty(), ShaderConfig.legacyPackSelection(parse("renderResMul=1.0\n")));
    }

    @Test
    void surroundingWhitespaceIsIgnored() throws IOException {
        assertEquals(Optional.of("Pack Name.zip"), ShaderConfig.legacyPackSelection(parse("shaderPack = Pack Name.zip  \n")));
    }
}
