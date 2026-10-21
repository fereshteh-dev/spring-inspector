package io.github.fereshtehdev.springinspector.project.maven;

import java.util.List;
import java.util.Map;
import java.util.Optional;

record Pom(
        Optional<Coordinates> parent,
        Map<String, String> properties,
        List<Coordinates> managedDependencies,
        List<Coordinates> dependencies,
        List<Plugin> plugins,
        List<String> modules,
        Coordinates self) {

    Pom {
        properties = Map.copyOf(properties);
        managedDependencies = List.copyOf(managedDependencies);
        dependencies = List.copyOf(dependencies);
        plugins = List.copyOf(plugins);
        modules = List.copyOf(modules);
    }

    /** The project's own groupId and version, inherited from the parent when not declared. */
    String groupId() {
        return self.groupId().isEmpty() ? parent.map(Coordinates::groupId).orElse("") : self.groupId();
    }

    Optional<String> version() {
        return self.version().or(() -> parent.flatMap(Coordinates::version));
    }

    /** Entries of {@code dependencyManagement} with {@code <scope>import</scope>}: BOMs whose versions apply here. */
    List<Coordinates> importedBoms() {
        return managedDependencies.stream()
                .filter(dependency ->
                        dependency.scope().filter("import"::equals).isPresent())
                .toList();
    }

    record Coordinates(String groupId, String artifactId, Optional<String> version, Optional<String> scope) {

        Coordinates(String groupId, String artifactId, Optional<String> version) {
            this(groupId, artifactId, version, Optional.empty());
        }

        String key() {
            return groupId + ":" + artifactId;
        }

        boolean is(String group, String artifact) {
            return groupId.equals(group) && artifactId.equals(artifact);
        }
    }

    record Plugin(Coordinates coordinates, Map<String, String> configuration) {

        Plugin {
            configuration = Map.copyOf(configuration);
        }

        Optional<String> config(String key) {
            return Optional.ofNullable(configuration.get(key));
        }
    }
}
