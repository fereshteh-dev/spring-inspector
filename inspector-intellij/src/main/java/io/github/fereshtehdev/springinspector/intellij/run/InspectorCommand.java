package io.github.fereshtehdev.springinspector.intellij.run;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The command line the plugin runs: the bundled {@code spring-inspector.jar} on a Java 25 runtime, writing a JSON
 * report. It runs in its own process, so the inspection can neither slow down nor crash the IDE. Its output is UTF-8
 * whatever the platform's default, so summaries and file names arrive intact.
 */
public record InspectorCommand(Path java, Path jar, Path project, InspectionScope scope, List<Path> files) {

    public InspectorCommand {
        files = List.copyOf(files);
        if (scope == InspectionScope.FILES && files.isEmpty()) {
            throw new IllegalArgumentException("Name the files to inspect");
        }
    }

    public InspectorCommand(Path java, Path jar, Path project, InspectionScope scope) {
        this(java, jar, project, scope, List.of());
    }

    public List<String> arguments() {
        List<String> arguments = new ArrayList<>(List.of(
                java.toString(),
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-jar",
                jar.toString(),
                "check",
                "--format",
                "json",
                "--project",
                project.toString()));
        // Last, because --files takes every argument after it.
        arguments.add(scope.option());
        if (scope == InspectionScope.FILES) {
            files.forEach(file -> arguments.add(file.toString()));
        }
        return List.copyOf(arguments);
    }
}
