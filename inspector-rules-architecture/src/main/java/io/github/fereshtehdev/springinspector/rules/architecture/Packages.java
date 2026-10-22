package io.github.fereshtehdev.springinspector.rules.architecture;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Package names as their lower-case segments, the way the architecture rules match them. */
final class Packages {

    private static final Pattern DOT = Pattern.compile("\\.");

    private Packages() {}

    static List<String> segments(String packageName) {
        return DOT.splitAsStream(packageName.toLowerCase(Locale.ROOT))
                .filter(segment -> !segment.isEmpty())
                .toList();
    }
}
