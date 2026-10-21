package io.github.fereshtehdev.springinspector.cli.render;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhatsWrong;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.report.CategoryResult;
import io.github.fereshtehdev.springinspector.core.domain.report.CategoryStatus;
import io.github.fereshtehdev.springinspector.core.domain.report.CheckProblem;
import io.github.fereshtehdev.springinspector.core.domain.report.GateDecision;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.domain.report.Score;
import io.github.fereshtehdev.springinspector.core.domain.report.SeverityCounts;
import java.net.URI;
import java.time.Duration;
import java.util.List;

final class SampleReports {

    static final Finding WEAK_HASH = new Finding(
            RuleId.of("security.weak-cryptography"),
            Category.SECURITY,
            Severity.MAJOR,
            Location.of("src/main/java/A.java", LineRange.line(7)),
            new WhatsWrong("MD5 is a broken hash function", "MessageDigest.getInstance(\"MD5\")"),
            new WhyItMatters(
                    "Collisions are practical.",
                    List.of(new Reference("CWE-327", URI.create("https://cwe.mitre.org/data/definitions/327.html")))),
            HowToFix.withPatch(
                    "Use SHA-256.",
                    new Patch(
                            "--- a/src/main/java/A.java\n+++ b/src/main/java/A.java\n@@ -7 +7 @@\n-\"MD5\"\n+\"SHA-256\"\n")),
            Confidence.HIGH,
            FindingSource.RULE);

    static final Finding LONG_METHOD = new Finding(
            RuleId.of("cleancode.long-method"),
            Category.CLEAN_CODE,
            Severity.MINOR,
            Location.of("src/main/java/B.java", LineRange.of(3, 90)),
            new WhatsWrong("run has 80 lines of code; the limit is 60", "void run() {"),
            new WhyItMatters("Hard to read.", List.of()),
            HowToFix.withoutPatch("Extract methods."),
            Confidence.HIGH,
            FindingSource.RULE);

    static final Finding NOTE = new Finding(
            RuleId.of("migration.note"),
            Category.MIGRATION,
            Severity.INFO,
            Location.of("pom.xml", LineRange.line(1)),
            new WhatsWrong("Spring Boot 3.5 leaves OSS support soon", ""),
            new WhyItMatters("No more fixes.", List.of()),
            HowToFix.withoutPatch("Plan the upgrade."),
            Confidence.MEDIUM,
            FindingSource.RULE);

    private SampleReports() {}

    static ReportCard blocked() {
        return new ReportCard(
                List.of(
                        new CategoryResult(
                                Category.SECURITY,
                                CategoryStatus.FAILED,
                                SeverityCounts.of(List.of(WEAK_HASH)),
                                List.of(WEAK_HASH),
                                0),
                        new CategoryResult(
                                Category.CLEAN_CODE,
                                CategoryStatus.HINTS,
                                SeverityCounts.of(List.of(LONG_METHOD)),
                                List.of(LONG_METHOD),
                                2),
                        new CategoryResult(
                                Category.MIGRATION,
                                CategoryStatus.HINTS,
                                SeverityCounts.of(List.of(NOTE)),
                                List.of(NOTE),
                                0),
                        new CategoryResult(
                                Category.SPRING_BOOT,
                                CategoryStatus.PASSED,
                                SeverityCounts.of(List.of()),
                                List.of(),
                                0)),
                new Score(55),
                new GateDecision.Block(List.of("score 55 is below 70")),
                List.of(
                        new CheckProblem.Failed(RuleId.of("spring.broken"), "IllegalStateException: boom"),
                        new CheckProblem.TimedOut(RuleId.of("spring.slow"), Duration.ofSeconds(5))));
    }

    static ReportCard clean() {
        return new ReportCard(
                List.of(new CategoryResult(
                        Category.SECURITY, CategoryStatus.PASSED, SeverityCounts.of(List.of()), List.of(), 0)),
                new Score(95),
                GateDecision.pass(),
                List.of());
    }

    static ReportCard empty() {
        return new ReportCard(List.of(), Score.PERFECT, GateDecision.pass(), List.of());
    }
}
