package io.github.fereshtehdev.springinspector.core.domain.project;

import java.util.Optional;

public sealed interface VersionRequirement {

    static VersionRequirement any() {
        return Any.INSTANCE;
    }

    static VersionRequirement within(VersionRange range) {
        return new Within(range);
    }

    default boolean isSatisfiedBy(Optional<Version> detected) {
        return switch (this) {
            case Any any -> true;
            case Within(VersionRange range) -> detected.map(range::contains).orElse(false);
        };
    }

    enum Any implements VersionRequirement {
        INSTANCE
    }

    record Within(VersionRange range) implements VersionRequirement {}
}
