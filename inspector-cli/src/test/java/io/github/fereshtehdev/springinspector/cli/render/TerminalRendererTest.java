package io.github.fereshtehdev.springinspector.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TerminalRendererTest {

    private static String render(ReportCard card, Style style, Optional<Category> details) throws IOException {
        StringBuilder out = new StringBuilder();
        new TerminalRenderer(style, details).render(card, out);
        return out.toString();
    }

    @Test
    void showsTheCardFindingsHiddenCountsProblemsAndGate() throws IOException {
        String out = render(SampleReports.blocked(), Style.PLAIN, Optional.empty());

        assertThat(out)
                .startsWith("Spring Inspector  score 55/100\n")
                .contains("  [FAIL] Security     1 major\n")
                .contains("      MAJOR   src/main/java/A.java:7  MD5 is a broken hash function  [fix available]\n")
                .contains("  [HINT] Clean code   1 minor\n")
                .contains("      INFO    pom.xml:1  Spring Boot 3.5 leaves OSS support soon\n")
                .contains("  [ OK ] Spring Boot  no findings\n")
                .contains("  ... 2 more in Clean code\n")
                .contains("  ! spring.broken failed and was skipped: IllegalStateException: boom\n")
                .contains("  ! spring.slow was stopped after 5 s and was skipped\n")
                .contains("  Gate blocked\n     - score 55 is below 70\n")
                .contains("--details security");
    }

    @Test
    void detailsShowTheThreePartsAndThePatch() throws IOException {
        String out = render(SampleReports.blocked(), Style.PLAIN, Optional.of(Category.SECURITY));

        assertThat(out)
                .contains("Security in detail")
                .contains("-- security.weak-cryptography - MAJOR - confidence high")
                .contains("    | MessageDigest.getInstance(\"MD5\")")
                .contains("    -> CWE-327: https://cwe.mitre.org/data/definitions/327.html")
                .contains("    How to fix\n    Use SHA-256.\n")
                .contains("    +\"SHA-256\"")
                .doesNotContain("--details security");
    }

    @Test
    void detailsOfACategoryWithoutFindingsOrResultsSaySo() throws IOException {
        assertThat(render(SampleReports.blocked(), Style.PLAIN, Optional.of(Category.SPRING_BOOT)))
                .contains("Spring Boot in detail\n  Nothing to report.");
        assertThat(render(SampleReports.blocked(), Style.PLAIN, Optional.of(Category.SPRING_AI)))
                .contains("No Spring AI checks ran on these changes.");
    }

    @Test
    void aCleanRunPassesTheGate() throws IOException {
        String out = render(SampleReports.clean(), Style.PLAIN, Optional.empty());

        assertThat(out).contains("Gate passed").doesNotContain("--details");
    }

    @Test
    void saysWhenNoCheckApplied() throws IOException {
        assertThat(render(SampleReports.empty(), Style.PLAIN, Optional.empty()))
                .contains("No checks applied to the changed files.");
    }

    @Test
    void usesColourAndSymbolsWhenTheTerminalSupportsThem() throws IOException {
        String out = render(SampleReports.blocked(), new Style(true, true), Optional.of(Category.SECURITY));

        assertThat(out)
                .contains("\u001B[")
                .contains("❌")
                .contains("⛔")
                .contains("│ ")
                .contains("↳");
        assertThat(render(SampleReports.clean(), new Style(true, true), Optional.empty()))
                .contains("✅")
                .contains("🟢");
    }

    @Test
    void detectsWhatTheTerminalCanShow() {
        assertThat(Style.detect(Map.of(), true, "UTF-8")).isEqualTo(new Style(true, true));
        assertThat(Style.detect(Map.of(), false, "UTF-8")).isEqualTo(new Style(false, true));
        assertThat(Style.detect(Map.of("NO_COLOR", ""), true, "windows-1252")).isEqualTo(Style.PLAIN);
        assertThat(Style.detect(Map.of("TERM", "dumb"), true, "utf-8").color()).isFalse();
    }

    @Test
    void readsCategoryNamesTheWayPeopleTypeThem() {
        assertThat(Categories.parse("spring-boot")).contains(Category.SPRING_BOOT);
        assertThat(Categories.parse(" SPRING_AI ")).contains(Category.SPRING_AI);
        assertThat(Categories.parse("springboot")).isEmpty();
        assertThat(Categories.option(Category.CLEAN_CODE)).isEqualTo("clean-code");
        assertThat(Categories.title(Category.ARCHITECTURE)).isEqualTo("Architecture");
    }
}
