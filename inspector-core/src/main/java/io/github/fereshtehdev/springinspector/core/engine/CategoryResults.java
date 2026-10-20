package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.report.CategoryResult;
import io.github.fereshtehdev.springinspector.core.domain.report.CategoryStatus;
import io.github.fereshtehdev.springinspector.core.domain.report.SeverityCounts;
import java.util.List;
import java.util.Set;

final class CategoryResults {

    private CategoryResults() {}

    static List<CategoryResult> of(Set<Category> categories, List<Finding> rankedFindings, int maxShown) {
        return categories.stream()
                .sorted()
                .map(category -> resultFor(category, rankedFindings, maxShown))
                .toList();
    }

    private static CategoryResult resultFor(Category category, List<Finding> rankedFindings, int maxShown) {
        List<Finding> inCategory = rankedFindings.stream()
                .filter(finding -> finding.category() == category)
                .toList();
        SeverityCounts counts = SeverityCounts.of(inCategory);
        List<Finding> shown = inCategory.subList(0, Math.min(maxShown, inCategory.size()));
        return new CategoryResult(category, statusOf(counts), counts, shown, inCategory.size() - shown.size());
    }

    private static CategoryStatus statusOf(SeverityCounts counts) {
        if (counts.issues() > 0) {
            return CategoryStatus.FAILED;
        }
        return counts.total() > 0 ? CategoryStatus.HINTS : CategoryStatus.PASSED;
    }
}
