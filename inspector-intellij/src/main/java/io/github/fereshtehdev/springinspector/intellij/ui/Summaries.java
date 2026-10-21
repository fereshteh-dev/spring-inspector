package io.github.fereshtehdev.springinspector.intellij.ui;

import io.github.fereshtehdev.springinspector.intellij.report.Report;
import io.github.fereshtehdev.springinspector.intellij.report.Report.CategoryReport;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** The short texts the tool window shows above and inside the findings tree. */
final class Summaries {

    private Summaries() {}

    /** For example: "Score 60/100 · gate blocked · 3 findings". */
    static String headline(Report report) {
        int findings = report.findingCount();
        return "Score " + report.score() + "/100 · gate " + (report.blocks() ? "blocked" : "passed") + " · "
                + (findings == 0 ? "no findings" : findings + (findings == 1 ? " finding" : " findings"));
    }

    /** What the user should know about how the run went: advisories, skipped checks, warnings. */
    static List<String> notes(Report report, List<String> warnings) {
        List<String> notes = new ArrayList<>();
        report.advisoriesWarning().ifPresent(notes::add);
        notes.addAll(report.problems());
        notes.addAll(warnings);
        return notes;
    }

    /** For example: "2 blocker, 1 info", or "no findings". */
    static String counts(CategoryReport category) {
        Map<Severity, Long> counts = category.findings().stream()
                .collect(Collectors.groupingBy(Finding::severity, Collectors.counting()));
        List<String> parts = new ArrayList<>();
        for (Severity severity : Severity.values()) {
            Long count = counts.get(severity);
            if (count != null) {
                parts.add(count + " " + severity.name().toLowerCase(Locale.ROOT));
            }
        }
        String text = parts.isEmpty() ? "no findings" : String.join(", ", parts);
        return category.hiddenFindings() > 0 ? text + " (" + category.hiddenFindings() + " more not shown)" : text;
    }
}
