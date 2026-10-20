package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import java.util.Comparator;

final class FindingRanking {

    static final Comparator<Finding> ORDER = Comparator.comparing(Finding::severity)
            .thenComparing(Finding::confidence)
            .thenComparing(Finding::source)
            .thenComparing(finding -> finding.location().file())
            .thenComparing(finding -> finding.location().lines())
            .thenComparing(Finding::ruleId);

    private FindingRanking() {}
}
