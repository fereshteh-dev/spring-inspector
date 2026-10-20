package io.github.fereshtehdev.springinspector.core.domain.report;

public record Score(int value) {

    public static final Score PERFECT = new Score(100);

    public Score {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Score must be between 0 and 100, got " + value);
        }
    }
}
