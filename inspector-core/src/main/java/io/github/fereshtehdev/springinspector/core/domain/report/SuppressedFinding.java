package io.github.fereshtehdev.springinspector.core.domain.report;

import io.github.fereshtehdev.springinspector.core.domain.Finding;

/** A finding silenced by an {@code inspector:ignore} comment, kept with its reason so reviews can audit it. */
public record SuppressedFinding(Finding finding, String reason) {

    public SuppressedFinding {
        if (reason.isBlank()) {
            throw new IllegalArgumentException("A suppression needs a reason");
        }
    }
}
