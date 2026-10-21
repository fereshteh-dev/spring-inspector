package io.github.fereshtehdev.springinspector.core.domain.report;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of an inspection: results per category, score, gate decision and what could not be checked.
 *
 * @param suppressed findings silenced by an {@code inspector:ignore} comment; they count towards neither score nor gate
 * @param suppressionsWithoutReason {@code inspector:ignore} comments that were not honoured because they give no reason
 * @param advisories how current the vulnerability advisories were, when dependencies are checked
 */
public record ReportCard(
        List<CategoryResult> categories,
        Score score,
        GateDecision gate,
        List<CheckProblem> problems,
        List<SuppressedFinding> suppressed,
        List<Location> suppressionsWithoutReason,
        Optional<AdvisoryStatus> advisories) {

    public ReportCard {
        categories = List.copyOf(categories);
        problems = List.copyOf(problems);
        suppressed = List.copyOf(suppressed);
        suppressionsWithoutReason = List.copyOf(suppressionsWithoutReason);
    }

    public ReportCard(
            List<CategoryResult> categories,
            Score score,
            GateDecision gate,
            List<CheckProblem> problems,
            List<SuppressedFinding> suppressed,
            List<Location> suppressionsWithoutReason) {
        this(categories, score, gate, problems, suppressed, suppressionsWithoutReason, Optional.empty());
    }

    public ReportCard(List<CategoryResult> categories, Score score, GateDecision gate, List<CheckProblem> problems) {
        this(categories, score, gate, problems, List.of(), List.of());
    }

    public ReportCard withAdvisories(AdvisoryStatus status) {
        return new ReportCard(
                categories, score, gate, problems, suppressed, suppressionsWithoutReason, Optional.of(status));
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
