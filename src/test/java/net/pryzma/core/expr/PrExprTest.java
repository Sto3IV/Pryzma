package net.pryzma.core.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.pryzma.core.PrHash;

class PrExprTest {
    /** A controllable world: fixed time values and a manual clock. */
    static final class TestEnv implements PrExprEnv {
        long millis;
        int frames;
        final List<String> printed = new ArrayList<>();

        @Override
        public float time() {
            return 1234.5F;
        }

        @Override
        public float dayTime() {
            return 6000.25F;
        }

        @Override
        public float dayCount() {
            return 3.0F;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public int frameCounter() {
            return frames;
        }

        @Override
        public void print(String message) {
            printed.add(message);
        }
    }

    private final Map<String, float[]> vars = new HashMap<>();
    private final TestEnv env = new TestEnv();
    private final PrExprParser parser = new PrExprParser(name -> {
        float[] cell = vars.get(name);
        if (cell != null) {
            return (PrExpr.F) () -> cell[0];
        }
        return "is_child".equals(name) ? (PrExpr.B) () -> vars.containsKey("child") : null;
    }, env);

    private float f(String text) throws PrExprException {
        return parser.parseFloat(text).eval();
    }

    private boolean b(String text) throws PrExprException {
        return parser.parseBool(text).eval();
    }

    @Test
    void precedenceAndUnaryOperators() throws PrExprException {
        assertEquals(7.0F, f("1 + 2 * 3"));
        assertEquals(9.0F, f("(1 + 2) * 3"));
        assertEquals(-6.0F, f("-2 * 3"));
        assertEquals(5.0F, f("2 - -3"));
        assertEquals(5.0F, f("2--3"), "operator tokens only continue with & | =");
        assertEquals(2.0F, f("10 / 5"));
        assertEquals(-4.0F, f("-(1 + 3)"));
        assertEquals(1.0F, f("8 - 4 - 3"), "left associative");
    }

    @Test
    void modTruncatesAndFmodFloors() throws PrExprException {
        assertEquals(1.0F, f("7 % 3"));
        assertEquals(-1.0F, f("-7 % 3"), "OptiFine % truncates towards zero");
        assertEquals(2.0F, f("fmod(-7, 3)"), "fmod floors");
        assertEquals(0.25F, f("frac(2.25)"));
    }

    @Test
    void comparisonsAndBooleanLogic() throws PrExprException {
        assertTrue(b("1 < 2 && !(3 >= 4)"));
        assertTrue(b("1 == 1 || 1 != 1"));
        assertFalse(b("true && false"));
        assertTrue(b("between(5, 1, 10)"));
        assertFalse(b("between(11, 1, 10)"));
        assertTrue(b("equals(1.0, 1.05, 0.1)"));
        assertTrue(b("in(3, 1, 2, 3)"));
        assertFalse(b("in(4, 1, 2, 3)"));
        assertTrue(b("ifb(false, false, true)"));
    }

    @Test
    void ifChainsPickTheFirstTrueBranch() throws PrExprException {
        assertEquals(20.0F, f("if(1 > 2, 10, 20)"));
        assertEquals(30.0F, f("if(1 > 2, 10, 2 == 2, 30, 40)"));
        assertEquals(40.0F, f("if(false, 10, false, 30, 40)"));
    }

    @Test
    void typesAreStrictAsInOptifine() {
        assertThrows(PrExprException.class, () -> f("if(1, 2, 3)"), "condition must be boolean");
        assertThrows(PrExprException.class, () -> f("1 + (2 > 1)"), "no bool to float conversion");
        assertThrows(PrExprException.class, () -> f("if(true, 1, 2, 3)"), "if needs an odd argument count");
        assertThrows(PrExprException.class, () -> f("sin()"));
        assertThrows(PrExprException.class, () -> f("foo(1)"), "unknown function");
        assertThrows(PrExprException.class, () -> f("nothing.rx"), "unknown variable");
        assertThrows(PrExprException.class, () -> f("min"), "min needs arguments");
        assertThrows(PrExprException.class, () -> f("(1 + 2"));
        assertThrows(PrExprException.class, () -> f("1 +"));
        assertThrows(PrExprException.class, () -> f("1 # 2"));
        assertThrows(PrExprException.class, () -> b("1 + 2"), "float is not boolean");
    }

    @Test
    void variablesResolveThroughTheResolver() throws PrExprException {
        vars.put("head.rx", new float[] {0.5F});
        PrExpr.F e = parser.parseFloat("head.rx * 2 + torad(180)");
        assertEquals(1.0F + (float) Math.PI, e.eval(), 1.0E-6F);
        vars.get("head.rx")[0] = 1.5F;
        assertEquals(3.0F + (float) Math.PI, e.eval(), 1.0E-6F, "variables are read at evaluation time");
        assertFalse(b("is_child"));
        vars.put("child", new float[] {1});
        assertTrue(b("is_child"));
    }

    @Test
    void constantsFoldButVariablesDoNot() throws PrExprException {
        assertInstanceOf(PrExprParser.Folded.class, parser.parse("pi * 2 + sin(0)"));
        vars.put("x", new float[] {1});
        assertFalse(parser.parse("x * 2") instanceof PrExprParser.Folded);
        assertInstanceOf(PrExprParser.FoldedBool.class, parser.parse("!(1 > 2)"));
    }

    @Test
    void mathMatchesOptifineDefinitions() throws PrExprException {
        assertEquals((float) Math.PI, f("torad(180)"));
        assertEquals(180.0F, f("todeg(pi)"), 1.0E-4F);
        assertEquals(3.0F, f("clamp(5, 1, 3)"));
        assertEquals(1.0F, f("min(4, 1, 3)"));
        assertEquals(4.0F, f("max(4, 1, 3)"));
        assertEquals(7.0F, f("min(7)"));
        assertEquals(2.5F, f("lerp(0.5, 2, 3)"));
        assertEquals(-1.0F, f("signum(-5)"));
        assertEquals(3.0F, f("round(2.5)"));
        assertEquals(-2.0F, f("floor(-1.5)"));
        assertEquals(3.0F, f("sqrt(9)"));
        assertEquals(8.0F, f("pow(2, 3)"));
    }

    @Test
    void seededRandomIsStableAndInRange() throws PrExprException {
        float r1 = f("random(42)");
        assertEquals(r1, f("random(42)"));
        assertTrue(r1 >= 0.0F && r1 <= 1.0F);
        assertTrue(f("random(43)") != r1);
        float unseeded = f("random");
        assertTrue(unseeded >= 0.0F && unseeded < 1.0F);
    }

    @Test
    void worldFunctionsReadTheEnvironment() throws PrExprException {
        assertEquals(1234.5F, f("time"));
        assertEquals(6000.25F, f("day_time"));
        assertEquals(3.0F, f("day_count"));
    }

    @Test
    void smoothApproachesTheTargetOverTheFadeTime() throws PrExprException {
        PrSmoother.reset();
        vars.put("target", new float[] {0.0F});
        PrExpr.F e = parser.parseFloat("smooth(77, target, 1, 1)");
        env.millis = 1000;
        assertEquals(0.0F, e.eval(), "first sample initialises");
        vars.get("target")[0] = 10.0F;
        env.millis = 1100;
        float step1 = e.eval();
        assertTrue(step1 > 0.0F && step1 < 10.0F, "one tenth of the fade time moves part of the way: " + step1);
        env.millis = 3100;
        assertEquals(10.0F, e.eval(), "past the fade time the target is reached");
        // Without a literal id each call keeps its own state.
        PrExpr.F auto1 = parser.parseFloat("smooth(target)");
        PrExpr.F auto2 = parser.parseFloat("smooth(target * 2)");
        assertEquals(10.0F, auto1.eval());
        assertEquals(20.0F, auto2.eval());
    }

    @Test
    void printThrottlesByFrames() throws PrExprException {
        PrExpr.F e = parser.parseFloat("print(1, 10, 5)");
        env.frames = 100;
        assertEquals(5.0F, e.eval());
        e.eval();
        assertEquals(1, env.printed.size(), "second print in the same frame window is suppressed");
        env.frames = 111;
        e.eval();
        assertEquals(2, env.printed.size());
    }

    @Test
    void vectorsBuildFromFloats() throws PrExprException {
        PrExpr e = parser.parse("vec3(1, 2 * 2, pi)");
        assertInstanceOf(PrExpr.V.class, e);
        float[] v = ((PrExpr.V) e).eval();
        assertEquals(3, v.length);
        assertEquals(4.0F, v[1]);
    }

    @Test
    void optifineIntHashMatchesReferenceValues() throws PrExprException {
        // Values of OptiFine's Config.intHash, evaluated once in jshell from the decompiled formula.
        assertEquals(1062685034, PrHash.intHash(0));
        assertEquals(663891101, PrHash.intHash(1));
        assertEquals(1462734105, PrHash.intHash(42));
        assertEquals(1902946834, PrHash.intHash(-7));
        assertEquals(1742141682 / 2.1474836E9F, f("random(42)"), "random(seed) = |intHash(bits(seed))| / 2.1474836E9");
    }
}
