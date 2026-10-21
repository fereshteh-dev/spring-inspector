package io.github.fereshtehdev.springinspector.project.dependencies;

import java.nio.file.Path;

/** A declared library whose version could not be worked out without running the build. */
public record Unresolved(String coordinate, Path declaredIn, int line, String reason) {}
