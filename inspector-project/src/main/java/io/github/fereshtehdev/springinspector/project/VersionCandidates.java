package io.github.fereshtehdev.springinspector.project;

import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import java.util.Optional;
import java.util.function.Function;

public final class VersionCandidates {

    private Optional<Version> found = Optional.empty();

    public VersionCandidates offer(Optional<String> raw) {
        return offer(raw, VersionCandidates::parseVersion);
    }

    public VersionCandidates offer(Optional<String> raw, Function<String, Optional<Version>> parser) {
        if (found.isEmpty()) {
            found = raw.flatMap(parser);
        }
        return this;
    }

    public Optional<Version> best() {
        return found;
    }

    private static Optional<Version> parseVersion(String raw) {
        try {
            return Optional.of(Version.parse(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
