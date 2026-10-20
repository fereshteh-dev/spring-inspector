package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.report.CheckProblem;
import java.util.List;

record CheckRun(List<Finding> findings, List<CheckProblem> problems) {

    CheckRun {
        findings = List.copyOf(findings);
        problems = List.copyOf(problems);
    }
}
