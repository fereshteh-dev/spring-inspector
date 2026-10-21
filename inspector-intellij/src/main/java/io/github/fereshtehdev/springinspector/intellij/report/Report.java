package io.github.fereshtehdev.springinspector.intellij.report;

import java.util.List;
import java.util.Optional;

/**
 * One run's report card, as the command line's {@code --format json} describes it.
 *
 * @param gateReasons why the gate blocks; empty when it passes
 * @param problems checks that failed or timed out, so their findings are missing
 * @param advisoriesWarning set when the vulnerability advisories are missing or out of date
 */
public record Report(
        int score,
        boolean blocks,
        List<String> gateReasons,
        List<CategoryReport> categories,
        List<String> problems,
        Optional<String> advisoriesUpdatedAt,
        Optional<String> advisoriesWarning) {

    public Report {
        gateReasons = List.copyOf(gateReasons);
        categories = List.copyOf(categories);
        problems = List.copyOf(problems);
    }

    public int findingCount() {
        return categories.stream().mapToInt(category -> category.findings().size()).sum();
    }

    /**
     * The findings of one category.
     *
     * @param id the category as the command line names it, such as {@code security} or {@code clean-code}
     * @param status {@code PASSED}, {@code HINTS} or {@code FAILED}
     * @param hiddenFindings findings left out because the category reached its configured maximum
     */
    public record CategoryReport(String id, String status, List<Finding> findings, int hiddenFindings) {

        public CategoryReport {
            findings = List.copyOf(findings);
        }

        public String title() {
            return switch (id) {
                case "security" -> "Security";
                case "spring-boot" -> "Spring Boot";
                case "spring-ai" -> "Spring AI";
                case "clean-code" -> "Clean code";
                case "architecture" -> "Architecture";
                case "migration" -> "Migration";
                default -> id;
            };
        }
    }

    /**
     * One finding with its three parts: what is wrong, why it matters, how to fix it.
     *
     * @param file relative to the project root, with {@code /} separators
     * @param patch a unified diff that fixes it, when the rule can write one
     */
    public record Finding(
            String ruleId,
            Severity severity,
            String confidence,
            String source,
            String file,
            int startLine,
            int endLine,
            String whatsWrong,
            String snippet,
            String whyItMatters,
            List<Reference> references,
            String howToFix,
            Optional<String> patch) {

        public Finding {
            references = List.copyOf(references);
        }

        public String location() {
            return file + ":" + startLine;
        }
    }

    public record Reference(String label, String url) {}

    public enum Severity {
        BLOCKER,
        MAJOR,
        MINOR,
        INFO
    }
}
