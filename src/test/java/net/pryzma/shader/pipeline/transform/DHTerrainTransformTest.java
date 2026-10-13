package net.pryzma.shader.pipeline.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import net.pryzma.shader.pipeline.transform.transformer.DHTerrainTransformer;

/**
 * The Farlands LOD's cut of the vanilla radius in a shader pack's Distant Horizons fragment stage
 * ({@link DHTerrainTransformer#injectLodClip}): the injected GLSL parses, declares its uniforms once, and runs first
 * in {@code main}, before the pack's own code can write anything. The full {@code TransformPatcher} needs the running
 * game (its shader configuration), so the injection is exercised on its own here. Whitespace and float suffixes are
 * ignored: the printer formats the output its own way.
 */
class DHTerrainTransformTest {
    /** A fragment stage as the Distant Horizons transformer leaves it before the cut: core GLSL, Iris matrices. */
    private static final String FRAGMENT = """
            #version 330 core
            uniform mat4 iris_ProjectionMatrixInverse;
            uniform mat4 iris_ModelViewMatrixInverse;
            uniform sampler2D lightmap;
            in vec4 color;
            in vec2 lmcoord;
            layout(location = 0) out vec4 iris_FragData0;
            void main() {
                vec4 lit = color * texture(lightmap, lmcoord);
                if (lit.a < 0.1) {
                    discard;
                }
                iris_FragData0 = lit;
            }
            """;

    /** The fragment stage with the cut injected, without whitespace. */
    private static String inject(String source) {
        SingleASTTransformer<JobParameters> transformer = new SingleASTTransformer<>();
        transformer.setTransformation(tree -> DHTerrainTransformer.injectLodClip(transformer, tree));
        return compact(transformer.transform(source));
    }

    /** Without whitespace and with float literals unsuffixed: the printer writes {@code 2.0} as {@code 2.0f}. */
    private static String compact(String code) {
        return code.replaceAll("\\s+", "").replaceAll("(\\d)f\\b", "$1");
    }

    @Test
    void theCutIsDeclaredAndRunsFirstInMain() {
        String out = inject(FRAGMENT);
        assertEquals(1, count(out, compact("uniform float pryzma_LodClip;")), out);
        assertEquals(1, count(out, compact("uniform vec2 pryzma_LodInvViewport;")), out);
        int main = out.indexOf(compact("void main() {"));
        int position = out.indexOf(compact("vec4 pryzma_lodPos = iris_ProjectionMatrixInverse *"));
        int toPlayer = out.indexOf(compact("pryzma_lodPos = iris_ModelViewMatrixInverse * (pryzma_lodPos / pryzma_lodPos.w);"));
        int cut = out.indexOf(compact("if (dot(pryzma_lodPos.xz, pryzma_lodPos.xz) < pryzma_LodClip * pryzma_LodClip) discard;"));
        int packCode = out.indexOf(compact("vec4 lit = color * texture(lightmap, lmcoord);"));
        assertTrue(main >= 0, out);
        assertEquals(main + compact("void main() {").length(), position, "the screen position is unprojected first in main: " + out);
        assertTrue(toPlayer > position, "then moved to player space: " + out);
        assertTrue(cut > toPlayer, "then tested against the clip radius, and discarded: " + out);
        assertTrue(packCode > cut, "before any of the pack's code: " + out);
        assertTrue(out.indexOf(compact("uniform float pryzma_LodClip;")) < main, "declared before main: " + out);
    }

    @Test
    void theCutUnprojectsTheFragmentWithItsDepth() {
        String out = inject(FRAGMENT);
        assertTrue(out.contains(compact("gl_FragCoord.xy * pryzma_LodInvViewport * 2.0 - 1.0")), "window to NDC x, y: " + out);
        assertTrue(out.contains(compact("gl_FragCoord.z * 2.0 - 1.0")), "window depth to NDC z: " + out);
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            n++;
        }
        return n;
    }
}
