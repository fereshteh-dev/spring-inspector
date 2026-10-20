package io.github.fereshtehdev.springinspector.core.domain.report;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import java.util.List;

public record CategoryResult(
        Category category, CategoryStatus status, SeverityCounts counts, List<Finding> findings, int hiddenFindings) {

    public CategoryResult {
        findings = List.copyOf(findings);
        if (hiddenFindings < 0) {
            throw new IllegalArgumentException("Hidden findings cannot be negative");
        }
    }
}
