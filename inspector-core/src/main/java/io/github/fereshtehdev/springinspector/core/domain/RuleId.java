package io.github.fereshtehdev.springinspector.core.domain;

import java.util.regex.Pattern;

public record RuleId(String value) implements Comparable<RuleId> {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public RuleId {
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Rule id must be letters, digits, '.', '_' or '-': '" + value + "'");
        }
    }

    public static RuleId of(String value) {
        return new RuleId(value);
    }

    @Override
    public int compareTo(RuleId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
