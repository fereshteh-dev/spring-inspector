package io.github.fereshtehdev.springinspector.core.domain;

public record Patch(String unifiedDiff) {

    public Patch {
        if (unifiedDiff.isBlank()) {
            throw new IllegalArgumentException("A patch cannot be empty");
        }
    }
}
