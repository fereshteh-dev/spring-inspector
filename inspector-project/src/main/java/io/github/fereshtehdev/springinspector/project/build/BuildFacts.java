package io.github.fereshtehdev.springinspector.project.build;

import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record BuildFacts(
        Optional<Version> java, Optional<Version> springBoot, Optional<Version> springAi, List<Path> modules) {

    public BuildFacts {
        modules = List.copyOf(modules);
    }

    public static BuildFacts none() {
        return new BuildFacts(Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }
}
