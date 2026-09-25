package net.pryzma.core.expr;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.Mth;
import net.pryzma.core.PrHash;

/** Compiles a typed call of a {@link PrFunc} into an evaluating closure. */
final class PrFunctions {
    private static final Map<String, Integer> PRINT_FRAMES = new HashMap<>();

    private PrFunctions() {
    }

    static PrExpr build(PrFunc f, PrExpr[] args, PrExprEnv env) {
        PrExpr.F a = args.length > 0 && args[0] instanceof PrExpr.F x ? x : null;
        PrExpr.F b = args.length > 1 && args[1] instanceof PrExpr.F x ? x : null;
        PrExpr.F c = args.length > 2 && args[2] instanceof PrExpr.F x ? x : null;
        PrExpr.B p = args.length > 0 && args[0] instanceof PrExpr.B x ? x : null;
        PrExpr.B q = args.length > 1 && args[1] instanceof PrExpr.B x ? x : null;
        return switch (f) {
            case PLUS -> (PrExpr.F) () -> a.eval() + b.eval();
            case MINUS -> (PrExpr.F) () -> a.eval() - b.eval();
            case MUL -> (PrExpr.F) () -> a.eval() * b.eval();
            case DIV -> (PrExpr.F) () -> a.eval() / b.eval();
            case MOD -> (PrExpr.F) () -> {
                // Truncating remainder as OptiFine computes it: x - y * (int) (x / y).
                float x = a.eval();
                float y = b.eval();
                return x - y * (float) (int) (x / y);
            };
            case NEG -> (PrExpr.F) () -> -a.eval();
            case PI -> (PrExpr.F) () -> (float) Math.PI;
            case SIN -> (PrExpr.F) () -> Mth.sin(a.eval());
            case COS -> (PrExpr.F) () -> Mth.cos(a.eval());
            case ASIN -> (PrExpr.F) () -> (float) Math.asin(a.eval());
            case ACOS -> (PrExpr.F) () -> (float) Math.acos(a.eval());
            case TAN -> (PrExpr.F) () -> (float) Math.tan(a.eval());
            case ATAN -> (PrExpr.F) () -> (float) Math.atan(a.eval());
            case ATAN2 -> (PrExpr.F) () -> (float) Mth.atan2(a.eval(), b.eval());
            case TORAD -> (PrExpr.F) () -> PrExprParser.toRad(a.eval());
            case TODEG -> (PrExpr.F) () -> PrExprParser.toDeg(a.eval());
            case MIN -> minMax(args, true);
            case MAX -> minMax(args, false);
            case CLAMP -> (PrExpr.F) () -> Mth.clamp(a.eval(), b.eval(), c.eval());
            case ABS -> (PrExpr.F) () -> Math.abs(a.eval());
            case FLOOR -> (PrExpr.F) () -> (float) Mth.floor(a.eval());
            case CEIL -> (PrExpr.F) () -> (float) Mth.ceil(a.eval());
            case EXP -> (PrExpr.F) () -> (float) Math.exp(a.eval());
            case FRAC -> (PrExpr.F) () -> Mth.frac(a.eval());
            case LOG -> (PrExpr.F) () -> (float) Math.log(a.eval());
            case POW -> (PrExpr.F) () -> (float) Math.pow(a.eval(), b.eval());
            case RANDOM -> a == null
                    ? (PrExpr.F) () -> (float) Math.random()
                    : (PrExpr.F) () -> Math.abs(PrHash.intHash(Float.floatToIntBits(a.eval()))) / 2.1474836E9F;
            case ROUND -> (PrExpr.F) () -> (float) Math.round(a.eval());
            case SIGNUM -> (PrExpr.F) () -> Math.signum(a.eval());
            case SQRT -> (PrExpr.F) () -> (float) Math.sqrt(a.eval());
            case FMOD -> (PrExpr.F) () -> {
                float x = a.eval();
                float y = b.eval();
                return x - y * (float) Mth.floor(x / y);
            };
            case LERP -> (PrExpr.F) () -> Mth.lerp(a.eval(), b.eval(), c.eval());
            case TIME -> (PrExpr.F) env::time;
            case DAY_TIME -> (PrExpr.F) env::dayTime;
            case DAY_COUNT -> (PrExpr.F) env::dayCount;
            case PRINT -> (PrExpr.F) () -> {
                float value = c.eval();
                if (printDue("print" + (int) a.eval(), (int) b.eval(), env)) {
                    env.print("CEM print(" + (int) a.eval() + ") = " + value);
                }
                return value;
            };
            case PRINTB -> {
                PrExpr.B v = (PrExpr.B) args[2];
                yield (PrExpr.B) () -> {
                    boolean value = v.eval();
                    if (printDue("printb" + (int) a.eval(), (int) b.eval(), env)) {
                        env.print("CEM printb(" + (int) a.eval() + ") = " + value);
                    }
                    return value;
                };
            }
            case IF -> {
                int checks = (args.length - 1) / 2;
                PrExpr.B[] conditions = new PrExpr.B[checks];
                PrExpr.F[] values = new PrExpr.F[checks];
                for (int i = 0; i < checks; i++) {
                    conditions[i] = (PrExpr.B) args[i * 2];
                    values[i] = (PrExpr.F) args[i * 2 + 1];
                }
                PrExpr.F otherwise = (PrExpr.F) args[args.length - 1];
                yield (PrExpr.F) () -> {
                    for (int i = 0; i < conditions.length; i++) {
                        if (conditions[i].eval()) {
                            return values[i].eval();
                        }
                    }
                    return otherwise.eval();
                };
            }
            case IFB -> {
                int checks = (args.length - 1) / 2;
                PrExpr.B[] conditions = new PrExpr.B[checks];
                PrExpr.B[] values = new PrExpr.B[checks];
                for (int i = 0; i < checks; i++) {
                    conditions[i] = (PrExpr.B) args[i * 2];
                    values[i] = (PrExpr.B) args[i * 2 + 1];
                }
                PrExpr.B otherwise = (PrExpr.B) args[args.length - 1];
                yield (PrExpr.B) () -> {
                    for (int i = 0; i < conditions.length; i++) {
                        if (conditions[i].eval()) {
                            return values[i].eval();
                        }
                    }
                    return otherwise.eval();
                };
            }
            case NOT -> (PrExpr.B) () -> !p.eval();
            case AND -> (PrExpr.B) () -> p.eval() && q.eval();
            case OR -> (PrExpr.B) () -> p.eval() || q.eval();
            case GREATER -> (PrExpr.B) () -> a.eval() > b.eval();
            case GREATER_OR_EQUAL -> (PrExpr.B) () -> a.eval() >= b.eval();
            case SMALLER -> (PrExpr.B) () -> a.eval() < b.eval();
            case SMALLER_OR_EQUAL -> (PrExpr.B) () -> a.eval() <= b.eval();
            case EQUAL -> (PrExpr.B) () -> a.eval() == b.eval();
            case NOT_EQUAL -> (PrExpr.B) () -> a.eval() != b.eval();
            case BETWEEN -> (PrExpr.B) () -> {
                float v = a.eval();
                return v >= b.eval() && v <= c.eval();
            };
            case EQUALS -> (PrExpr.B) () -> Math.abs(a.eval() - b.eval()) <= c.eval();
            case IN -> {
                PrExpr.F[] candidates = new PrExpr.F[args.length - 1];
                for (int i = 1; i < args.length; i++) {
                    candidates[i - 1] = (PrExpr.F) args[i];
                }
                yield (PrExpr.B) () -> {
                    float v = a.eval();
                    for (PrExpr.F candidate : candidates) {
                        if (v == candidate.eval()) {
                            return true;
                        }
                    }
                    return false;
                };
            }
            case SMOOTH -> smooth(args, env);
            case TRUE -> (PrExpr.B) () -> true;
            case FALSE -> (PrExpr.B) () -> false;
            case VEC2 -> (PrExpr.V) () -> new float[] {a.eval(), b.eval()};
            case VEC3 -> (PrExpr.V) () -> new float[] {a.eval(), b.eval(), c.eval()};
            case VEC4 -> {
                PrExpr.F d = (PrExpr.F) args[3];
                yield (PrExpr.V) () -> new float[] {a.eval(), b.eval(), c.eval(), d.eval()};
            }
        };
    }

    private static PrExpr.F minMax(PrExpr[] args, boolean min) {
        PrExpr.F[] values = new PrExpr.F[args.length];
        for (int i = 0; i < args.length; i++) {
            values[i] = (PrExpr.F) args[i];
        }
        return () -> {
            float best = values[0].eval();
            for (int i = 1; i < values.length; i++) {
                float v = values[i].eval();
                if (min ? v < best : v > best) {
                    best = v;
                }
            }
            return best;
        };
    }

    /**
     * {@code smooth([id,] value[, fadeUp[, fadeDown]])}: a number literal as first argument is an
     * explicit id; otherwise the call gets its own id and the arguments shift by one, as in OptiFine.
     */
    private static PrExpr.F smooth(PrExpr[] args, PrExprEnv env) {
        if (args[0] instanceof PrExpr.Constant id && args.length >= 2) {
            PrExpr.F value = (PrExpr.F) args[1];
            PrExpr.F up = args.length > 2 ? (PrExpr.F) args[2] : null;
            PrExpr.F down = args.length > 3 ? (PrExpr.F) args[3] : up;
            int key = (int) id.value();
            return () -> {
                float fadeUp = up == null ? 1.0F : up.eval();
                return PrSmoother.smooth(key, value.eval(), fadeUp, down == null ? fadeUp : down.eval(), env.millis());
            };
        }
        PrExpr.F value = (PrExpr.F) args[0];
        PrExpr.F up = args.length > 1 ? (PrExpr.F) args[1] : null;
        PrExpr.F down = args.length > 2 ? (PrExpr.F) args[2] : up;
        int key = PrSmoother.nextId();
        return () -> {
            float fadeUp = up == null ? 1.0F : up.eval();
            return PrSmoother.smooth(key, value.eval(), fadeUp, down == null ? fadeUp : down.eval(), env.millis());
        };
    }

    private static boolean printDue(String key, int frames, PrExprEnv env) {
        synchronized (PRINT_FRAMES) {
            int now = env.frameCounter();
            Integer last = PRINT_FRAMES.get(key);
            if (last != null && now - last < Math.max(1, frames)) {
                return false;
            }
            PRINT_FRAMES.put(key, now);
            return true;
        }
    }
}
