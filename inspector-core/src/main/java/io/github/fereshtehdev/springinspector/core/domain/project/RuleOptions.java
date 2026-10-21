package io.github.fereshtehdev.springinspector.core.domain.project;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Settings for one rule, such as {@code max-lines: 80}, as text; the rule decides what they mean. */
public record RuleOptions(Map<String, String> values) {

    private static final RuleOptions NONE = new RuleOptions(Map.of());

    public RuleOptions {
        values = Map.copyOf(values);
    }

    public static RuleOptions none() {
        return NONE;
    }

    /** A positive whole number, or empty when not set; anything else is a configuration error. */
    public Optional<Integer> positiveInteger(String key) {
        String value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            int number = Integer.parseInt(value.strip());
            if (number > 0) {
                return Optional.of(number);
            }
        } catch (NumberFormatException notANumber) {
            // Reported below with the key, which is what the user needs.
        }
        throw new IllegalArgumentException("Option '" + key + "' must be a positive whole number, got '" + value + "'");
    }

    /** The same text for the same settings, whatever their order; part of the findings cache key. */
    public String fingerprint() {
        return new TreeMap<>(values)
                .entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(";"));
    }
}
