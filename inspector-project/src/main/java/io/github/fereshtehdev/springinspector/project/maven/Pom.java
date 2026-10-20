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
        List<String> modules) {

    Pom {
        properties = Map.copyOf(properties);
        managedDependencies = List.copyOf(managedDependencies);
        dependencies = List.copyOf(dependencies);
        plugins = List.copyOf(plugins);
        modules = List.copyOf(modules);
    }

    record Coordinates(String groupId, String artifactId, Optional<String> version) {

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
