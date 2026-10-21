package io.github.fereshtehdev.springinspector.cli.render;

import io.github.fereshtehdev.springinspector.core.domain.report.CheckProblem;

final class Problems {

    private Problems() {}

    static String describe(CheckProblem problem) {
        return switch (problem) {
            case CheckProblem.Failed(var ruleId, String reason) -> ruleId + " failed and was skipped: " + reason;
            case CheckProblem.TimedOut(var ruleId, var timeout) ->
                ruleId + " was stopped after " + timeout.toSeconds() + " s and was skipped";
        };
    }
}
