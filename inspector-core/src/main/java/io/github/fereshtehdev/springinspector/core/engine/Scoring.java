package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.report.Score;
import java.util.List;

final class Scoring {

    private Scoring() {}

    static Score score(List<Finding> findings) {
        int penalty = findings.stream()
                .filter(finding -> finding.source() != FindingSource.AI)
                .mapToInt(Scoring::penalty)
                .sum();
        return new Score(Math.max(0, 100 - penalty));
    }

    private static int penalty(Finding finding) {
        return switch (finding.severity()) {
            case BLOCKER -> 20;
            case MAJOR -> 8;
            case MINOR -> 2;
            case INFO -> 0;
        };
    }
}
