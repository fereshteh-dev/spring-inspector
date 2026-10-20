package io.github.fereshtehdev.springinspector.core.domain;

import java.util.Optional;

public record HowToFix(String explanation, Optional<Patch> patch) {

    public HowToFix {
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("A finding must say how to fix it");
        }
    }

    public static HowToFix withPatch(String explanation, Patch patch) {
        return new HowToFix(explanation, Optional.of(patch));
    }

    public static HowToFix withoutPatch(String explanation) {
        return new HowToFix(explanation, Optional.empty());
    }
}
