package net.pryzma.shader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * The two text passes shader packs need before anything else: {@code #include} expansion of
 * program sources (with {@code #line} markers so compiler errors point at the right file), and
 * the C-style conditional directives OptiFine evaluates in {@code shaders.properties}.
 */
public final class PrGlslPreprocessor {
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\".*$");
    private static final Pattern DIRECTIVE = Pattern.compile("^\\s*#\\s*(\\w+)\\s*(.*?)\\s*$");
    private static final Pattern DEFINED = Pattern.compile("defined\\s*\\(\\s*(\\w+)\\s*\\)|defined\\s+(\\w+)");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_]\\w*");
    private static final int MAX_DEPTH = 32;

    private PrGlslPreprocessor() {
    }

    /** A source with its includes expanded, and the files it was assembled from (the {@code #line} source numbers). */
    public record Source(String text, List<String> files) {
    }

    /**
     * Expands the includes of {@code path}. An include starting with {@code /} is relative to
     * {@code shaders/}, any other to the including file. {@code null} when the file is missing.
     */
    public static Source expand(PrShaderPack pack, String path, Consumer<String> warn) {
        String text = pack.read(path);
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length() + 256);
        List<String> files = new ArrayList<>();
        expand(pack, path, text, out, files, new ArrayDeque<>(), warn);
        return new Source(out.toString(), files);
    }

    private static void expand(PrShaderPack pack, String path, String text, StringBuilder out, List<String> files,
            Deque<String> stack, Consumer<String> warn) {
        int index = files.size();
        files.add(path);
        stack.push(path);
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = INCLUDE.matcher(lines[i]);
            if (!m.matches()) {
                out.append(lines[i]).append('\n');
                continue;
            }
            String target = resolve(m.group(1), parent(path));
            String included = pack.read(target);
            if (included == null) {
                warn.accept("Missing include " + target + " in " + path);
                out.append("// missing include ").append(target).append('\n');
            } else if (stack.contains(target) || stack.size() >= MAX_DEPTH) {
                warn.accept("Recursive include " + target + " in " + path);
                out.append("// recursive include ").append(target).append('\n');
            } else {
                out.append("#line 1 ").append(files.size()).append('\n');
                expand(pack, target, included, out, files, stack, warn);
                out.append("#line ").append(i + 2).append(' ').append(index).append('\n');
            }
        }
        stack.pop();
    }

    /** Resolves an include path: {@code /x} from {@code shaders/}, {@code x} beside the including file. */
    static String resolve(String include, String dir) {
        String path = include.startsWith("/") ? "shaders" + include : dir + "/" + include;
        Deque<String> parts = new ArrayDeque<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
            } else {
                parts.addLast(part);
            }
        }
        return String.join("/", parts);
    }

    static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    /**
     * Evaluates {@code #define}/{@code #undef}/{@code #if}/{@code #ifdef}/{@code #ifndef}/
     * {@code #elif}/{@code #else}/{@code #endif} over {@code text} (as OptiFine does for
     * {@code shaders.properties}) and returns the lines of the active branches. {@code macros}
     * holds the predefined macros and receives the file's own definitions.
     */
    public static String conditionals(String text, Map<String, String> macros, Consumer<String> warn) {
        StringBuilder out = new StringBuilder(text.length());
        // Per nesting level: whether the current branch is active, and whether any branch was taken.
        Deque<boolean[]> levels = new ArrayDeque<>();
        for (String line : text.split("\r?\n", -1)) {
            Matcher d = DIRECTIVE.matcher(line);
            boolean active = levels.isEmpty() || levels.peek()[0];
            if (!d.matches()) {
                if (active) {
                    out.append(line).append('\n');
                }
                continue;
            }
            String name = d.group(1);
            String arg = d.group(2);
            switch (name) {
                case "ifdef", "ifndef" -> {
                    boolean cond = macros.containsKey(firstWord(arg)) == name.equals("ifdef");
                    levels.push(new boolean[] {active && cond, cond, active});
                }
                case "if" -> {
                    boolean cond = active && evaluate(arg, macros, warn);
                    levels.push(new boolean[] {cond, cond, active});
                }
                case "elif" -> {
                    boolean[] level = levels.peek();
                    if (level == null) {
                        warn.accept("#elif without #if");
                    } else {
                        boolean cond = level[2] && !level[1] && evaluate(arg, macros, warn);
                        level[0] = cond;
                        level[1] |= cond;
                    }
                }
                case "else" -> {
                    boolean[] level = levels.peek();
                    if (level == null) {
                        warn.accept("#else without #if");
                    } else {
                        level[0] = level[2] && !level[1];
                        level[1] = true;
                    }
                }
                case "endif" -> {
                    if (levels.isEmpty()) {
                        warn.accept("#endif without #if");
                    } else {
                        levels.pop();
                    }
                }
                case "define" -> {
                    if (active) {
                        String word = firstWord(arg);
                        macros.put(word, arg.substring(word.length()).trim());
                    }
                }
                case "undef" -> {
                    if (active) {
                        macros.remove(firstWord(arg));
                    }
                }
                default -> {
                    if (active) {
                        out.append(line).append('\n');
                    }
                }
            }
        }
        if (!levels.isEmpty()) {
            warn.accept("Missing #endif");
        }
        return out.toString();
    }

    private static String firstWord(String s) {
        Matcher m = IDENTIFIER.matcher(s);
        return m.lookingAt() ? m.group() : s.trim();
    }

    /**
     * A C preprocessor condition: {@code defined} tests, then macros replaced by their values
     * (an undefined name is 0, a macro defined empty is 1), evaluated with C semantics: any
     * non-zero value is true. A malformed condition is false.
     */
    static boolean evaluate(String condition, Map<String, String> macros, Consumer<String> warn) {
        String expr = expandMacros(condition, macros, 0);
        try {
            CondParser parser = new CondParser(expr);
            double value = parser.ternary();
            parser.skipSpace();
            if (parser.pos != expr.length()) {
                throw new IllegalArgumentException("unexpected '" + expr.substring(parser.pos) + "'");
            }
            return value != 0.0;
        } catch (RuntimeException e) {
            warn.accept("Cannot evaluate #if " + condition + ": " + e.getMessage());
            return false;
        }
    }

    /** Recursive descent over C's operator precedence; logical results are 1 or 0. */
    private static final class CondParser {
        private final String s;
        int pos;

        CondParser(String s) {
            this.s = s;
        }

        void skipSpace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private boolean eat(String op) {
            skipSpace();
            if (!s.startsWith(op, pos)) {
                return false;
            }
            // "&" must not match "&&", "<" not "<<" or "<=", and so on.
            int end = pos + op.length();
            if (op.length() == 1 && end < s.length() && "&|<>=".indexOf(op.charAt(0)) >= 0
                    && (s.charAt(end) == op.charAt(0) || s.charAt(end) == '=' && op.charAt(0) != '=')) {
                return false;
            }
            pos = end;
            return true;
        }

        double ternary() {
            double c = or();
            if (eat("?")) {
                double a = ternary();
                if (!eat(":")) {
                    throw new IllegalArgumentException("missing ':'");
                }
                double b = ternary();
                return c != 0 ? a : b;
            }
            return c;
        }

        private double or() {
            double v = and();
            while (eat("||")) {
                double r = and();
                v = v != 0 || r != 0 ? 1 : 0;
            }
            return v;
        }

        private double and() {
            double v = bitOr();
            while (eat("&&")) {
                double r = bitOr();
                v = v != 0 && r != 0 ? 1 : 0;
            }
            return v;
        }

        private double bitOr() {
            double v = bitXor();
            while (eat("|")) {
                v = (long) v | (long) bitXor();
            }
            return v;
        }

        private double bitXor() {
            double v = bitAnd();
            while (eat("^")) {
                v = (long) v ^ (long) bitAnd();
            }
            return v;
        }

        private double bitAnd() {
            double v = equality();
            while (eat("&")) {
                v = (long) v & (long) equality();
            }
            return v;
        }

        private double equality() {
            double v = relational();
            while (true) {
                if (eat("==")) {
                    v = v == relational() ? 1 : 0;
                } else if (eat("!=")) {
                    v = v != relational() ? 1 : 0;
                } else {
                    return v;
                }
            }
        }

        private double relational() {
            double v = shift();
            while (true) {
                if (eat("<=")) {
                    v = v <= shift() ? 1 : 0;
                } else if (eat(">=")) {
                    v = v >= shift() ? 1 : 0;
                } else if (eat("<")) {
                    v = v < shift() ? 1 : 0;
                } else if (eat(">")) {
                    v = v > shift() ? 1 : 0;
                } else {
                    return v;
                }
            }
        }

        private double shift() {
            double v = additive();
            while (true) {
                if (eat("<<")) {
                    v = (long) v << (long) additive();
                } else if (eat(">>")) {
                    v = (long) v >> (long) additive();
                } else {
                    return v;
                }
            }
        }

        private double additive() {
            double v = multiplicative();
            while (true) {
                if (eat("+")) {
                    v += multiplicative();
                } else if (eat("-")) {
                    v -= multiplicative();
                } else {
                    return v;
                }
            }
        }

        private double multiplicative() {
            double v = unary();
            while (true) {
                if (eat("*")) {
                    v *= unary();
                } else if (eat("/")) {
                    v /= unary();
                } else if (eat("%")) {
                    v %= unary();
                } else {
                    return v;
                }
            }
        }

        private double unary() {
            if (eat("!")) {
                return unary() == 0 ? 1 : 0;
            }
            if (eat("-")) {
                return -unary();
            }
            if (eat("+")) {
                return unary();
            }
            if (eat("~")) {
                return ~(long) unary();
            }
            return primary();
        }

        private double primary() {
            skipSpace();
            if (eat("(")) {
                double v = ternary();
                if (!eat(")")) {
                    throw new IllegalArgumentException("missing ')'");
                }
                return v;
            }
            if (s.startsWith("true", pos)) {
                pos += 4;
                return 1;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return 0;
            }
            int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            String number = s.substring(start, pos).replaceAll("[fFlLuU]+$", "");
            if (number.isEmpty()) {
                throw new IllegalArgumentException("expected a value at '" + s.substring(start) + "'");
            }
            return number.startsWith("0x") || number.startsWith("0X") ? Long.parseLong(number.substring(2), 16) : Double.parseDouble(number);
        }
    }

    private static String expandMacros(String text, Map<String, String> macros, int depth) {
        Matcher defined = DEFINED.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (defined.find()) {
            String name = defined.group(1) != null ? defined.group(1) : defined.group(2);
            defined.appendReplacement(sb, macros.containsKey(name) ? "1" : "0");
        }
        defined.appendTail(sb);
        Matcher m = IDENTIFIER.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group();
            String replacement;
            if (name.equals("true") || name.equals("false")) {
                replacement = name;
            } else if (!macros.containsKey(name)) {
                replacement = "0";
            } else {
                String value = macros.get(name);
                replacement = value.isEmpty() ? "1" : depth < 8 ? "(" + expandMacros(value, macros, depth + 1) + ")" : "0";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
