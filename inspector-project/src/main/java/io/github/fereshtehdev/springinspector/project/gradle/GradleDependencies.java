package io.github.fereshtehdev.springinspector.project.gradle;

import io.github.fereshtehdev.springinspector.project.BuildFileReader;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency.Origin;
import io.github.fereshtehdev.springinspector.project.dependencies.LocalRepository;
import io.github.fereshtehdev.springinspector.project.dependencies.ProjectDependencies;
import io.github.fereshtehdev.springinspector.project.dependencies.Unresolved;
import io.github.fereshtehdev.springinspector.project.maven.MavenBoms;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The libraries Gradle build scripts declare in the common notations: {@code "group:name:version"}, the map notation,
 * and version catalog references. Versions left out come from platforms, {@code mavenBom} imports and, with the
 * Spring Boot plugin, Spring Boot's own BOM, as far as these are in the local cache.
 */
public final class GradleDependencies {

    private static final String CONFIGURATION = "\\b(?:implementation|api|compileOnly|runtimeOnly|testImplementation"
            + "|testRuntimeOnly|testCompileOnly|annotationProcessor|developmentOnly|kapt|ksp|compile|runtime)";
    private static final String PLATFORM = "(?:(?:enforcedPlatform|platform)\\s*\\(\\s*)?";
    private static final Pattern STRING_NOTATION = Pattern.compile(CONFIGURATION + "\\s*\\(?\\s*(" + PLATFORM
            + ")[\"']([\\w.\\-]+):([\\w.\\-]+)(?::([^\"':@\\s]+))?[^\"']*[\"']");
    private static final Pattern MAP_NOTATION = Pattern.compile(CONFIGURATION + "\\s*\\(?\\s*group\\s*[:=]\\s*"
            + "[\"']([\\w.\\-]+)[\"']\\s*,\\s*name\\s*[:=]\\s*[\"']([\\w.\\-]+)[\"']"
            + "(?:\\s*,\\s*version\\s*[:=]\\s*[\"']([^\"']+)[\"'])?");
    private static final Pattern CATALOG_REFERENCE =
            Pattern.compile(CONFIGURATION + "\\s*\\(?\\s*(" + PLATFORM + ")libs\\.([\\w.]+)");
    private static final Pattern MAVEN_BOM =
            Pattern.compile("\\bmavenBom\\s*\\(?\\s*[\"']([\\w.\\-]+):([\\w.\\-]+):([^\"']+)[\"']");
    private static final Pattern BOOT_PLUGIN = Pattern.compile("[\"']org\\.springframework\\.boot[\"']");
    private static final List<String> BUILD_SCRIPTS = List.of("build.gradle", "build.gradle.kts");
    private static final Path CATALOG = Path.of("gradle", "libs.versions.toml");

    private final BuildFileReader reader;
    private final LocalRepository repository;
    private final Map<String, String> managed = new LinkedHashMap<>();
    private final Map<String, String> managedBy = new LinkedHashMap<>();
    private final List<Dependency> resolved = new ArrayList<>();
    private final List<Unresolved> unresolved = new ArrayList<>();

    private GradleDependencies(BuildFileReader reader, LocalRepository repository) {
        this.reader = reader;
        this.repository = repository;
    }

    /** Resolves versions left out through Spring Boot's BOM when the project uses the Spring Boot plugin. */
    public static ProjectDependencies resolve(
            BuildFileReader reader, List<Path> modules, Optional<String> springBoot, LocalRepository repository) {
        return new GradleDependencies(reader, repository).resolve(modules, springBoot);
    }

    private ProjectDependencies resolve(List<Path> modules, Optional<String> springBoot) {
        Map<Path, String> scripts = new LinkedHashMap<>();
        for (Path directory : directories(modules)) {
            for (String name : BUILD_SCRIPTS) {
                Path path = directory.resolve(name);
                reader.read(path).ifPresent(text -> scripts.put(path, ScriptText.withoutComments(text)));
            }
        }
        Map<String, String> variables = GradleDetector.variables(reader, List.copyOf(scripts.values()));
        VersionCatalog catalog = reader.read(CATALOG).map(VersionCatalog::parse).orElseGet(VersionCatalog::empty);

        if (springBoot.isPresent()
                && scripts.values().stream()
                        .anyMatch(s -> BOOT_PLUGIN.matcher(s).find())) {
            importBom("org.springframework.boot", "spring-boot-dependencies", springBoot.get());
        }
        scripts.values().forEach(script -> boms(script, variables, catalog));
        scripts.forEach((path, script) -> declarations(path, script, variables, catalog));
        return new ProjectDependencies(resolved, unresolved);
    }

    private static List<Path> directories(List<Path> modules) {
        List<Path> directories = new ArrayList<>(List.of(Path.of("")));
        modules.stream().filter(module -> !directories.contains(module)).forEach(directories::add);
        return directories;
    }

    private void boms(String script, Map<String, String> variables, VersionCatalog catalog) {
        Matcher bom = MAVEN_BOM.matcher(script);
        while (bom.find()) {
            String group = bom.group(1);
            String name = bom.group(2);
            GradleDetector.resolve(bom.group(3), variables).ifPresent(version -> importBom(group, name, version));
        }
        Matcher platform = STRING_NOTATION.matcher(script);
        while (platform.find()) {
            String group = platform.group(2);
            String name = platform.group(3);
            if (!platform.group(1).isEmpty() && platform.group(4) != null) {
                GradleDetector.resolve(platform.group(4), variables)
                        .ifPresent(version -> importBom(group, name, version));
            }
        }
        Matcher reference = CATALOG_REFERENCE.matcher(script);
        while (reference.find()) {
            if (!reference.group(1).isEmpty()) {
                catalog.library(reference.group(2))
                        .ifPresent(library -> library.version()
                                .ifPresent(version -> importBom(library.group(), library.name(), version)));
            }
        }
    }

    private void importBom(String group, String name, String version) {
        String label = name + " " + version;
        MavenBoms.managedVersions(repository, group, name, version).forEach((key, managedVersion) -> {
            if (managed.putIfAbsent(key, managedVersion) == null) {
                managedBy.put(key, label);
            }
        });
    }

    private void declarations(Path path, String script, Map<String, String> variables, VersionCatalog catalog) {
        Matcher string = STRING_NOTATION.matcher(script);
        while (string.find()) {
            if (string.group(1).isEmpty()) {
                add(path, line(script, string.start()), string.group(2), string.group(3), string.group(4), variables);
            }
        }
        Matcher map = MAP_NOTATION.matcher(script);
        while (map.find()) {
            add(path, line(script, map.start()), map.group(1), map.group(2), map.group(3), variables);
        }
        Matcher reference = CATALOG_REFERENCE.matcher(script);
        while (reference.find()) {
            if (reference.group(1).isEmpty()) {
                int line = line(script, reference.start());
                catalog.library(reference.group(2))
                        .ifPresent(library -> add(
                                path,
                                line,
                                library.group(),
                                library.name(),
                                library.version().orElse(null),
                                variables));
            }
        }
    }

    private void add(
            Path path,
            int line,
            String group,
            String name,
            @Nullable String rawVersion,
            Map<String, String> variables) {
        String coordinate = group + ":" + name;
        if (rawVersion == null) {
            String version = managed.get(coordinate);
            if (version == null) {
                unresolved.add(new Unresolved(
                        coordinate,
                        path,
                        line,
                        "its version comes from a platform or BOM that is not in the local Maven or Gradle cache"));
            } else {
                resolved.add(dependency(
                        group,
                        name,
                        version,
                        Origin.MANAGED,
                        path,
                        line,
                        Optional.ofNullable(managedBy.get(coordinate))));
            }
            return;
        }
        Optional<String> version = GradleDetector.resolve(rawVersion, variables)
                .filter(value -> !value.contains("+") && !value.startsWith("[") && !value.startsWith("("));
        version.ifPresentOrElse(
                value -> resolved.add(dependency(group, name, value, Origin.DECLARED, path, line, Optional.empty())),
                () -> unresolved.add(new Unresolved(
                        coordinate, path, line, "its version " + rawVersion + " is dynamic or uses an unknown value")));
    }

    private static Dependency dependency(
            String group, String name, String version, Origin origin, Path path, int line, Optional<String> by) {
        return new Dependency(group, name, version, origin, path, line, Optional.empty(), by, Optional.empty());
    }

    private static int line(String script, int index) {
        return (int) script.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }
}
