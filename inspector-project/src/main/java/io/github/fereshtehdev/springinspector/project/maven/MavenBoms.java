package io.github.fereshtehdev.springinspector.project.maven;

import io.github.fereshtehdev.springinspector.project.dependencies.LocalRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The versions a BOM manages, for builds such as Gradle's that import Maven BOMs. */
public final class MavenBoms {

    private MavenBoms() {}

    /** Versions by {@code groupId:artifactId}; empty when the BOM is not in the local cache. */
    public static Map<String, String> managedVersions(
            LocalRepository repository, String groupId, String artifactId, String version) {
        Map<String, String> versions = new LinkedHashMap<>();
        repository
                .pom(groupId, artifactId, version)
                .flatMap(MavenModels::parse)
                .map(pom -> new MavenModels(repository, key -> Optional.empty()).effective(pom))
                .ifPresent(effective ->
                        effective.managed().forEach((key, managed) -> versions.put(key, managed.version())));
        return versions;
    }
}
