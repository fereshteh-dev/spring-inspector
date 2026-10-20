package io.github.fereshtehdev.springinspector.core.domain.project;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Version(int major, int minor, int patch) implements Comparable<Version> {

    private static final Pattern NUMERIC_PREFIX =
            Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[.\\-+_][A-Za-z0-9].*)?");

    private static final Comparator<Version> ORDER = Comparator.comparingInt(Version::major)
            .thenComparingInt(Version::minor)
            .thenComparingInt(Version::patch);

    public Version {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version parts cannot be negative");
        }
    }

    public static Version of(int major, int minor, int patch) {
        return new Version(major, minor, patch);
    }

    public static Version parse(String text) {
        Matcher matcher = NUMERIC_PREFIX.matcher(text.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a version: '" + text + "'");
        }
        return new Version(part(matcher, 1), part(matcher, 2), part(matcher, 3));
    }

    private static int part(Matcher matcher, int group) {
        String digits = matcher.group(group);
        return digits == null ? 0 : Integer.parseInt(digits);
    }

    @Override
    public int compareTo(Version other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
