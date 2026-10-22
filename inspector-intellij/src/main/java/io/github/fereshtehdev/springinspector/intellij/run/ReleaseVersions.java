package io.github.fereshtehdev.springinspector.intellij.run;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The plugin's own version, and whether a release published on GitHub is newer. */
public final class ReleaseVersions {

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([0-9][0-9A-Za-z.\\-]{0,40})\"");
    private static final Pattern NUMBERS = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    private ReleaseVersions() {}

    /** The version this plugin was built as, such as {@code 0.2.0}; empty in a development build. */
    public static Optional<String> current() {
        try (InputStream in = ReleaseVersions.class.getResourceAsStream(
                "/io/github/fereshtehdev/springinspector/intellij/plugin-version.properties")) {
            if (in == null) {
                return Optional.empty();
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "").strip();
            return version.isEmpty() || version.contains("$") ? Optional.empty() : Optional.of(version);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /** The version of the newest release in GitHub's {@code releases/latest} answer. */
    public static Optional<String> latest(String gitHubRelease) {
        Matcher tag = TAG.matcher(gitHubRelease);
        return tag.find() ? Optional.of(tag.group(1)) : Optional.empty();
    }

    /** Whether {@code candidate} is a newer release than {@code current}; development builds are never told. */
    public static boolean isNewer(String candidate, String current) {
        if (current.endsWith("-SNAPSHOT")) {
            return false;
        }
        Optional<int[]> a = numbers(candidate);
        Optional<int[]> b = numbers(current);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (a.get()[i] != b.get()[i]) {
                return a.get()[i] > b.get()[i];
            }
        }
        return false;
    }

    private static Optional<int[]> numbers(String version) {
        Matcher matcher = NUMBERS.matcher(version);
        if (!matcher.lookingAt()) {
            return Optional.empty();
        }
        int[] parts = new int[3];
        for (int i = 0; i < 3; i++) {
            String part = matcher.group(i + 1);
            parts[i] = part == null ? 0 : Integer.parseInt(part);
        }
        return Optional.of(parts);
    }
}
