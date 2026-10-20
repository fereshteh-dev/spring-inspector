package io.github.fereshtehdev.springinspector.project;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Placeholders {

    private static final Pattern BRACED = Pattern.compile("\\$\\{([\\w.\\-]+)}");
    private static final Pattern BARE = Pattern.compile("\\$([A-Za-z_]\\w*)");
    private static final int MAX_PASSES = 10;

    private Placeholders() {}

    public static Optional<String> resolveBraced(String raw, Map<String, String> values) {
        return resolve(raw, values, BRACED);
    }

    public static Optional<String> resolveBracedOrBare(String raw, Map<String, String> values) {
        return resolveBraced(raw, values).flatMap(braced -> resolve(braced, values, BARE));
    }

    private static Optional<String> resolve(String raw, Map<String, String> values, Pattern placeholder) {
        String current = raw.strip();
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            Matcher matcher = placeholder.matcher(current);
            if (!matcher.find()) {
                return current.isEmpty() ? Optional.empty() : Optional.of(current);
            }
            String value = values.get(matcher.group(1));
            if (value == null) {
                return Optional.empty();
            }
            current = current.substring(0, matcher.start()) + value + current.substring(matcher.end());
        }
        return Optional.empty();
    }
}
