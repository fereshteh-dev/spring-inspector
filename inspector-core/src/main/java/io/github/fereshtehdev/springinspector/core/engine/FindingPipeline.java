package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FindingPipeline {

    private FindingPipeline() {}

    static List<Finding> process(List<Finding> findings, InspectorConfig config, ChangeSet changeSet) {
        Set<Key> seen = new HashSet<>();
        return findings.stream()
                .map(finding -> withOverriddenSeverity(finding, config.severityOverrides()))
                .filter(finding -> !config.changedLinesOnly() || changeSet.touches(finding.location()))
                .sorted(FindingRanking.ORDER)
                .filter(finding -> seen.add(new Key(finding.ruleId(), finding.location())))
                .toList();
    }

    private static Finding withOverriddenSeverity(Finding finding, Map<RuleId, Severity> overrides) {
        Severity override = overrides.get(finding.ruleId());
        if (override == null || finding.source() == FindingSource.AI) {
            return finding;
        }
        return finding.withSeverity(override);
    }

    private record Key(RuleId ruleId, Location location) {}
}
