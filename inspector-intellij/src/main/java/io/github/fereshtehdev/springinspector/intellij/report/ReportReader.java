package io.github.fereshtehdev.springinspector.intellij.report;

import io.github.fereshtehdev.springinspector.intellij.report.Report.CategoryReport;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Reference;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Severity;
import java.util.List;
import java.util.Locale;

/** Turns the command line's JSON report into a {@link Report}. */
public final class ReportReader {

    private ReportReader() {}

    /** Reads a report; throws {@link IllegalArgumentException} when the text is not one. */
    public static Report read(String json) {
        JsonTree root = JsonTree.parse(json);
        if (!root.get("tool").get("name").text().equals("spring-inspector")) {
            throw new IllegalArgumentException("The output is not a Spring Inspector report");
        }
        JsonTree advisories = root.get("advisories");
        return new Report(
                root.get("score").number(),
                root.get("gate").get("blocks").bool(),
                root.get("gate").get("reasons").items().stream()
                        .map(JsonTree::text)
                        .toList(),
                root.get("categories").items().stream()
                        .map(ReportReader::category)
                        .toList(),
                root.get("problems").items().stream()
                        .map(ReportReader::problem)
                        .toList(),
                advisories.get("updatedAt").optionalText(),
                advisories.get("warning").optionalText());
    }

    private static CategoryReport category(JsonTree category) {
        return new CategoryReport(
                category.get("category").text(),
                category.get("status").text(),
                category.get("findings").items().stream()
                        .map(ReportReader::finding)
                        .toList(),
                category.get("hiddenFindings").number());
    }

    private static Finding finding(JsonTree finding) {
        List<Reference> references = finding.get("references").items().stream()
                .map(reference -> new Reference(
                        reference.get("label").text(), reference.get("url").text()))
                .toList();
        return new Finding(
                finding.get("ruleId").text(),
                severity(finding.get("severity").text()),
                finding.get("confidence").text(),
                finding.get("source").text(),
                finding.get("file").text(),
                Math.max(1, finding.get("startLine").number()),
                Math.max(1, finding.get("endLine").number()),
                finding.get("whatsWrong").text(),
                finding.get("snippet").text(),
                finding.get("whyItMatters").text(),
                references,
                finding.get("howToFix").text(),
                finding.get("patch").optionalText());
    }

    private static String problem(JsonTree problem) {
        String rule = problem.get("ruleId").text();
        return switch (problem.get("kind").text()) {
            case "timedOut" -> rule + " was stopped after " + problem.get("timeoutMillis").number() / 1000
                    + " s and was skipped";
            case "failed" -> rule + " failed and was skipped: " + problem.get("reason").text();
            default -> rule + " did not finish";
        };
    }

    private static Severity severity(String text) {
        try {
            return Severity.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return Severity.INFO;
        }
    }
}
