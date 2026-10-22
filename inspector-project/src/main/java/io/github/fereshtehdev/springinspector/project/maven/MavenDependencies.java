package io.github.fereshtehdev.springinspector.project.maven;

import io.github.fereshtehdev.springinspector.project.build.BuildFileReader;
import io.github.fereshtehdev.springinspector.project.build.ModulePaths;
import io.github.fereshtehdev.springinspector.project.build.Placeholders;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency.Origin;
import io.github.fereshtehdev.springinspector.project.dependencies.LocalRepository;
import io.github.fereshtehdev.springinspector.project.dependencies.ProjectDependencies;
import io.github.fereshtehdev.springinspector.project.dependencies.Unresolved;
import io.github.fereshtehdev.springinspector.project.maven.MavenModels.Effective;
import io.github.fereshtehdev.springinspector.project.maven.MavenModels.Managed;
import io.github.fereshtehdev.springinspector.project.maven.Pom.Coordinates;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The libraries every POM of the project declares, with versions resolved through properties, parents and BOMs. */
public final class MavenDependencies {

    private static final int MAX_POMS = 500;

    private MavenDependencies() {}

    public static ProjectDependencies resolve(BuildFileReader reader, LocalRepository repository) {
        Map<Path, Source> sources = projectPoms(reader);
        Map<String, Pom> byKey = new HashMap<>();
        sources.values().forEach(source -> byKey.putIfAbsent(key(source.pom()), source.pom()));
        MavenModels models = new MavenModels(repository, key -> Optional.ofNullable(byKey.get(key)));

        List<Dependency> resolved = new ArrayList<>();
        List<Unresolved> unresolved = new ArrayList<>();
        sources.forEach((path, source) -> {
            Effective effective = models.effective(source.pom());
            PomLines lines = new PomLines(source.xml());
            for (Coordinates declared : source.pom().dependencies()) {
                if (byKey.containsKey(declared.key()) || declared.groupId().isEmpty()) {
                    continue;
                }
                int line = lines.lineOf(declared.artifactId());
                Optional<Managed> managed =
                        Optional.ofNullable(effective.managed().get(declared.key()));
                Optional<String> raw = declared.version();
                if (raw.isPresent()) {
                    Placeholders.resolveBraced(raw.get(), effective.properties())
                            .ifPresentOrElse(
                                    version -> resolved.add(dependency(
                                            declared, version, Origin.DECLARED, path, line, Optional.empty())),
                                    () -> unresolved.add(new Unresolved(
                                            declared.key(),
                                            path,
                                            line,
                                            "its version " + raw.get() + " uses a property that is not defined")));
                } else if (managed.isPresent()) {
                    resolved.add(dependency(
                            declared,
                            managed.get().version(),
                            Origin.MANAGED,
                            path,
                            line,
                            Optional.of(managed.get().source())));
                } else {
                    unresolved.add(new Unresolved(
                            declared.key(),
                            path,
                            line,
                            "its version comes from a parent or BOM that is not in the local Maven or Gradle cache"));
                }
            }
        });
        return new ProjectDependencies(resolved, unresolved);
    }

    private static String key(Pom pom) {
        return pom.groupId() + ":" + pom.self().artifactId();
    }

    private static Dependency dependency(
            Coordinates declared, String version, Origin origin, Path path, int line, Optional<String> managedBy) {
        return new Dependency(
                declared.groupId(),
                declared.artifactId(),
                version,
                origin,
                path,
                line,
                declared.scope(),
                managedBy,
                Optional.empty());
    }

    /** The root POM and every module below it, by path relative to the project root. */
    private static Map<Path, Source> projectPoms(BuildFileReader reader) {
        Map<Path, Source> sources = new LinkedHashMap<>();
        Set<Path> seen = new HashSet<>();
        Deque<Path> directories = new ArrayDeque<>(List.of(Path.of("")));
        while (!directories.isEmpty() && sources.size() < MAX_POMS) {
            Path directory = directories.removeFirst();
            if (!seen.add(directory)) {
                continue;
            }
            Path path = directory.resolve(MavenDetector.POM);
            Optional<String> xml = reader.read(path);
            Optional<Pom> pom = xml.flatMap(MavenModels::parse);
            if (xml.isPresent() && pom.isPresent()) {
                sources.put(path, new Source(pom.get(), xml.get()));
                for (String declared : pom.get().modules()) {
                    ModulePaths.insideProject(directory, moduleDirectory(declared))
                            .ifPresent(directories::addLast);
                }
            }
        }
        return sources;
    }

    private static String moduleDirectory(String declared) {
        if (!declared.endsWith(".xml")) {
            return declared;
        }
        Path parent = Path.of(declared).getParent();
        return parent == null ? "" : parent.toString();
    }

    private record Source(Pom pom, String xml) {}
}
