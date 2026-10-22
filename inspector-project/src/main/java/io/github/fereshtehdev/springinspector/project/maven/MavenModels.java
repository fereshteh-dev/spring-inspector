package io.github.fereshtehdev.springinspector.project.maven;

import io.github.fereshtehdev.springinspector.project.build.Placeholders;
import io.github.fereshtehdev.springinspector.project.dependencies.LocalRepository;
import io.github.fereshtehdev.springinspector.project.maven.Pom.Coordinates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Works out managed versions the way Maven does, without running it: properties and {@code dependencyManagement}
 * inherit down the parent chain (the child's properties win, also inside a parent's entries), and imported BOMs add
 * their own, already resolved, versions after that. Parents and BOMs outside the project come from the local cache.
 */
final class MavenModels {

    private static final int MAX_DEPTH = 8;

    private final LocalRepository repository;
    private final Function<String, Optional<Pom>> projectPoms;
    private final Map<String, Optional<Effective>> boms = new HashMap<>();

    /** Uses the project's own POMs, by {@code groupId:artifactId}, as parents before looking in the local cache. */
    MavenModels(LocalRepository repository, Function<String, Optional<Pom>> projectPoms) {
        this.repository = repository;
        this.projectPoms = projectPoms;
    }

    /**
     * The resolved properties and managed versions in effect for one POM.
     *
     * @param managed version and the BOM or parent that supplies it, by {@code groupId:artifactId}
     */
    record Effective(Map<String, String> properties, Map<String, Managed> managed) {}

    record Managed(String version, String source) {}

    Effective effective(Pom pom) {
        return effective(pom, 0);
    }

    private Effective effective(Pom pom, int depth) {
        List<Labelled> chain = chain(pom);
        Map<String, String> properties = new HashMap<>();
        for (Labelled link : chain.reversed()) {
            properties.putAll(link.pom().properties());
        }
        pom.version().ifPresent(version -> {
            properties.put("project.version", version);
            properties.put("version", version);
        });
        properties.put("project.groupId", pom.groupId());
        properties.put("project.artifactId", pom.self().artifactId());
        pom.parent()
                .flatMap(Coordinates::version)
                .ifPresent(version -> properties.put("project.parent.version", version));

        // Versions from parents outside the project are credited to the parent the build names, which is what to
        // upgrade.
        String external = chain.stream()
                .filter(link -> !link.local())
                .findFirst()
                .map(Labelled::label)
                .orElse("");
        Map<String, Managed> managed = new LinkedHashMap<>();
        for (Labelled link : chain) {
            String source = link.local() ? link.label() : external;
            for (Coordinates entry : link.pom().managedDependencies()) {
                if (entry.scope().filter("import"::equals).isEmpty() && !managed.containsKey(entry.key())) {
                    entry.version()
                            .flatMap(raw -> Placeholders.resolveBraced(raw, properties))
                            .ifPresent(version -> managed.put(entry.key(), new Managed(version, source)));
                }
            }
        }
        if (depth < MAX_DEPTH) {
            for (Labelled link : chain) {
                for (Coordinates bom : link.pom().importedBoms()) {
                    bom.version()
                            .flatMap(raw -> Placeholders.resolveBraced(raw, properties))
                            .ifPresent(version -> importBom(bom, version, depth, managed));
                }
            }
        }
        return new Effective(properties, managed);
    }

    private void importBom(Coordinates bom, String version, int depth, Map<String, Managed> managed) {
        String label = bom.artifactId() + " " + version;
        bom(bom.groupId(), bom.artifactId(), version, depth + 1)
                .ifPresent(imported -> imported.managed()
                        .forEach((key, value) -> managed.putIfAbsent(key, new Managed(value.version(), label))));
    }

    private Optional<Effective> bom(String groupId, String artifactId, String version, int depth) {
        String key = groupId + ":" + artifactId + ":" + version;
        Optional<Effective> known = boms.get(key);
        if (known != null) {
            return known;
        }
        boms.put(key, Optional.empty());
        Optional<Effective> resolved = repository
                .pom(groupId, artifactId, version)
                .flatMap(MavenModels::parse)
                .map(pom -> effective(pom, depth));
        boms.put(key, resolved);
        return resolved;
    }

    /** The POM and its ancestors, nearest first, as far as they can be found. */
    private List<Labelled> chain(Pom pom) {
        List<Labelled> chain = new ArrayList<>();
        chain.add(new Labelled(pom, label(pom), true));
        Optional<Coordinates> parent = pom.parent();
        while (parent.isPresent() && chain.size() <= MAX_DEPTH) {
            Coordinates coordinates = parent.get();
            Optional<Pom> local = projectPoms.apply(coordinates.key());
            Optional<Pom> found = local.or(() -> coordinates
                    .version()
                    .flatMap(version -> repository.pom(coordinates.groupId(), coordinates.artifactId(), version))
                    .flatMap(MavenModels::parse));
            if (found.isEmpty()) {
                break;
            }
            chain.add(new Labelled(found.get(), label(found.get()), local.isPresent()));
            parent = found.get().parent();
        }
        return chain;
    }

    private static String label(Pom pom) {
        return pom.self().artifactId()
                + pom.version().map(version -> " " + version).orElse("");
    }

    static Optional<Pom> parse(String xml) {
        try {
            return Optional.of(PomReader.parse(xml));
        } catch (IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }

    /** One POM of a chain; {@code local} when it is part of the project rather than from the cache. */
    private record Labelled(Pom pom, String label, boolean local) {}
}
