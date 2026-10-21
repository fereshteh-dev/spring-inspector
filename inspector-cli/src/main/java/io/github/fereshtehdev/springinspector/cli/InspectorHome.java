package io.github.fereshtehdev.springinspector.cli;

import java.nio.file.Path;
import java.util.Map;

/** The per-user directory, {@code ~/.spring-inspector} unless {@code SPRING_INSPECTOR_HOME} names another. */
record InspectorHome(Path directory) {

    static final String VARIABLE = "SPRING_INSPECTOR_HOME";

    static InspectorHome from(Map<String, String> environment, Path userHome) {
        String configured = environment.get(VARIABLE);
        if (configured != null && !configured.isBlank()) {
            return new InspectorHome(Path.of(configured));
        }
        return new InspectorHome(userHome.resolve(".spring-inspector"));
    }

    /** Signs the findings caches; kept here, outside every inspected repository. */
    Path cacheKey() {
        return directory.resolve("cache.key");
    }

    /** The offline vulnerability advisories, shared by every project on this machine. */
    Path advisories() {
        return directory.resolve("advisories");
    }
}
