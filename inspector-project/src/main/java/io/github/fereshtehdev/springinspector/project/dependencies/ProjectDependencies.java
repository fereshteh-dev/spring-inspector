package io.github.fereshtehdev.springinspector.project.dependencies;

import java.util.List;

/** The project's libraries with known versions, and those that could not be resolved offline. */
public record ProjectDependencies(List<Dependency> resolved, List<Unresolved> unresolved) {

    public ProjectDependencies {
        resolved = List.copyOf(resolved);
        unresolved = List.copyOf(unresolved);
    }
}
