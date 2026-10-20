package io.github.fereshtehdev.springinspector.core.domain.change;

import io.github.fereshtehdev.springinspector.core.domain.LineRange;

public record Hunk(LineRange changedLines) {

    public static Hunk lines(int start, int end) {
        return new Hunk(LineRange.of(start, end));
    }
}
