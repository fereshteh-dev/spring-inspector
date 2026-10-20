package io.github.fereshtehdev.springinspector.core.domain;

public enum Severity {
    BLOCKER,
    MAJOR,
    MINOR,
    INFO;

    public boolean isAtLeast(Severity threshold) {
        return compareTo(threshold) <= 0;
    }
}
