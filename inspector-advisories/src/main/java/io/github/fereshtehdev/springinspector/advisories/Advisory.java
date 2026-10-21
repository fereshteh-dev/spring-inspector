package io.github.fereshtehdev.springinspector.advisories;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One vulnerability as published, for example GHSA-2m8h-fgr8-2q9w, with the Maven packages and versions it affects.
 *
 * @param aliases other ids of the same vulnerability, such as its CVE
 * @param packages the affected packages, each with its own ranges
 */
public record Advisory(
        String id, List<String> aliases, String summary, AdvisorySeverity severity, List<AffectedPackage> packages) {

    public Advisory {
        aliases = List.copyOf(aliases);
        packages = List.copyOf(packages);
    }

    /** CVE ids first, as people search for them, then the advisory's own id. */
    public List<String> identifiers() {
        List<String> cves =
                aliases.stream().filter(alias -> alias.startsWith("CVE-")).toList();
        return cves.isEmpty() ? List.of(id) : cves;
    }

    /**
     * Versions of one package that an advisory affects: listed explicitly, or covered by ranges.
     *
     * @param coordinate {@code groupId:artifactId}
     */
    public record AffectedPackage(String coordinate, List<AffectedRange> ranges, Set<String> versions) {

        public AffectedPackage {
            ranges = List.copyOf(ranges);
            versions = Set.copyOf(versions);
        }

        public boolean affects(String version) {
            if (versions.contains(version)) {
                return true;
            }
            MavenVersion parsed = MavenVersion.parse(version);
            return ranges.stream().anyMatch(range -> range.contains(parsed));
        }

        /** The lowest version that fixes the affected range containing {@code version}, if one is published. */
        public Optional<String> fixedVersionFor(String version) {
            MavenVersion parsed = MavenVersion.parse(version);
            return ranges.stream()
                    .filter(range -> range.contains(parsed))
                    .flatMap(range -> range.fixed().stream())
                    .min((a, b) -> MavenVersion.parse(a).compareTo(MavenVersion.parse(b)));
        }
    }
}
