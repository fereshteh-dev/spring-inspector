package io.github.fereshtehdev.springinspector.core.domain.project;

import java.nio.file.Path;
import java.util.List;

public record ProjectContext(
        Path root, DetectedVersions versions, BuildTool buildTool, List<Path> modules, InspectorConfig config) {

    public ProjectContext {
        modules = List.copyOf(modules);
    }
}
