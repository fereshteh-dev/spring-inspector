package io.github.fereshtehdev.springinspector.intellij.run;

import java.nio.file.Path;
import java.util.List;

/**
 * The command line the plugin runs: the bundled {@code spring-inspector.jar} on a Java 25 runtime, writing a JSON
 * report. It runs in its own process, so the inspection can neither slow down nor crash the IDE. Its output is UTF-8
 * whatever the platform's default, so summaries and file names arrive intact.
 */
public record InspectorCommand(Path java, Path jar, Path project, InspectionScope scope) {

    public List<String> arguments() {
        return List.of(
                java.toString(),
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-jar",
                jar.toString(),
                "check",
                scope.option(),
                "--format",
                "json",
                "--project",
                project.toString());
    }
}
