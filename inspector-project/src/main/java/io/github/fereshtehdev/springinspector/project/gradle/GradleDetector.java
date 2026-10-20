package io.github.fereshtehdev.springinspector.project.gradle;

import io.github.fereshtehdev.springinspector.project.BuildFacts;
import io.github.fereshtehdev.springinspector.project.BuildFileReader;
import io.github.fereshtehdev.springinspector.project.JavaVersions;
import io.github.fereshtehdev.springinspector.project.ModulePaths;
import io.github.fereshtehdev.springinspector.project.Placeholders;
import io.github.fereshtehdev.springinspector.project.VersionCandidates;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class GradleDetector {

    public static final List<Path> MARKER_FILES = Stream.of(
                    "settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts")
            .map(Path::of)
            .toList();

    private static final List<String> BUILD_SCRIPTS = List.of("build.gradle", "build.gradle.kts");
    private static final Path CATALOG = Path.of("gradle", "libs.versions.toml");
    private static final Path PROPERTIES = Path.of("gradle.properties");

    private static final String QUOTED = "[\"']([^\"']+)[\"']";
    private static final Pattern INCLUDE = Pattern.compile("\\binclude\\b\\s*\\(?([^\\n)]*)");
    private static final Pattern QUOTED_NAME = Pattern.compile(QUOTED);
    private static final List<Pattern> ASSIGNMENTS = List.of(
            Pattern.compile("\\bset\\(\\s*[\"'](\\w+)[\"']\\s*,\\s*" + QUOTED + "\\s*\\)"),
            Pattern.compile("\\bextra\\[\\s*[\"'](\\w+)[\"']\\s*]\\s*=\\s*" + QUOTED),
            Pattern.compile("(?:\\bext\\.|\\b(?:val|var|def)\\s+|^\\s*)(\\w+)\\s*=\\s*" + QUOTED, Pattern.MULTILINE));
    private static final Pattern BOOT_PLUGIN =
            Pattern.compile("id\\s*\\(?\\s*[\"']org\\.springframework\\.boot[\"']\\s*\\)?\\s*version\\s*\\(?\\s*(?:"
                    + QUOTED + "|(\\w+))");
    private static final Pattern BOOT_COORDINATE = Pattern.compile(
            "[\"']org\\.springframework\\.boot:spring-boot-(?:gradle-plugin|dependencies):([^\"']+)[\"']");
    private static final Pattern SPRING_AI_BOM =
            Pattern.compile("[\"']org\\.springframework\\.ai:spring-ai-bom:([^\"']+)[\"']");
    private static final Pattern SPRING_AI_ANY =
            Pattern.compile("[\"']org\\.springframework\\.ai:[\\w.-]+:([^\"']+)[\"']");
    private static final Pattern RELEASE = Pattern.compile("options\\.release(?:\\.set)?\\s*[=(]\\s*(\\d+)");
    private static final Pattern COMPATIBILITY = Pattern.compile(
            "(?:source|target)Compatibility\\s*=\\s*(?:JavaVersion\\.VERSION_([\\d_]+)|JavaVersion\\.toVersion\\(\\s*[\"']?([\\d.]+)|[\"']?([\\d.]+))");
    private static final Pattern TOOLCHAIN =
            Pattern.compile("(?:JavaLanguageVersion\\.of|jvmToolchain)\\(\\s*[\"']?(\\d+)");

    private final BuildFileReader reader;

    private GradleDetector(BuildFileReader reader) {
        this.reader = reader;
    }

    public static BuildFacts detect(BuildFileReader reader) {
        return new GradleDetector(reader).detect();
    }

    private BuildFacts detect() {
        String settings = script(Path.of("settings.gradle"), Path.of("settings.gradle.kts"));
        List<Path> modules = modules(settings);
        List<String> scripts = new ArrayList<>(List.of(settings, buildScript(Path.of(""))));
        modules.forEach(module -> scripts.add(buildScript(module)));
        Map<String, String> variables = variables(scripts);
        VersionCatalog catalog = reader.read(CATALOG).map(VersionCatalog::parse).orElseGet(VersionCatalog::empty);

        VersionCandidates java = new VersionCandidates();
        VersionCandidates springBoot = new VersionCandidates();
        VersionCandidates springAi = new VersionCandidates();
        for (Pattern pattern : List.of(RELEASE, COMPATIBILITY, TOOLCHAIN)) {
            scripts.forEach(script -> java.offer(firstGroup(pattern, script), JavaVersions::parse));
        }
        scripts.forEach(script -> springBoot
                .offer(firstGroup(BOOT_PLUGIN, script).flatMap(raw -> resolve(raw, variables)))
                .offer(firstGroup(BOOT_COORDINATE, script).flatMap(raw -> resolve(raw, variables))));
        springBoot
                .offer(catalogPlugin(catalog, "org.springframework.boot"))
                .offer(catalogLibrary(
                        catalog,
                        "org.springframework.boot",
                        name -> name.equals("spring-boot-dependencies") || name.equals("spring-boot-gradle-plugin")));
        scripts.forEach(script -> springAi.offer(
                        firstGroup(SPRING_AI_BOM, script).flatMap(raw -> resolve(raw, variables)))
                .offer(firstGroup(SPRING_AI_ANY, script).flatMap(raw -> resolve(raw, variables))));
        springAi.offer(catalogLibrary(catalog, "org.springframework.ai", name -> true));

        return new BuildFacts(java.best(), springBoot.best(), springAi.best(), modules);
    }

    private String script(Path... candidates) {
        for (Path candidate : candidates) {
            Optional<String> text = reader.read(candidate);
            if (text.isPresent()) {
                return ScriptText.withoutComments(text.get());
            }
        }
        return "";
    }

    private String buildScript(Path directory) {
        return script(BUILD_SCRIPTS.stream().map(directory::resolve).toArray(Path[]::new));
    }

    private List<Path> modules(String settings) {
        Set<Path> modules = new LinkedHashSet<>();
        Matcher include = INCLUDE.matcher(settings);
        while (include.find()) {
            Matcher name = QUOTED_NAME.matcher(include.group(1));
            while (name.find()) {
                String declared = name.group(1).replaceFirst("^:", "").replace(':', '/');
                ModulePaths.insideProject(Path.of(""), declared)
                        .ifPresentOrElse(
                                modules::add,
                                () -> reader.warn("Gradle project '" + name.group(1)
                                        + "' is outside the project and was skipped"));
            }
        }
        return List.copyOf(modules);
    }

    private Map<String, String> variables(List<String> scripts) {
        Map<String, String> variables = new HashMap<>();
        reader.read(PROPERTIES).ifPresent(text -> {
            Properties properties = new Properties();
            try {
                properties.load(new StringReader(text));
            } catch (IOException | IllegalArgumentException e) {
                reader.warn(PROPERTIES + " could not be parsed: " + e.getMessage());
            }
            properties.stringPropertyNames().forEach(name -> variables.put(name, properties.getProperty(name)));
        });
        for (String script : scripts) {
            for (Pattern assignment : ASSIGNMENTS) {
                Matcher matcher = assignment.matcher(script);
                while (matcher.find()) {
                    variables.putIfAbsent(matcher.group(1), matcher.group(2));
                }
            }
        }
        return variables;
    }

    private static Optional<String> resolve(String raw, Map<String, String> variables) {
        String expression = raw.matches("\\w+") && !raw.matches("\\d.*") ? "${" + raw + "}" : raw;
        return Placeholders.resolveBracedOrBare(expression, variables);
    }

    private static Optional<String> firstGroup(Pattern pattern, String script) {
        Matcher matcher = pattern.matcher(script);
        if (!matcher.find()) {
            return Optional.empty();
        }
        for (int group = 1; group <= matcher.groupCount(); group++) {
            String value = matcher.group(group);
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> catalogPlugin(VersionCatalog catalog, String id) {
        return catalog.plugins().stream()
                .filter(plugin -> plugin.id().equals(id))
                .flatMap(plugin -> plugin.version().stream())
                .findFirst();
    }

    private static Optional<String> catalogLibrary(
            VersionCatalog catalog, String group, Predicate<String> nameMatches) {
        return catalog.libraries().stream()
                .filter(library -> library.group().equals(group) && nameMatches.test(library.name()))
                .flatMap(library -> library.version().stream())
                .findFirst();
    }
}
