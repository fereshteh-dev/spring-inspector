package io.github.fereshtehdev.springinspector.document;

import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedVersions;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport;
import java.util.Optional;

/**
 * Everything known about a project that its document is written from.
 *
 * @param name the project's name: its application name, or its folder
 * @param modules how many build modules it has
 * @param quality the result of inspecting every file, or empty when the inspection was skipped
 */
public record ProjectFacts(
        String name,
        DetectedVersions versions,
        BuildTool buildTool,
        int modules,
        ArchitectureReport architecture,
        SystemDesign design,
        Optional<ReportCard> quality) {}
