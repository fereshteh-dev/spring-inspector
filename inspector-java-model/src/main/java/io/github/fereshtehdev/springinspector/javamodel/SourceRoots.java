package io.github.fereshtehdev.springinspector.javamodel;

import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Conventional Maven and Gradle Java source folders of every module, relative to the project root. */
final class SourceRoots {

    private static final List<Path> CONVENTIONAL =
            List.of(Path.of("src", "main", "java"), Path.of("src", "test", "java"));

    private SourceRoots() {}

    static List<Path> of(ProjectContext project) {
        Set<Path> roots = new LinkedHashSet<>();
        CONVENTIONAL.forEach(roots::add);
        for (Path module : project.modules()) {
            CONVENTIONAL.forEach(root -> roots.add(module.resolve(root).normalize()));
        }
        return List.copyOf(roots);
    }
}
