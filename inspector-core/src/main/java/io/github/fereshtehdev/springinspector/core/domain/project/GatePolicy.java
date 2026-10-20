package io.github.fereshtehdev.springinspector.core.domain.project;

import io.github.fereshtehdev.springinspector.core.domain.Severity;
import java.util.OptionalInt;

public record GatePolicy(Severity blockOn, OptionalInt minimumScore) {

    public GatePolicy {
        minimumScore.ifPresent(score -> {
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("Minimum score must be between 0 and 100, got " + score);
            }
        });
    }

    public static GatePolicy defaults() {
        return new GatePolicy(Severity.BLOCKER, OptionalInt.empty());
    }
}
