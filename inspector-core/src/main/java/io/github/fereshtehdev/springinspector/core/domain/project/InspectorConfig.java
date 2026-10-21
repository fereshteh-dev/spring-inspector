package io.github.fereshtehdev.springinspector.core.domain.project;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How an inspection behaves: what runs, how findings are weighed and shown, and what blocks a commit.
 *
 * @param ruleOptions settings per rule, read by the rule itself
 * @param ignoredPaths files that are never inspected, such as generated sources
 */
public record InspectorConfig(
        Set<Category> disabledCategories,
        Set<RuleId> disabledRules,
        Map<RuleId, Severity> severityOverrides,
        Duration checkTimeout,
        int maxFindingsPerCategory,
        boolean changedLinesOnly,
        GatePolicy gatePolicy,
        Map<RuleId, RuleOptions> ruleOptions,
        List<PathPattern> ignoredPaths) {

    public static final Duration DEFAULT_CHECK_TIMEOUT = Duration.ofSeconds(5);
    public static final int DEFAULT_MAX_FINDINGS_PER_CATEGORY = 20;

    public InspectorConfig {
        disabledCategories = Set.copyOf(disabledCategories);
        disabledRules = Set.copyOf(disabledRules);
        severityOverrides = Map.copyOf(severityOverrides);
        ruleOptions = Map.copyOf(ruleOptions);
        ignoredPaths = List.copyOf(ignoredPaths);
        if (checkTimeout.isNegative() || checkTimeout.isZero()) {
            throw new IllegalArgumentException("Check timeout must be positive, got " + checkTimeout);
        }
        if (maxFindingsPerCategory < 1) {
            throw new IllegalArgumentException(
                    "At least one finding per category must be shown, got " + maxFindingsPerCategory);
        }
    }

    public InspectorConfig(
            Set<Category> disabledCategories,
            Set<RuleId> disabledRules,
            Map<RuleId, Severity> severityOverrides,
            Duration checkTimeout,
            int maxFindingsPerCategory,
            boolean changedLinesOnly,
            GatePolicy gatePolicy) {
        this(
                disabledCategories,
                disabledRules,
                severityOverrides,
                checkTimeout,
                maxFindingsPerCategory,
                changedLinesOnly,
                gatePolicy,
                Map.of(),
                List.of());
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

    public RuleOptions options(RuleId ruleId) {
        return ruleOptions.getOrDefault(ruleId, RuleOptions.none());
    }

    public boolean isIgnored(Path projectRelativePath) {
        return ignoredPaths.stream().anyMatch(pattern -> pattern.matches(projectRelativePath));
    }
}
