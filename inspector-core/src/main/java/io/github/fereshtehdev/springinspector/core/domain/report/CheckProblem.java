package io.github.fereshtehdev.springinspector.core.domain.report;

import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import java.time.Duration;

public sealed interface CheckProblem {

    RuleId ruleId();

    record Failed(RuleId ruleId, String reason) implements CheckProblem {}

    record TimedOut(RuleId ruleId, Duration timeout) implements CheckProblem {}
}
