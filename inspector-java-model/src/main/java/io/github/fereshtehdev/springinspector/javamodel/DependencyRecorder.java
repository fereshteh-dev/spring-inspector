package io.github.fereshtehdev.springinspector.javamodel;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Collects the project files that one check's result on one file was derived from. */
final class DependencyRecorder {

    private final Map<Path, Optional<String>> dependencies = new ConcurrentHashMap<>();

    void record(Path file, Optional<String> contentHash) {
        dependencies.put(file, contentHash);
    }

    Map<Path, Optional<String>> dependencies() {
        return Map.copyOf(dependencies);
    }
}
