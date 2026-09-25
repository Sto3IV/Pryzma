package net.pryzma.core.expr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.pryzma.core.expr.PrExpr.Type;

/**
 * The built-in functions and operators of the OptiFine expression language with their signatures:
 * fixed leading parameters, a repeated group and fixed trailing parameters, optionally capped.
 */
enum PrFunc {
    PLUS(10, Type.FLOAT, "+", sig(Type.FLOAT, Type.FLOAT)),
    MINUS(10, Type.FLOAT, "-", sig(Type.FLOAT, Type.FLOAT)),
    MUL(11, Type.FLOAT, "*", sig(Type.FLOAT, Type.FLOAT)),
    DIV(11, Type.FLOAT, "/", sig(Type.FLOAT, Type.FLOAT)),
    MOD(11, Type.FLOAT, "%", sig(Type.FLOAT, Type.FLOAT)),
    NEG(12, Type.FLOAT, "neg", sig(Type.FLOAT)),
    PI(Type.FLOAT, "pi", sig()),
    SIN(Type.FLOAT, "sin", sig(Type.FLOAT)),
    COS(Type.FLOAT, "cos", sig(Type.FLOAT)),
    ASIN(Type.FLOAT, "asin", sig(Type.FLOAT)),
    ACOS(Type.FLOAT, "acos", sig(Type.FLOAT)),
    TAN(Type.FLOAT, "tan", sig(Type.FLOAT)),
    ATAN(Type.FLOAT, "atan", sig(Type.FLOAT)),
    ATAN2(Type.FLOAT, "atan2", sig(Type.FLOAT, Type.FLOAT)),
    TORAD(Type.FLOAT, "torad", sig(Type.FLOAT)),
    TODEG(Type.FLOAT, "todeg", sig(Type.FLOAT)),
    MIN(Type.FLOAT, "min", var(new Type[] {Type.FLOAT}, new Type[] {Type.FLOAT}, new Type[0], Integer.MAX_VALUE)),
    MAX(Type.FLOAT, "max", var(new Type[] {Type.FLOAT}, new Type[] {Type.FLOAT}, new Type[0], Integer.MAX_VALUE)),
    CLAMP(Type.FLOAT, "clamp", sig(Type.FLOAT, Type.FLOAT, Type.FLOAT)),
    ABS(Type.FLOAT, "abs", sig(Type.FLOAT)),
    FLOOR(Type.FLOAT, "floor", sig(Type.FLOAT)),
    CEIL(Type.FLOAT, "ceil", sig(Type.FLOAT)),
    EXP(Type.FLOAT, "exp", sig(Type.FLOAT)),
    FRAC(Type.FLOAT, "frac", sig(Type.FLOAT)),
    LOG(Type.FLOAT, "log", sig(Type.FLOAT)),
    POW(Type.FLOAT, "pow", sig(Type.FLOAT, Type.FLOAT)),
    RANDOM(Type.FLOAT, "random", var(new Type[0], new Type[] {Type.FLOAT}, new Type[0], 1), false),
    ROUND(Type.FLOAT, "round", sig(Type.FLOAT)),
    SIGNUM(Type.FLOAT, "signum", sig(Type.FLOAT)),
    SQRT(Type.FLOAT, "sqrt", sig(Type.FLOAT)),
    FMOD(Type.FLOAT, "fmod", sig(Type.FLOAT, Type.FLOAT)),
    LERP(Type.FLOAT, "lerp", sig(Type.FLOAT, Type.FLOAT, Type.FLOAT)),
    TIME(Type.FLOAT, "time", sig(), false),
    DAY_TIME(Type.FLOAT, "day_time", sig(), false),
    DAY_COUNT(Type.FLOAT, "day_count", sig(), false),
    PRINT(Type.FLOAT, "print", sig(Type.FLOAT, Type.FLOAT, Type.FLOAT), false),
    PRINTB(Type.BOOL, "printb", sig(Type.FLOAT, Type.FLOAT, Type.BOOL), false),
    IF(Type.FLOAT, "if", var(new Type[] {Type.BOOL, Type.FLOAT}, new Type[] {Type.BOOL, Type.FLOAT}, new Type[] {Type.FLOAT}, Integer.MAX_VALUE)),
    NOT(12, Type.BOOL, "!", sig(Type.BOOL)),
    AND(3, Type.BOOL, "&&", sig(Type.BOOL, Type.BOOL)),
    OR(2, Type.BOOL, "||", sig(Type.BOOL, Type.BOOL)),
    GREATER(8, Type.BOOL, ">", sig(Type.FLOAT, Type.FLOAT)),
    GREATER_OR_EQUAL(8, Type.BOOL, ">=", sig(Type.FLOAT, Type.FLOAT)),
    SMALLER(8, Type.BOOL, "<", sig(Type.FLOAT, Type.FLOAT)),
    SMALLER_OR_EQUAL(8, Type.BOOL, "<=", sig(Type.FLOAT, Type.FLOAT)),
    EQUAL(7, Type.BOOL, "==", sig(Type.FLOAT, Type.FLOAT)),
    NOT_EQUAL(7, Type.BOOL, "!=", sig(Type.FLOAT, Type.FLOAT)),
    BETWEEN(7, Type.BOOL, "between", sig(Type.FLOAT, Type.FLOAT, Type.FLOAT)),
    EQUALS(7, Type.BOOL, "equals", sig(Type.FLOAT, Type.FLOAT, Type.FLOAT)),
    IN(Type.BOOL, "in", var(new Type[] {Type.FLOAT}, new Type[] {Type.FLOAT}, new Type[] {Type.FLOAT}, Integer.MAX_VALUE)),
    SMOOTH(Type.FLOAT, "smooth", var(new Type[] {Type.FLOAT}, new Type[] {Type.FLOAT}, new Type[0], 4), false),
    TRUE(Type.BOOL, "true", sig()),
    FALSE(Type.BOOL, "false", sig()),
    IFB(Type.BOOL, "ifb", var(new Type[] {Type.BOOL, Type.BOOL}, new Type[] {Type.BOOL, Type.BOOL}, new Type[] {Type.BOOL}, Integer.MAX_VALUE)),
    VEC2(Type.VEC, "vec2", sig(Type.FLOAT, Type.FLOAT)),
    VEC3(Type.VEC, "vec3", sig(Type.FLOAT, Type.FLOAT, Type.FLOAT)),
    VEC4(Type.VEC, "vec4", sig(Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT));

    /** A parameter list: fixed {@code first}, {@code repeat} as often as fits, fixed {@code last}. */
    record Signature(Type[] first, Type[] repeat, Type[] last, int maxCount) {
        Type[] typesFor(int argCount) {
            int fixed = first.length + last.length;
            int variable = argCount - fixed;
            int repeats = 0;
            if (repeat.length > 0) {
                for (int used = 0; used + repeat.length <= variable && fixed + used + repeat.length <= maxCount; used += repeat.length) {
                    repeats++;
                }
            }
            List<Type> out = new ArrayList<>(List.of(first));
            for (int i = 0; i < repeats; i++) {
                out.addAll(List.of(repeat));
            }
            out.addAll(List.of(last));
            return out.toArray(new Type[0]);
        }
    }

    private static final Map<String, PrFunc> BY_NAME = new HashMap<>();
    private static final Map<String, PrFunc> BINARY = new HashMap<>();

    static {
        for (PrFunc f : values()) {
            if (f.precedence > 0 && f != NEG && f != NOT && f != BETWEEN && f != EQUALS) {
                BINARY.put(f.name, f);
            } else if (f != NEG) {
                BY_NAME.put(f.name, f);
            }
        }
    }

    final int precedence;
    final Type returnType;
    final String name;
    final Signature signature;
    /** Deterministic in its arguments, so constant arguments fold at parse time. */
    final boolean pure;

    PrFunc(Type returnType, String name, Signature signature) {
        this(0, returnType, name, signature, true);
    }

    PrFunc(Type returnType, String name, Signature signature, boolean pure) {
        this(0, returnType, name, signature, pure);
    }

    PrFunc(int precedence, Type returnType, String name, Signature signature) {
        this(precedence, returnType, name, signature, true);
    }

    PrFunc(int precedence, Type returnType, String name, Signature signature, boolean pure) {
        this.precedence = precedence;
        this.returnType = returnType;
        this.name = name;
        this.signature = signature;
        this.pure = pure;
    }

    /** Parameter types for a call with {@code argCount} arguments. */
    Type[] parameterTypes(int argCount) {
        return signature.typesFor(argCount);
    }

    static PrFunc named(String name) {
        return BY_NAME.get(name);
    }

    static PrFunc binary(String text) {
        return BINARY.get(text);
    }

    private static Signature sig(Type... types) {
        return new Signature(types, new Type[0], new Type[0], Integer.MAX_VALUE);
    }

    private static Signature var(Type[] first, Type[] repeat, Type[] last, int maxCount) {
        return new Signature(first, repeat, last, maxCount);
    }
}
