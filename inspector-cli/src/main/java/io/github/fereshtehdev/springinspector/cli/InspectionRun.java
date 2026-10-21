package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.core.domain.project.DetectedProject;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import java.nio.file.Path;
import java.util.Map;

/**
 * @param unparseableFiles changed files the parser could not read, with the reason; their findings are missing
 */
record InspectionRun(DetectedProject project, ReportCard report, int changedFiles, Map<Path, String> unparseableFiles) {

    InspectionRun {
        unparseableFiles = Map.copyOf(unparseableFiles);
    }
}
