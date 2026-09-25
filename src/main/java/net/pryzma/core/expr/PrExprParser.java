package net.pryzma.core.expr;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the OptiFine expression language used by CEM animations ({@code .jem}
 * {@code animations}) and shader packs ({@code uniform.*}, {@code variable.*}).
 *
 * <p>Grammar, precedence and typing follow OptiFine's {@code ExpressionParser} exactly:
 * identifiers start with a letter and may contain {@code _ : .}; numbers start with a digit; binary
 * operators bind {@code * / %} (11) over {@code + -} (10) over comparisons (8) over {@code == !=}
 * (7) over {@code &&} (3) over {@code ||} (2); unary {@code - + !} apply to the next operand only;
 * arguments must match the function signature without implicit float/bool conversion.
 *
 * <p>Pure subexpressions over constants are folded at parse time.
 */
public final class PrExprParser {
    /** Supplies variables: model part values, entity parameters, uniforms. */
    @FunctionalInterface
    public interface Resolver {
        /** The expression for {@code name}, or {@code null} when the name is unknown. */
        PrExpr resolve(String name);
    }

    private final Resolver resolver;
    private final PrExprEnv env;

    public PrExprParser(Resolver resolver, PrExprEnv env) {
        this.resolver = resolver;
        this.env = env;
    }

    public PrExpr parse(String text) throws PrExprException {
        List<Token> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            throw new PrExprException("Empty expression");
        }
        Cursor c = new Cursor(tokens);
        PrExpr e = parseInfix(c, tokens.size());
        if (c.pos != tokens.size()) {
            throw new PrExprException("Unexpected token: " + tokens.get(c.pos).text);
        }
        return e;
    }

    public PrExpr.F parseFloat(String text) throws PrExprException {
        PrExpr e = parse(text);
        if (e instanceof PrExpr.F f) {
            return f;
        }
        throw new PrExprException("Not a float expression: " + text);
    }

    public PrExpr.B parseBool(String text) throws PrExprException {
        PrExpr e = parse(text);
        if (e instanceof PrExpr.B b) {
            return b;
        }
        throw new PrExprException("Not a boolean expression: " + text);
    }

    // ------------------------------------------------------------------ tokens

    enum Kind {
        IDENTIFIER, NUMBER, OPERATOR, COMMA, OPEN, CLOSE
    }

    record Token(Kind kind, String text) {
    }

    private static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";

    static List<Token> tokenize(String s) throws PrExprException {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            Kind kind;
            String next;
            if (ALPHA.indexOf(ch) >= 0) {
                kind = Kind.IDENTIFIER;
                next = ALPHA + DIGITS + "_:.";
            } else if (DIGITS.indexOf(ch) >= 0) {
                kind = Kind.NUMBER;
                next = DIGITS + ".";
            } else if ("+-*/%!&|<>=".indexOf(ch) >= 0) {
                kind = Kind.OPERATOR;
                next = "&|=";
            } else if (ch == ',') {
                kind = Kind.COMMA;
                next = "";
            } else if (ch == '(') {
                kind = Kind.OPEN;
                next = "";
            } else if (ch == ')') {
                kind = Kind.CLOSE;
                next = "";
            } else {
                throw new PrExprException("Invalid character '" + ch + "' in: " + s);
            }
            int start = i++;
            while (i < s.length() && next.indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            out.add(new Token(kind, s.substring(start, i)));
        }
        return out;
    }

    private static final class Cursor {
        final List<Token> tokens;
        int pos;

        Cursor(List<Token> tokens) {
            this.tokens = tokens;
        }

        Token peek(int end) {
            return pos < end ? tokens.get(pos) : null;
        }
    }

    // ------------------------------------------------------------------ infix

    /** An infix chain from the cursor up to (exclusive) {@code end}. */
    private PrExpr parseInfix(Cursor c, int end) throws PrExprException {
        List<PrExpr> operands = new ArrayList<>();
        List<PrFunc> operators = new ArrayList<>();
        operands.add(parseOperand(c, end));
        while (c.pos < end) {
            Token t = c.tokens.get(c.pos++);
            PrFunc op = t.kind == Kind.OPERATOR ? PrFunc.binary(t.text) : null;
            if (op == null) {
                throw new PrExprException("Invalid operator: " + t.text);
            }
            operators.add(op);
            operands.add(parseOperand(c, end));
        }
        if (operators.isEmpty()) {
            return operands.get(0);
        }
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (PrFunc op : operators) {
            max = Math.max(max, op.precedence);
            min = Math.min(min, op.precedence);
        }
        for (int p = max; p >= min; p--) {
            for (int i = 0; i < operators.size(); i++) {
                if (operators.get(i).precedence == p) {
                    PrFunc op = operators.remove(i);
                    PrExpr a = operands.remove(i);
                    PrExpr b = operands.remove(i);
                    operands.add(i, make(op, new PrExpr[] {a, b}));
                    i--;
                }
            }
        }
        return operands.get(0);
    }

    private PrExpr parseOperand(Cursor c, int end) throws PrExprException {
        Token t = c.peek(end);
        if (t == null) {
            throw new PrExprException("Missing expression");
        }
        c.pos++;
        switch (t.kind) {
            case NUMBER -> {
                try {
                    return new PrExpr.Constant(Float.parseFloat(t.text));
                } catch (NumberFormatException e) {
                    throw new PrExprException("Invalid number: " + t.text);
                }
            }
            case IDENTIFIER -> {
                Token next = c.peek(end);
                PrFunc f = PrFunc.named(t.text);
                if (next != null && next.kind == Kind.OPEN) {
                    if (f == null) {
                        throw new PrExprException("Unknown function: " + t.text);
                    }
                    c.pos++;
                    int close = matchingClose(c, end);
                    List<PrExpr> args = new ArrayList<>();
                    int argStart = c.pos;
                    int depth = 0;
                    for (int i = argStart; i < close; i++) {
                        Token a = c.tokens.get(i);
                        if (a.kind == Kind.OPEN) {
                            depth++;
                        } else if (a.kind == Kind.CLOSE) {
                            depth--;
                        } else if (a.kind == Kind.COMMA && depth == 0) {
                            args.add(parseGroup(c, argStart, i));
                            argStart = i + 1;
                        }
                    }
                    if (argStart < close) {
                        args.add(parseGroup(c, argStart, close));
                    } else if (!args.isEmpty()) {
                        throw new PrExprException("Missing argument in " + t.text + "()");
                    }
                    c.pos = close + 1;
                    return make(f, args.toArray(new PrExpr[0]));
                }
                if (f != null) {
                    if (f.parameterTypes(0) == null || f.parameterTypes(0).length > 0) {
                        throw new PrExprException("Missing arguments: " + t.text);
                    }
                    return make(f, new PrExpr[0]);
                }
                PrExpr v = resolver == null ? null : resolver.resolve(t.text);
                if (v == null) {
                    throw new PrExprException("Unknown variable: " + t.text);
                }
                return v;
            }
            case OPEN -> {
                int close = matchingClose(c, end);
                PrExpr inner = parseGroup(c, c.pos, close);
                c.pos = close + 1;
                return inner;
            }
            case OPERATOR -> {
                switch (t.text) {
                    case "+" -> {
                        return parseOperand(c, end);
                    }
                    case "-" -> {
                        return make(PrFunc.NEG, new PrExpr[] {parseOperand(c, end)});
                    }
                    case "!" -> {
                        return make(PrFunc.NOT, new PrExpr[] {parseOperand(c, end)});
                    }
                    default -> throw new PrExprException("Invalid expression: " + t.text);
                }
            }
            default -> throw new PrExprException("Invalid expression: " + t.text);
        }
    }

    /** Index of the bracket closing the one just consumed. */
    private static int matchingClose(Cursor c, int end) throws PrExprException {
        int depth = 0;
        for (int i = c.pos; i < end; i++) {
            Kind k = c.tokens.get(i).kind;
            if (k == Kind.OPEN) {
                depth++;
            } else if (k == Kind.CLOSE) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        throw new PrExprException("Missing closing bracket");
    }

    private PrExpr parseGroup(Cursor c, int from, int to) throws PrExprException {
        if (from >= to) {
            throw new PrExprException("Missing expression");
        }
        Cursor sub = new Cursor(c.tokens);
        sub.pos = from;
        PrExpr e = parseInfix(sub, to);
        if (sub.pos != to) {
            throw new PrExprException("Unexpected token: " + c.tokens.get(sub.pos).text);
        }
        return e;
    }

    // ------------------------------------------------------------------ functions

    private PrExpr make(PrFunc f, PrExpr[] args) throws PrExprException {
        PrExpr.Type[] types = f.parameterTypes(args.length);
        if (types == null || types.length != args.length) {
            throw new PrExprException("Invalid number of arguments for " + f.name + ": " + args.length);
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].type() != types[i]) {
                throw new PrExprException("Invalid argument " + i + " of " + f.name + ": " + args[i].type() + ", expected " + types[i]);
            }
        }
        PrExpr e = PrFunctions.build(f, args, env);
        return f.pure && allConstant(args) ? fold(e) : e;
    }

    private static boolean allConstant(PrExpr[] args) {
        for (PrExpr a : args) {
            if (!(a instanceof PrExpr.Constant || a instanceof Folded || a instanceof FoldedBool)) {
                return false;
            }
        }
        return true;
    }

    private static PrExpr fold(PrExpr e) {
        return switch (e) {
            case PrExpr.F f -> new Folded(f.eval());
            case PrExpr.B b -> new FoldedBool(b.eval());
            case PrExpr.V v -> v;
        };
    }

    /** A computed constant; unlike a literal it is not taken as a {@code smooth()} id. */
    record Folded(float value) implements PrExpr.F {
        @Override
        public float eval() {
            return value;
        }
    }

    record FoldedBool(boolean value) implements PrExpr.B {
        @Override
        public boolean eval() {
            return value;
        }
    }

    /** OptiFine {@code MathUtils.toRad}, operation order kept so results match bit for bit. */
    static float toRad(float deg) {
        return deg / 180.0F * (float) Math.PI;
    }

    /** OptiFine {@code MathUtils.toDeg}. */
    static float toDeg(float rad) {
        return rad * 180.0F / (float) Math.PI;
    }
}
