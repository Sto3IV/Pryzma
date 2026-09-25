package net.pryzma.core.expr;

/**
 * A compiled OptiFine expression (CEM animations, custom shader uniforms). Expressions are typed
 * exactly as in OptiFine: float, boolean or float vector, with no implicit conversion between them.
 */
public sealed interface PrExpr permits PrExpr.F, PrExpr.B, PrExpr.V {
    enum Type {
        FLOAT, BOOL, VEC
    }

    Type type();

    @FunctionalInterface
    non-sealed interface F extends PrExpr {
        float eval();

        @Override
        default Type type() {
            return Type.FLOAT;
        }
    }

    @FunctionalInterface
    non-sealed interface B extends PrExpr {
        boolean eval();

        @Override
        default Type type() {
            return Type.BOOL;
        }
    }

    @FunctionalInterface
    non-sealed interface V extends PrExpr {
        float[] eval();

        @Override
        default Type type() {
            return Type.VEC;
        }
    }

    /** A number literal; {@code smooth()} treats a literal first argument as an explicit id. */
    record Constant(float value) implements F {
        @Override
        public float eval() {
            return value;
        }
    }
}
