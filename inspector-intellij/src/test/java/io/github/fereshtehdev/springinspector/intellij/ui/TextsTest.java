package io.github.fereshtehdev.springinspector.intellij.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.intellij.report.Report;
import io.github.fereshtehdev.springinspector.intellij.report.Report.CategoryReport;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Reference;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Severity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TextsTest {

    static Finding finding(Severity severity, String snippet, Optional<String> patch, List<Reference> references) {
        return new Finding(
                "security.sql-concatenation",
                severity,
                "HIGH",
                "RULE",
                "src/main/java/Orders.java",
                12,
                12,
                "SQL built from <input> is executed",
                snippet,
                "Attackers can change the query.\n- CVE-1 (critical): first\n- CVE-2: second\nMore text.",
                references,
                "Use a PreparedStatement.",
                patch);
    }

    @Test
    void showsTheThreePartsThePatchAndWebLinks() {
        String html = FindingHtml.of(finding(
                Severity.BLOCKER,
                "query(\"SELECT \" + id)",
                Optional.of("--- a/Orders.java\n+++ b/Orders.java"),
                List.of(new Reference("OWASP", "https://owasp.org/sql"), new Reference("Local", "file:///etc/passwd"))));

        assertThat(html)
                .contains("<b>BLOCKER</b> &middot; security.sql-concatenation &middot; confidence high")
                .contains("src/main/java/Orders.java:12")
                .contains("<h3>What's wrong</h3><p>SQL built from &lt;input&gt; is executed</p>")
                .contains("<pre>query(&quot;SELECT &quot; + id)</pre>")
                .contains("<p>Attackers can change the query.</p><ul><li>CVE-1 (critical): first</li>"
                        + "<li>CVE-2: second</li></ul><p>More text.</p>")
                .contains("<a href=\"https://owasp.org/sql\">OWASP</a>")
                .contains("Local: file:///etc/passwd")
                .doesNotContain("href=\"file:")
                .contains("<h3>How to fix</h3><p>Use a PreparedStatement.</p>")
                .contains("<h3>Patch</h3><pre>--- a/Orders.java\n+++ b/Orders.java</pre>");
    }

    @Test
    void escapesMarkupFromTheInspectedCode() {
        String html = FindingHtml.of(
                finding(Severity.INFO, "<script>alert('x')</script> & <img src=x>", Optional.empty(), List.of()));

        assertThat(html)
                .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &lt;img src=x&gt;")
                .doesNotContain("<script>")
                .doesNotContain("<h3>Patch</h3>");
        assertThat(FindingHtml.message("a\n- b")).isEqualTo("<html><body><p>a</p><ul><li>b</li></ul></body></html>");
    }

    @Test
    void summarisesTheRunAndEachCategory() {
        CategoryReport security = new CategoryReport(
                "security",
                "FAILED",
                List.of(
                        finding(Severity.BLOCKER, "", Optional.empty(), List.of()),
                        finding(Severity.BLOCKER, "", Optional.empty(), List.of()),
                        finding(Severity.INFO, "", Optional.empty(), List.of())),
                2);
        CategoryReport clean = new CategoryReport("clean-code", "PASSED", List.of(), 0);
        Report report = new Report(
                60,
                true,
                List.of(),
                List.of(security, clean),
                List.of("spring.slow was stopped"),
                Optional.empty(),
                Optional.of("Advisories are old"));

        assertThat(Summaries.headline(report)).isEqualTo("Score 60/100 · gate blocked · 3 findings");
        assertThat(Summaries.counts(security)).isEqualTo("2 blocker, 1 info (2 more not shown)");
        assertThat(Summaries.counts(clean)).isEqualTo("no findings");
        assertThat(Summaries.notes(report, List.of("unknown setting")))
                .containsExactly("Advisories are old", "spring.slow was stopped", "unknown setting");
        Report passing = new Report(
                100, false, List.of(), List.of(clean), List.of(), Optional.empty(), Optional.empty());
        Report single = new Report(
                90,
                false,
                List.of(),
                List.of(new CategoryReport(
                        "security", "HINTS", List.of(finding(Severity.MINOR, "", Optional.empty(), List.of())), 0)),
                List.of(),
                Optional.empty(),
                Optional.empty());
        assertThat(Summaries.headline(passing)).isEqualTo("Score 100/100 · gate passed · no findings");
        assertThat(Summaries.headline(single)).endsWith("1 finding");
    }
}
