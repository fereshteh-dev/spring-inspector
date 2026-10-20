package io.github.fereshtehdev.springinspector.core.domain.report;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import java.util.List;
import java.util.Optional;

public record ReportCard(List<CategoryResult> categories, Score score, GateDecision gate, List<CheckProblem> problems) {

    public ReportCard {
        categories = List.copyOf(categories);
        problems = List.copyOf(problems);
    }

    public Optional<CategoryResult> category(Category category) {
        return categories.stream()
                .filter(result -> result.category() == category)
                .findFirst();
    }

    public boolean isComplete() {
        return problems.isEmpty();
    }
}
