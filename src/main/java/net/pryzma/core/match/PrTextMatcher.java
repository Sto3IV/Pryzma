package net.pryzma.core.match;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * OptiFine text value matching ({@code name=} and NBT string values):
 * {@code pattern:} / {@code ipattern:} with {@code *} and {@code ?} wildcards, {@code regex:} /
 * {@code iregex:}, otherwise exact equality. The {@code i} forms ignore case.
 */
public final class PrTextMatcher {
    private final String exact;
    private final Pattern pattern;

    private PrTextMatcher(String exact, Pattern pattern) {
        this.exact = exact;
        this.pattern = pattern;
    }

    /** {@code null} for a missing value. */
    public static PrTextMatcher parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.startsWith("pattern:")) {
                return new PrTextMatcher(null, Pattern.compile(wildcard(value.substring(8)), Pattern.DOTALL));
            }
            if (value.startsWith("ipattern:")) {
                return new PrTextMatcher(null, Pattern.compile(wildcard(value.substring(9)),
                        Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }
            if (value.startsWith("regex:")) {
                return new PrTextMatcher(null, Pattern.compile(value.substring(6), Pattern.DOTALL));
            }
            if (value.startsWith("iregex:")) {
                return new PrTextMatcher(null, Pattern.compile(value.substring(7),
                        Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }
        } catch (PatternSyntaxException e) {
            return new PrTextMatcher(value, null);
        }
        return new PrTextMatcher(value, null);
    }

    public boolean matches(String text) {
        if (text == null) {
            return false;
        }
        return pattern != null ? pattern.matcher(text).matches() : exact.equals(text);
    }

    private static String wildcard(String glob) {
        StringBuilder sb = new StringBuilder(glob.length() + 8);
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '\\' -> {
                    if (i + 1 < glob.length()) {
                        sb.append(Pattern.quote(String.valueOf(glob.charAt(++i))));
                    }
                }
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return pattern != null ? pattern.pattern() : exact.toLowerCase(Locale.ROOT);
    }
}
