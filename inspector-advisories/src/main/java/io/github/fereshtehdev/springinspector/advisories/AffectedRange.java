package io.github.fereshtehdev.springinspector.advisories;

import java.util.Optional;

/**
 * Versions from {@code introduced} (inclusive; empty means from the first version) up to {@code fixed} (exclusive)
 * or {@code lastAffected} (inclusive); with neither, every later version is affected.
 */
public record AffectedRange(Optional<String> introduced, Optional<String> fixed, Optional<String> lastAffected) {

    public boolean contains(MavenVersion version) {
        boolean started = introduced
                .map(start -> MavenVersion.parse(start).compareTo(version) <= 0)
                .orElse(true);
        boolean beforeFix =
                fixed.map(end -> version.compareTo(MavenVersion.parse(end)) < 0).orElse(true);
        boolean upToLast = lastAffected
                .map(end -> version.compareTo(MavenVersion.parse(end)) <= 0)
                .orElse(true);
        return started && beforeFix && upToLast;
    }
}
