package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.project.GatePolicy;
import io.github.fereshtehdev.springinspector.core.domain.report.GateDecision;
import io.github.fereshtehdev.springinspector.core.domain.report.Score;
import java.util.ArrayList;
import java.util.List;

final class Gate {

    private Gate() {}

    static GateDecision decide(GatePolicy policy, List<Finding> findings, Score score) {
        List<String> reasons = new ArrayList<>();
        findings.stream()
                .filter(finding -> finding.source() != FindingSource.AI)
                .filter(finding -> finding.severity().isAtLeast(policy.blockOn()))
                .map(finding -> finding.severity() + " " + finding.ruleId() + " at " + finding.location())
                .forEach(reasons::add);
        policy.minimumScore().stream()
                .filter(minimum -> score.value() < minimum)
                .mapToObj(minimum -> "Score " + score.value() + " is below the minimum of " + minimum)
                .forEach(reasons::add);
        return reasons.isEmpty() ? GateDecision.pass() : new GateDecision.Block(reasons);
    }
}
