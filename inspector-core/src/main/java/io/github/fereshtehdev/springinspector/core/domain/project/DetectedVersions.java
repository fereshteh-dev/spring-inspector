package io.github.fereshtehdev.springinspector.core.domain.project;

import java.util.Optional;

public record DetectedVersions(Optional<Version> java, Optional<Version> springBoot, Optional<Version> springAi) {

    public static DetectedVersions none() {
        return new DetectedVersions(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
