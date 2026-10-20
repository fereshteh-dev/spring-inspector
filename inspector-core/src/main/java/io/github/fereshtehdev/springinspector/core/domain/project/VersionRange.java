package io.github.fereshtehdev.springinspector.core.domain.project;

import java.util.Optional;

public record VersionRange(Version from, Optional<Version> until) {

    public VersionRange {
        until.ifPresent(end -> {
            if (end.compareTo(from) <= 0) {
                throw new IllegalArgumentException("Empty version range [" + from + ", " + end + ")");
            }
        });
    }

    public static VersionRange atLeast(Version from) {
        return new VersionRange(from, Optional.empty());
    }

    public static VersionRange between(Version fromInclusive, Version untilExclusive) {
        return new VersionRange(fromInclusive, Optional.of(untilExclusive));
    }

    public boolean contains(Version version) {
        return version.compareTo(from) >= 0
                && until.map(end -> version.compareTo(end) < 0).orElse(true);
    }

    @Override
    public String toString() {
        return "[" + from + ", " + until.map(Version::toString).orElse("") + ")";
    }
}
