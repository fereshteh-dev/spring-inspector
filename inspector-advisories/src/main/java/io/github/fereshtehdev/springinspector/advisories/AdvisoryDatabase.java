package io.github.fereshtehdev.springinspector.advisories;

import io.github.fereshtehdev.springinspector.advisories.Advisory.AffectedPackage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The local copy of the advisories, indexed by package, with when and from what it was fetched. */
public final class AdvisoryDatabase {

    private final List<Advisory> advisories;
    private final Metadata metadata;
    private final Map<String, List<Match>> byPackage;

    public AdvisoryDatabase(List<Advisory> advisories, Metadata metadata) {
        this.advisories = List.copyOf(advisories);
        this.metadata = metadata;
        this.byPackage = new HashMap<>();
        for (Advisory advisory : this.advisories) {
            for (AffectedPackage affected : advisory.packages()) {
                byPackage
                        .computeIfAbsent(affected.coordinate(), key -> new ArrayList<>())
                        .add(new Match(advisory, affected));
            }
        }
    }

    public List<Advisory> advisories() {
        return advisories;
    }

    public Metadata metadata() {
        return metadata;
    }

    /**
     * The advisories affecting this version of {@code groupId:artifactId}, each once even when it lists the package
     * in several entries.
     */
    public List<Match> affecting(String coordinate, String version) {
        Map<String, Match> matches = new LinkedHashMap<>();
        for (Match match : byPackage.getOrDefault(coordinate, List.of())) {
            if (match.affected().affects(version)) {
                matches.putIfAbsent(match.advisory().id(), match);
            }
        }
        return List.copyOf(matches.values());
    }

    /** One advisory and the package entry of it that matched. */
    public record Match(Advisory advisory, AffectedPackage affected) {}

    /**
     * Where the data came from and how fresh it is.
     *
     * @param fetchedAt when this machine last checked the source
     * @param sourceVersion the source's own version marker, such as an ETag, used to skip unchanged downloads
     */
    public record Metadata(Instant fetchedAt, Optional<String> sourceVersion) {}
}
