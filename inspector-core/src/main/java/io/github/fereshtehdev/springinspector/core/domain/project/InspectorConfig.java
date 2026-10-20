package io.github.fereshtehdev.springinspector.core.domain.project;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public record InspectorConfig(
        Set<Category> disabledCategories,
        Set<RuleId> disabledRules,
        Map<RuleId, Severity> severityOverrides,
        Duration checkTimeout,
        int maxFindingsPerCategory,
        boolean changedLinesOnly,
        GatePolicy gatePolicy) {

    public static final Duration DEFAULT_CHECK_TIMEOUT = Duration.ofSeconds(5);
    public static final int DEFAULT_MAX_FINDINGS_PER_CATEGORY = 20;

    public InspectorConfig {
        disabledCategories = Set.copyOf(disabledCategories);
        disabledRules = Set.copyOf(disabledRules);
        severityOverrides = Map.copyOf(severityOverrides);
        if (checkTimeout.isNegative() || checkTimeout.isZero()) {
            throw new IllegalArgumentException("Check timeout must be positive, got " + checkTimeout);
        }
        if (maxFindingsPerCategory < 1) {
            throw new IllegalArgumentException(
                    "At least one finding per category must be shown, got " + maxFindingsPerCategory);
        }
    }

    public static InspectorConfig defaults() {
        return new InspectorConfig(
                Set.of(),
                Set.of(),
                Map.of(),
                DEFAULT_CHECK_TIMEOUT,
                DEFAULT_MAX_FINDINGS_PER_CATEGORY,
                true,
                GatePolicy.defaults());
    }

    public boolean isEnabled(RuleId ruleId, Category category) {
        return !disabledRules.contains(ruleId) && !disabledCategories.contains(category);
    }
}
