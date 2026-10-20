package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import java.util.List;

public interface AdvisorySource {

    List<Finding> findingsFor(ProjectContext project);
}
