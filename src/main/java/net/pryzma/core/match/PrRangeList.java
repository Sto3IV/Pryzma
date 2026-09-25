package net.pryzma.core.match;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * OptiFine integer range lists: {@code "1-3 5 7-9"}. The signed form also accepts negative bounds,
 * written either bare ({@code -64-0}) or bracketed ({@code (-64)-(-1)}).
 */
public final class PrRangeList {
    private static final Pattern SIGNED_SEPARATOR = Pattern.compile("(\\d|\\))-(\\d|\\()");

    private final int[] min;
    private final int[] max;

    private PrRangeList(int[] min, int[] max) {
        this.min = min;
        this.max = max;
    }

    /** A single inclusive range. */
    public static PrRangeList of(int min, int max) {
        return new PrRangeList(new int[] {min}, new int[] {max});
    }

    /** Inclusive ranges {@code min[i]..max[i]}, for formats with their own range syntax. */
    public static PrRangeList of(int[] min, int[] max) {
        if (min.length != max.length) {
            throw new IllegalArgumentException("bounds differ in length");
        }
        return new PrRangeList(min.clone(), max.clone());
    }

    public boolean contains(int value) {
        for (int i = 0; i < min.length; i++) {
            if (value >= min[i] && value <= max[i]) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return min.length == 0;
    }

    /** Lower bound of the first range; OptiFine reads a potion rule's splash bit from it. */
    public int firstMin() {
        return min[0];
    }

    /** Non-negative ranges ({@code days}, {@code metadata}); {@code null} on any malformed token. */
    public static PrRangeList parse(String text) {
        return parse(text, false);
    }

    /** Signed ranges ({@code heights}); {@code null} on any malformed token. */
    public static PrRangeList parseSigned(String text) {
        return parse(text, true);
    }

    private static PrRangeList parse(String text, boolean signed) {
        if (text == null) {
            return null;
        }
        List<int[]> ranges = new ArrayList<>();
        for (String token : text.trim().split("[ ,]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int[] r = signed ? parseSignedToken(token) : parseToken(token);
            if (r == null) {
                return null;
            }
            ranges.add(r);
        }
        int[] lo = new int[ranges.size()];
        int[] hi = new int[ranges.size()];
        for (int i = 0; i < lo.length; i++) {
            lo[i] = ranges.get(i)[0];
            hi[i] = ranges.get(i)[1];
        }
        return new PrRangeList(lo, hi);
    }

    private static int[] parseToken(String token) {
        int dash = token.indexOf('-');
        if (dash < 0) {
            int v = parseNonNegative(token);
            return v < 0 ? null : new int[] {v, v};
        }
        String a = token.substring(0, dash);
        String b = token.substring(dash + 1);
        int lo = parseNonNegative(a);
        // "5-" means "5 and above", as in OptiFine's metadata and damage ranges.
        int hi = b.isEmpty() ? Integer.MAX_VALUE : parseNonNegative(b);
        return lo < 0 || hi < 0 ? null : ordered(lo, hi);
    }

    /** OptiFine's RangeInt swaps reversed bounds: {@code 100-99} is 99..100. */
    private static int[] ordered(int a, int b) {
        return new int[] {Math.min(a, b), Math.max(a, b)};
    }

    private static int[] parseSignedToken(String token) {
        if (token.indexOf('=') >= 0) {
            return null;
        }
        String eq = SIGNED_SEPARATOR.matcher(token).replaceAll("$1=$2");
        int sep = eq.indexOf('=');
        if (sep < 0) {
            Integer v = parseSignedInt(stripBrackets(token));
            return v == null ? null : new int[] {v, v};
        }
        Integer lo = parseSignedInt(stripBrackets(eq.substring(0, sep)));
        Integer hi = parseSignedInt(stripBrackets(eq.substring(sep + 1)));
        return lo == null || hi == null ? null : ordered(lo, hi);
    }

    private static String stripBrackets(String s) {
        return s.startsWith("(") && s.endsWith(")") ? s.substring(1, s.length() - 1) : s;
    }

    private static int parseNonNegative(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return v < 0 ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Integer parseSignedInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
