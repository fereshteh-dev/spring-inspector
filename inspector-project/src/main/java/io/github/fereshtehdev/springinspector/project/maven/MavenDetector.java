package io.github.fereshtehdev.springinspector.project.maven;

import io.github.fereshtehdev.springinspector.project.build.BuildFacts;
import io.github.fereshtehdev.springinspector.project.build.BuildFileReader;
import io.github.fereshtehdev.springinspector.project.build.JavaVersions;
import io.github.fereshtehdev.springinspector.project.build.ModulePaths;
import io.github.fereshtehdev.springinspector.project.build.Placeholders;
import io.github.fereshtehdev.springinspector.project.build.VersionCandidates;
import io.github.fereshtehdev.springinspector.project.maven.Pom.Coordinates;
import io.github.fereshtehdev.springinspector.project.maven.Pom.Plugin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class MavenDetector {

    public static final Path POM = Path.of("pom.xml");

    private static final int MAX_MODULE_DEPTH = 10;
    private static final String BOOT_GROUP = "org.springframework.boot";
    private static final String SPRING_AI_GROUP = "org.springframework.ai";
    private static final Set<String> BOOT_PARENTS =
            Set.of("spring-boot-starter-parent", "spring-boot-parent", "spring-boot-dependencies");

    private final BuildFileReader reader;
    private final VersionCandidates java = new VersionCandidates();
    private final VersionCandidates springBoot = new VersionCandidates();
    private final VersionCandidates springAi = new VersionCandidates();
    private final List<Path> modules = new ArrayList<>();
    private final Set<Path> visited = new HashSet<>();

    private MavenDetector(BuildFileReader reader) {
        this.reader = reader;
    }

    public static BuildFacts detect(BuildFileReader reader) {
        MavenDetector detector = new MavenDetector(reader);
        detector.visit(Path.of(""), Map.of(), 0);
        return new BuildFacts(
                detector.java.best(), detector.springBoot.best(), detector.springAi.best(), detector.modules);
    }

    private void visit(Path directory, Map<String, String> inheritedProperties, int depth) {
        if (!visited.add(directory)) {
            return;
        }
        Path pomPath = directory.resolve(POM);
        Optional<String> xml = reader.read(pomPath);
        if (xml.isEmpty()) {
            return;
        }
        Pom pom;
        try {
            pom = PomReader.parse(xml.get());
        } catch (IllegalArgumentException e) {
            reader.warn(pomPath + " could not be parsed: " + e.getMessage());
            return;
        }
        Map<String, String> properties = new HashMap<>(inheritedProperties);
        properties.putAll(pom.properties());
        collect(pom, properties);
        for (String declared : pom.modules()) {
            visitModule(directory, declared, properties, depth);
        }
    }

    private void visitModule(Path directory, String declared, Map<String, String> properties, int depth) {
        Optional<Path> module =
                ModulePaths.insideProject(directory, declared.endsWith(".xml") ? parentOf(declared) : declared);
        if (module.isEmpty()) {
            reader.warn("Module '" + declared + "' in " + directory.resolve(POM)
                    + " is outside the project and was skipped");
            return;
        }
        if (depth >= MAX_MODULE_DEPTH) {
            reader.warn("Modules nested deeper than " + MAX_MODULE_DEPTH + " levels were skipped");
            return;
        }
        modules.add(module.get());
        visit(module.get(), properties, depth + 1);
    }

    private static String parentOf(String declaredPomFile) {
        Path parent = Path.of(declaredPomFile).getParent();
        return parent == null ? "." : parent.toString();
    }

    private void collect(Pom pom, Map<String, String> properties) {
        Optional<Plugin> compiler = plugin(pom, "maven-compiler-plugin");
        Optional<Plugin> toolchains = plugin(pom, "maven-toolchains-plugin");
        Stream.of(
                        property(properties, "maven.compiler.release"),
                        compiler.flatMap(p -> p.config("release")),
                        property(properties, "java.version"),
                        property(properties, "maven.compiler.target"),
                        compiler.flatMap(p -> p.config("target")),
                        property(properties, "maven.compiler.source"),
                        compiler.flatMap(p -> p.config("source")),
                        toolchains.flatMap(p -> p.config("toolchains.jdk.version")))
                .map(raw -> raw.flatMap(value -> Placeholders.resolveBraced(value, properties)))
                .forEach(candidate -> java.offer(candidate, JavaVersions::parse));

        springBoot
                .offer(versionOf(
                        pom.parent()
                                .filter(p -> p.groupId().equals(BOOT_GROUP) && BOOT_PARENTS.contains(p.artifactId())),
                        properties))
                .offer(versionOf(find(pom.managedDependencies(), BOOT_GROUP, "spring-boot-dependencies"), properties))
                .offer(versionOf(
                        pom.plugins().stream()
                                .map(Plugin::coordinates)
                                .filter(c -> c.is(BOOT_GROUP, "spring-boot-maven-plugin"))
                                .findFirst(),
                        properties));

        springAi.offer(versionOf(find(pom.managedDependencies(), SPRING_AI_GROUP, "spring-ai-bom"), properties))
                .offer(versionOf(
                        Stream.concat(pom.managedDependencies().stream(), pom.dependencies().stream())
                                .filter(c -> c.groupId().equals(SPRING_AI_GROUP)
                                        && c.version().isPresent())
                                .findFirst(),
                        properties));
    }

    private static Optional<Plugin> plugin(Pom pom, String artifactId) {
        return pom.plugins().stream()
                .filter(plugin -> plugin.coordinates().is("org.apache.maven.plugins", artifactId))
                .findFirst();
    }

    private static Optional<Coordinates> find(List<Coordinates> dependencies, String groupId, String artifactId) {
        return dependencies.stream().filter(c -> c.is(groupId, artifactId)).findFirst();
    }

    private static Optional<String> versionOf(Optional<Coordinates> coordinates, Map<String, String> properties) {
        return coordinates.flatMap(Coordinates::version).flatMap(raw -> Placeholders.resolveBraced(raw, properties));
    }

    private static Optional<String> property(Map<String, String> properties, String name) {
        return Optional.ofNullable(properties.get(name));
    }
}
