package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.core.domain.project.DetectedProject;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What one inspection found, with what the user should know about how it ran.
 *
 * @param unparseableFiles changed files the parser could not read, with the reason; their findings are missing
 * @param configFile the configuration file used, or empty for the defaults
 * @param warnings problems in the configuration that did not stop the run
 */
record InspectionRun(
        DetectedProject project,
        ReportCard report,
        int changedFiles,
        Map<Path, String> unparseableFiles,
        Optional<Path> configFile,
        List<String> warnings) {

    InspectionRun {
        unparseableFiles = Map.copyOf(unparseableFiles);
        warnings = List.copyOf(warnings);
    }
}
