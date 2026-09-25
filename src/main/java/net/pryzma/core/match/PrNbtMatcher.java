package net.pryzma.core.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * One OptiFine NBT condition ({@code nbt.<path>=<value>}, {@code components.<path>=<value>}) with
 * the semantics of OptiFine's {@code NbtTagValue}: a dot-separated path where {@code *} matches any
 * child and list children are addressed by index or {@code count}; a value that is an exact text,
 * {@code pattern:}/{@code ipattern:} wildcards, {@code regex:}/{@code iregex:}, {@code range:}
 * integers or {@code exists:true|false}, optionally negated with {@code !} and compared {@code raw:}
 * (the tag's SNBT) instead of its text.
 */
public final class PrNbtMatcher {
    private enum Kind {
        TEXT, PATTERN, IPATTERN, REGEX, RANGE, EXISTS, INVALID
    }

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-f]{6}+$");

    private final String[] path;
    private final boolean negative;
    private final boolean raw;
    private final Kind kind;
    private final String value;
    private final Pattern regex;
    private final PrRangeList range;
    private final boolean hexColor;

    private PrNbtMatcher(String[] path, boolean negative, boolean raw, Kind kind, String value, Pattern regex,
            PrRangeList range, boolean hexColor) {
        this.path = path;
        this.negative = negative;
        this.raw = raw;
        this.kind = kind;
        this.value = value;
        this.regex = regex;
        this.range = range;
        this.hexColor = hexColor;
    }

    /** @param path the tag path after the {@code nbt.} prefix, e.g. {@code display.Name} */
    public static PrNbtMatcher parse(String path, String value) {
        String[] segments = tokenize(path);
        boolean negative = false;
        boolean raw = false;
        if (value.startsWith("!")) {
            negative = true;
            value = value.substring(1);
        }
        if (value.startsWith("raw:")) {
            raw = true;
            value = value.substring(4);
        }
        Kind kind = Kind.TEXT;
        Pattern regex = null;
        PrRangeList range = null;
        try {
            if (value.startsWith("pattern:")) {
                value = value.substring(8);
                kind = value.equals("*") ? Kind.EXISTS : Kind.PATTERN;
            } else if (value.startsWith("ipattern:")) {
                value = value.substring(9).toLowerCase(Locale.ROOT);
                kind = value.equals("*") ? Kind.EXISTS : Kind.IPATTERN;
            } else if (value.startsWith("regex:")) {
                value = value.substring(6);
                regex = Pattern.compile(value);
                kind = value.equals(".*") ? Kind.EXISTS : Kind.REGEX;
            } else if (value.startsWith("iregex:")) {
                value = value.substring(7);
                regex = Pattern.compile(value, Pattern.CASE_INSENSITIVE);
                kind = value.equals(".*") ? Kind.EXISTS : Kind.REGEX;
            } else if (value.startsWith("range:")) {
                value = value.substring(6);
                range = PrRangeList.parseSigned(value);
                kind = range == null ? Kind.INVALID : Kind.RANGE;
            } else if (value.startsWith("exists:")) {
                value = value.substring(7);
                if (value.equalsIgnoreCase("true")) {
                    kind = Kind.EXISTS;
                } else if (value.equalsIgnoreCase("false")) {
                    kind = Kind.EXISTS;
                    negative = !negative;
                } else {
                    kind = Kind.INVALID;
                }
            }
        } catch (PatternSyntaxException e) {
            kind = Kind.INVALID;
        }
        if (kind == Kind.INVALID) {
            negative = false;
        }
        value = unescapeJava(value);
        boolean hexColor = kind == Kind.TEXT && HEX_COLOR.matcher(value).matches();
        return new PrNbtMatcher(segments, negative, raw, kind, value, regex, range, hexColor);
    }

    public boolean isValid() {
        return kind != Kind.INVALID;
    }

    public String[] path() {
        return path.clone();
    }

    /** First path segment: the component an item condition reads, or {@code *} for any. */
    public String head() {
        return path.length == 0 ? "" : path[0];
    }

    public boolean matches(Tag root) {
        return negative != matchesTag(root, 0);
    }

    private boolean matchesTag(Tag tag, int level) {
        if (tag == null) {
            return false;
        }
        if (level >= path.length) {
            return matchesBase(tag);
        }
        String name = path[level];
        if (name.equals("*")) {
            if (tag instanceof CompoundTag compound) {
                for (String key : compound.getAllKeys()) {
                    if (matchesTag(compound.get(key), level + 1)) {
                        return true;
                    }
                }
            }
            if (tag instanceof ListTag list) {
                for (Tag child : list) {
                    if (matchesTag(child, level + 1)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return matchesTag(child(tag, name), level + 1);
    }

    private static Tag child(Tag tag, String name) {
        if (tag instanceof CompoundTag compound) {
            return compound.get(name);
        }
        if (tag instanceof ListTag list) {
            if (name.equals("count")) {
                return IntTag.valueOf(list.size());
            }
            int index = index(name);
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }
        return null;
    }

    private boolean matchesBase(Tag tag) {
        switch (kind) {
            case INVALID:
                return false;
            case EXISTS:
                return true;
            case RANGE:
                int number = intValue(tag, Integer.MIN_VALUE);
                if (number != Integer.MIN_VALUE) {
                    return range.contains(number);
                }
                // A text tag holding a number is compared by value, as in OptiFine.
                return matchesValue(raw ? tag.toString() : text(tag, false));
            default:
                return matchesValue(raw ? tag.toString() : text(tag, hexColor));
        }
    }

    /** Compares a plain text, as entity names ({@code name.N}) are matched. */
    public boolean matchesValue(String text) {
        if (text == null) {
            return false;
        }
        return switch (kind) {
            case INVALID -> false;
            case TEXT -> text.equals(value);
            case PATTERN -> wildcard(text, value);
            case IPATTERN -> wildcard(text.toLowerCase(Locale.ROOT), value);
            case REGEX -> regex.matcher(text).matches();
            case RANGE -> {
                try {
                    yield range.contains(Integer.parseInt(text.trim()));
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case EXISTS -> true;
        };
    }

    /** The comparison text of a tag: numbers as written, strings unquoted, JSON text flattened. */
    static String text(Tag tag, boolean hexColor) {
        if (tag instanceof StringTag string) {
            String s = string.getAsString();
            if ((s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[{") && s.endsWith("}]"))) {
                return mergedJsonText(s);
            }
            if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) {
                return s.substring(1, s.length() - 1);
            }
            return s;
        }
        if (tag instanceof IntTag i) {
            return hexColor ? "#" + String.format("%06x", i.getAsInt()) : Integer.toString(i.getAsInt());
        }
        if (tag instanceof ByteTag b) {
            return Byte.toString(b.getAsByte());
        }
        if (tag instanceof ShortTag s) {
            return Short.toString(s.getAsShort());
        }
        if (tag instanceof LongTag l) {
            return Long.toString(l.getAsLong());
        }
        if (tag instanceof FloatTag f) {
            return Float.toString(f.getAsFloat());
        }
        if (tag instanceof DoubleTag d) {
            return Double.toString(d.getAsDouble());
        }
        return tag.toString();
    }

    private static int intValue(Tag tag, int def) {
        if (tag instanceof IntTag i) {
            return i.getAsInt();
        }
        if (tag instanceof ByteTag b) {
            return b.getAsByte();
        }
        if (tag instanceof ShortTag s) {
            return s.getAsShort();
        }
        if (tag instanceof LongTag l) {
            return (int) l.getAsLong();
        }
        if (tag instanceof FloatTag f) {
            return (int) f.getAsFloat();
        }
        if (tag instanceof DoubleTag d) {
            return (int) d.getAsDouble();
        }
        return def;
    }

    /** Concatenation of every {@code "text":"..."} value, as OptiFine flattens JSON chat components. */
    static String mergedJsonText(String json) {
        StringBuilder out = new StringBuilder();
        String token = "\"text\":\"";
        int pos = -1;
        while ((pos = json.indexOf(token, pos + 1)) >= 0) {
            boolean escape = false;
            for (int i = pos + token.length(); i < json.length(); i++) {
                char ch = json.charAt(i);
                if (escape) {
                    out.append(switch (ch) {
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> ch;
                    });
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    break;
                } else {
                    out.append(ch);
                }
            }
        }
        return out.toString();
    }

    /** OptiFine {@code StrUtils.equalsMask}: {@code *} any run, {@code ?} any single character. */
    static boolean wildcard(String text, String mask) {
        int t = 0;
        int m = 0;
        int star = -1;
        int mark = 0;
        while (t < text.length()) {
            if (m < mask.length() && (mask.charAt(m) == '?' || mask.charAt(m) == text.charAt(t))) {
                t++;
                m++;
            } else if (m < mask.length() && mask.charAt(m) == '*') {
                star = m++;
                mark = t;
            } else if (star >= 0) {
                m = star + 1;
                t = ++mark;
            } else {
                return false;
            }
        }
        while (m < mask.length() && mask.charAt(m) == '*') {
            m++;
        }
        return m == mask.length();
    }

    /** OptiFine {@code Config.tokenize(path, ".")}: empty segments are dropped. */
    private static String[] tokenize(String path) {
        List<String> out = new ArrayList<>();
        for (String s : path.split("\\.")) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out.toArray(new String[0]);
    }

    /** Java string escapes (newline, tab, backslash, four-digit unicode, ...), as OptiFine unescapes values. */
    static String unescapeJava(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '\\' || i + 1 >= s.length()) {
                out.append(ch);
                continue;
            }
            char next = s.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        try {
                            out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            out.append("\\u");
                        }
                    } else {
                        out.append("\\u");
                    }
                }
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    /** A list index: a non-negative integer, otherwise -1. */
    private static int index(String s) {
        try {
            return Math.max(-1, Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String toString() {
        return String.join(".", path) + " = " + value;
    }
}
