package io.github.fereshtehdev.springinspector.project.dependencies;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A library the project uses, with the version it resolves to and where that is decided.
 *
 * @param declaredIn the build file, relative to the project root, that the finding should point at
 * @param line the line in {@code declaredIn}; 1 when the library is not declared there
 * @param managedBy the BOM or parent that supplies the version, for {@link Origin#MANAGED}
 * @param packagedIn the built archive it was found in, for {@link Origin#PACKAGED}
 */
public record Dependency(
        String groupId,
        String artifactId,
        String version,
        Origin origin,
        Path declaredIn,
        int line,
        Optional<String> scope,
        Optional<String> managedBy,
        Optional<Path> packagedIn) {

    public String coordinate() {
        return groupId + ":" + artifactId;
    }

    /** How the version was found. */
    public enum Origin {
        /** Written in the build file, possibly through a property. */
        DECLARED,
        /** Taken from {@code dependencyManagement}, a parent POM or a BOM. */
        MANAGED,
        /** Read from a library inside an application archive the project has already built. */
        PACKAGED
    }
}
