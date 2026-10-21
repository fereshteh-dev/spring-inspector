package io.github.fereshtehdev.springinspector.advisories;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A Maven version ordered the way Maven orders them: {@code 1.0-alpha1 < 1.0-rc1 < 1.0 = 1.0.RELEASE < 1.0-sp1 <
 * 1.0.1}. Numbers compare numerically, known qualifiers by their meaning, unknown ones after a release and
 * alphabetically, and missing parts count as zero or as a plain release.
 */
public final class MavenVersion implements Comparable<MavenVersion> {

    private static final Map<String, Integer> QUALIFIERS = Map.ofEntries(
            Map.entry("alpha", 1),
            Map.entry("a", 1),
            Map.entry("beta", 2),
            Map.entry("b", 2),
            Map.entry("milestone", 3),
            Map.entry("m", 3),
            Map.entry("rc", 4),
            Map.entry("cr", 4),
            Map.entry("snapshot", 5),
            Map.entry("", 6),
            Map.entry("ga", 6),
            Map.entry("final", 6),
            Map.entry("release", 6),
            Map.entry("sp", 7));
    private static final int UNKNOWN = 8;

    private final String text;
    private final List<Object> parts;

    private MavenVersion(String text, List<Object> parts) {
        this.text = text;
        this.parts = parts;
    }

    public static MavenVersion parse(String text) {
        List<Object> parts = new ArrayList<>();
        String lower = text.strip().toLowerCase(Locale.ROOT);
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean separator = c == '.' || c == '-' || c == '_' || c == '+';
            boolean kindChange =
                    !token.isEmpty() && Character.isDigit(c) != Character.isDigit(token.charAt(token.length() - 1));
            if (separator || kindChange) {
                add(parts, token);
            }
            if (!separator) {
                token.append(c);
            }
        }
        add(parts, token);
        return new MavenVersion(text.strip(), List.copyOf(parts));
    }

    private static void add(List<Object> parts, StringBuilder token) {
        if (token.isEmpty()) {
            return;
        }
        String value = token.toString();
        parts.add(Character.isDigit(value.charAt(0)) ? new BigInteger(value) : value);
        token.setLength(0);
    }

    @Override
    public int compareTo(MavenVersion other) {
        int length = Math.max(parts.size(), other.parts.size());
        for (int i = 0; i < length; i++) {
            Object mine = i < parts.size() ? parts.get(i) : null;
            Object theirs = i < other.parts.size() ? other.parts.get(i) : null;
            int order = compare(mine, theirs);
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }

    /** {@code null} is a missing part: zero next to a number, a plain release next to a qualifier. */
    private static int compare(@Nullable Object mine, @Nullable Object theirs) {
        if (mine instanceof BigInteger a && theirs instanceof BigInteger b) {
            return a.compareTo(b);
        }
        if (mine instanceof String a && theirs instanceof String b) {
            return compareQualifiers(a, b);
        }
        if (mine == null && theirs == null) {
            return 0;
        }
        if (mine == null) {
            return -compare(theirs, null);
        }
        if (theirs == null) {
            return mine instanceof BigInteger number ? number.signum() : compareQualifiers((String) mine, "");
        }
        return mine instanceof BigInteger ? 1 : -1;
    }

    private static int compareQualifiers(String a, String b) {
        int rankA = QUALIFIERS.getOrDefault(a, UNKNOWN);
        int rankB = QUALIFIERS.getOrDefault(b, UNKNOWN);
        if (rankA != rankB) {
            return Integer.compare(rankA, rankB);
        }
        return rankA == UNKNOWN ? a.compareTo(b) : 0;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof MavenVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        List<Object> significant = new ArrayList<>(parts);
        while (!significant.isEmpty() && compare(significant.getLast(), null) == 0) {
            significant.removeLast();
        }
        return significant.stream()
                .map(part -> part instanceof String qualifier && QUALIFIERS.getOrDefault(qualifier, UNKNOWN) != UNKNOWN
                        ? (Object) QUALIFIERS.get(qualifier)
                        : part)
                .toList()
                .hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
