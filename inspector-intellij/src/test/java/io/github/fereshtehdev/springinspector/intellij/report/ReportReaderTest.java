package io.github.fereshtehdev.springinspector.intellij.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ReportReaderTest {

    @Test
    void readsTheReportTheCommandLineWrites() throws IOException {
        Report report = ReportReader.read(resource("/vulnerable-project-report.json"));

        assertThat(report.score()).isEqualTo(60);
        assertThat(report.blocks()).isTrue();
        assertThat(report.gateReasons()).hasSize(2);
        assertThat(report.advisoriesUpdatedAt()).isPresent();
        assertThat(report.advisoriesWarning()).isEmpty();
        assertThat(report.categories()).extracting(Report.CategoryReport::title).contains("Security", "Clean code");
        assertThat(report.findingCount()).isEqualTo(3);
        Finding first = report.categories().getFirst().findings().getFirst();
        assertThat(first.ruleId()).isEqualTo("security.vulnerable-dependency");
        assertThat(first.severity()).isEqualTo(Severity.BLOCKER);
        assertThat(first.location()).isEqualTo("pom.xml:17");
        assertThat(first.whyItMatters()).contains("\n- CVE-2022-22965 (critical)");
        assertThat(first.references())
                .first()
                .satisfies(reference ->
                        assertThat(reference.url()).isEqualTo("https://osv.dev/vulnerability/GHSA-36p3-wjmg-h94x"));
        assertThat(first.patch()).isEmpty();
    }

    @Test
    void readsPatchesProblemsAndWarnings() {
        Report report = ReportReader.read("""
                {
                  "tool": {"name": "spring-inspector", "version": "1.0"},
                  "score": 95,
                  "gate": {"blocks": false, "reasons": []},
                  "advisories": {"updatedAt": null, "stale": true, "warning": "No vulnerability advisories"},
                  "categories": [{
                    "category": "clean-code", "status": "HINTS", "hiddenFindings": 4,
                    "findings": [{
                      "ruleId": "cleancode.return-null", "severity": "unknown", "confidence": "MEDIUM",
                      "source": "RULE", "file": "src/A.java", "startLine": 0, "endLine": 3,
                      "whatsWrong": "Returns null", "snippet": "return null;", "whyItMatters": "Callers crash",
                      "references": [], "howToFix": "Return Optional", "patch": "--- a/src/A.java\\n+++ b/src/A.java"
                    }]
                  }],
                  "problems": [
                    {"ruleId": "spring.slow", "kind": "timedOut", "timeoutMillis": 5000},
                    {"ruleId": "spring.broken", "kind": "failed", "reason": "boom"},
                    {"ruleId": "spring.odd"}
                  ]
                }
                """);

        assertThat(report.blocks()).isFalse();
        assertThat(report.advisoriesUpdatedAt()).isEmpty();
        assertThat(report.advisoriesWarning()).contains("No vulnerability advisories");
        assertThat(report.categories().getFirst().hiddenFindings()).isEqualTo(4);
        Finding finding = report.categories().getFirst().findings().getFirst();
        assertThat(finding.severity()).isEqualTo(Severity.INFO);
        assertThat(finding.startLine()).isEqualTo(1);
        assertThat(finding.patch()).contains("--- a/src/A.java\n+++ b/src/A.java");
        assertThat(report.problems())
                .containsExactly(
                        "spring.slow was stopped after 5 s and was skipped",
                        "spring.broken failed and was skipped: boom",
                        "spring.odd did not finish");
    }

    @Test
    void rejectsOutputThatIsNotAReport() {
        assertThatThrownBy(() -> ReportReader.read("")).hasMessageContaining("empty");
        assertThatThrownBy(() -> ReportReader.read("error: no such option")).hasMessageContaining("not valid JSON");
        assertThatThrownBy(() -> ReportReader.read("{\"categories\": [")).hasMessageContaining("not valid JSON");
        assertThatThrownBy(() -> ReportReader.read("{\"tool\": {\"name\": \"other\"}}"))
                .hasMessageContaining("not a Spring Inspector report");
        assertThatThrownBy(() -> ReportReader.read("[".repeat(100) + "]".repeat(100)))
                .hasMessageContaining("too deeply");
    }

    @Test
    void namesCategoriesForPeople() {
        assertThat(java.util.stream.Stream.of(
                                "security", "spring-boot", "spring-ai", "clean-code", "architecture", "migration", "new")
                        .map(id -> new Report.CategoryReport(id, "PASSED", java.util.List.of(), 0).title()))
                .containsExactly(
                        "Security", "Spring Boot", "Spring AI", "Clean code", "Architecture", "Migration", "new");
    }

    static String resource(String name) throws IOException {
        try (InputStream in = Objects.requireNonNull(ReportReaderTest.class.getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
