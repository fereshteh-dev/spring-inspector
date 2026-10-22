package io.github.fereshtehdev.springinspector.project;

import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.project.build.BuildFileReader;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency.Origin;
import io.github.fereshtehdev.springinspector.project.dependencies.LocalRepository;
import io.github.fereshtehdev.springinspector.project.dependencies.PackagedLibraries;
import io.github.fereshtehdev.springinspector.project.dependencies.PackagedLibraries.Library;
import io.github.fereshtehdev.springinspector.project.dependencies.ProjectDependencies;
import io.github.fereshtehdev.springinspector.project.dependencies.Unresolved;
import io.github.fereshtehdev.springinspector.project.gradle.GradleDependencies;
import io.github.fereshtehdev.springinspector.project.maven.MavenDependencies;
import io.github.fereshtehdev.springinspector.project.maven.MavenDetector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the project's libraries without running its build: declared in the build files, versioned through
 * properties, parents and BOMs from the local cache, and completed by the libraries inside an archive it already built.
 * A version written in the build files wins over a packaged one, which may be stale.
 */
public final class ProjectDependencyResolver {

    private static final List<String> BUILD_FILES = List.of("pom.xml", "build.gradle.kts", "build.gradle");

    private final LocalRepository repository;

    public ProjectDependencyResolver(LocalRepository repository) {
        this.repository = repository;
    }

    public ProjectDependencies resolve(ProjectContext project) {
        BuildFileReader reader = new BuildFileReader(project.root());
        ProjectDependencies declared = switch (project.buildTool()) {
            case MAVEN -> MavenDependencies.resolve(reader, repository);
            case GRADLE ->
                GradleDependencies.resolve(
                        reader,
                        project.modules(),
                        project.versions().springBoot().map(Version::toString),
                        repository);
            case UNKNOWN -> new ProjectDependencies(List.of(), List.of());
        };
        return withPackaged(declared, PackagedLibraries.find(project.root(), project.modules()), reader);
    }

    private static ProjectDependencies withPackaged(
            ProjectDependencies declared, List<Library> packaged, BuildFileReader reader) {
        Map<String, Library> byCoordinate = new LinkedHashMap<>();
        packaged.forEach(library -> byCoordinate.putIfAbsent(library.coordinate(), library));

        List<Dependency> resolved = new ArrayList<>(declared.resolved());
        List<Unresolved> unresolved = new ArrayList<>();
        Set<String> known = new HashSet<>();
        declared.resolved().forEach(dependency -> known.add(dependency.coordinate()));
        for (Unresolved missing : declared.unresolved()) {
            known.add(missing.coordinate());
            Library library = byCoordinate.get(missing.coordinate());
            if (library == null) {
                unresolved.add(missing);
            } else {
                resolved.add(packaged(library, missing.declaredIn(), missing.line()));
            }
        }
        for (Library library : byCoordinate.values()) {
            if (known.add(library.coordinate())) {
                resolved.add(packaged(library, buildFile(library.module(), reader), 1));
            }
        }
        return new ProjectDependencies(resolved, unresolved);
    }

    private static Dependency packaged(Library library, Path declaredIn, int line) {
        return new Dependency(
                library.groupId(),
                library.artifactId(),
                library.version(),
                Origin.PACKAGED,
                declaredIn,
                line,
                Optional.empty(),
                Optional.empty(),
                Optional.of(library.archive()));
    }

    private static Path buildFile(Path module, BuildFileReader reader) {
        return BUILD_FILES.stream()
                .map(module::resolve)
                .filter(reader::exists)
                .findFirst()
                .orElse(module.resolve(MavenDetector.POM));
    }
}
