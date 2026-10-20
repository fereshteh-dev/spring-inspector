package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.Check;
import java.util.List;

final class CheckSelection {

    private CheckSelection() {}

    static List<Check> applicableTo(ProjectContext project, List<Check> checks) {
        return checks.stream()
                .filter(check -> project.config().isEnabled(check.id(), check.category()))
                .filter(check -> check.applicability().appliesTo(project.versions()))
                .toList();
    }
}
