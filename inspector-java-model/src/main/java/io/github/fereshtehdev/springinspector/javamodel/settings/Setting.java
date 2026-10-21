package io.github.fereshtehdev.springinspector.javamodel.settings;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One Spring Boot setting such as {@code management.endpoints.web.exposure.include=*}, flattened from properties or
 * YAML.
 *
 * @param line one-based line the setting starts on
 * @param profile the profile the setting applies to, from the file name or {@code spring.config.activate.on-profile}
 * @param valueStart offset of the value in the file when it is written as plain text that an edit can replace
 * @param valueEnd offset just after that value
 */
public record Setting(
        String key, String value, int line, Optional<String> profile, OptionalInt valueStart, OptionalInt valueEnd) {

    /** Spring's relaxed binding: {@code server.forward-headers}, {@code server.forwardHeaders} and {@code FORWARD_HEADERS} match. */
    public static String canonical(String key) {
        return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    public boolean is(String key) {
        return canonical(this.key).equals(canonical(key));
    }

    public boolean startsWith(String prefix) {
        return canonical(this.key).startsWith(canonical(prefix));
    }

    /** {@code ${DB_PASSWORD}} and similar: resolved from the environment, not written in the file. */
    public boolean isPlaceholder() {
        return value.contains("${");
    }
}
