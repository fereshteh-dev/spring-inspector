package io.github.fereshtehdev.springinspector.core.domain;

import java.nio.file.Path;

public final class ProjectPath {

    private ProjectPath() {}

    public static Path requireRelative(Path path) {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("Path must be relative to the project root: " + path);
        }
        Path normalized = path.normalize();
        if (normalized.startsWith("..")) {
            throw new IllegalArgumentException("Path escapes the project root: " + path);
        }
        return normalized;
    }
}
