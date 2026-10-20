package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.domain.report.Score;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InspectionEngine {

    private final List<Check> checks;

    public InspectionEngine(List<Check> checks) {
        this.checks = List.copyOf(checks);
        Set<RuleId> ids = new HashSet<>();
        for (Check check : this.checks) {
            if (!ids.add(check.id())) {
                throw new IllegalArgumentException("Two checks share the rule id " + check.id());
            }
        }
    }

    public ReportCard inspect(InspectionInput input) {
        InspectorConfig config = input.project().config();
        List<Check> selected = CheckSelection.applicableTo(input.project(), checks);
        CheckRun run = new CheckRunner(config.checkTimeout()).run(selected, input);
        List<Finding> findings = FindingPipeline.process(run.findings(), config, input.changeSet());
        Score score = Scoring.score(findings);
        return new ReportCard(
                CategoryResults.of(reportedCategories(selected, findings), findings, config.maxFindingsPerCategory()),
                score,
                Gate.decide(config.gatePolicy(), findings, score),
                run.problems());
    }

    private static Set<Category> reportedCategories(List<Check> selected, List<Finding> findings) {
        Set<Category> categories = EnumSet.noneOf(Category.class);
        selected.forEach(check -> categories.add(check.category()));
        findings.forEach(finding -> categories.add(finding.category()));
        return categories;
    }
}
