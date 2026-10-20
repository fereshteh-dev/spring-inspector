package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;

public record InspectionInput(ProjectContext project, ChangeSet changeSet, SourceProvider sources) {}
