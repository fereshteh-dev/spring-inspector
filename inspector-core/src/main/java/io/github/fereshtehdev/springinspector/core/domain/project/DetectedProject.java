package io.github.fereshtehdev.springinspector.core.domain.project;

import java.nio.file.Path;
import java.util.List;

public record DetectedProject(
        Path root, DetectedVersions versions, BuildTool buildTool, List<Path> modules, List<String> warnings) {

    public DetectedProject {
        modules = List.copyOf(modules);
        warnings = List.copyOf(warnings);
    }

    public ProjectContext withConfig(InspectorConfig config) {
        return new ProjectContext(root, versions, buildTool, modules, config);
    }
}
