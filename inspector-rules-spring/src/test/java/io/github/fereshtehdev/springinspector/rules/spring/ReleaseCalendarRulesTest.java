package io.github.fereshtehdev.springinspector.rules.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar.ReleaseLine;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReleaseCalendarRulesTest {

    static final Clock TODAY = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);
    static final ReleaseCalendar CALENDAR = new ReleaseCalendar(
            Map.of(
                    ReleaseCalendar.SPRING_BOOT,
                    List.of(
                            line("4.1", "2026-06-30", "2027-07-31", "4.1.1"),
                            line("4.0", "2025-11-30", "2026-12-31", "4.0.8"),
                            line("3.5", "2025-05-31", "2026-06-30", "3.5.16")),
                    ReleaseCalendar.SPRING_AI,
                    List.of(new ReleaseLine("2.0", Optional.empty(), Optional.empty(), Optional.of("2.0.1"))),
                    ReleaseCalendar.JAVA,
                    List.of(
                            line("27", "2026-09-22", "2027-03-23", "27+35"),
                            line("25", "2025-09-22", "2031-09-30", "25.0.4.1+1"),
                            line("24", "2025-03-20", "2025-09-22", "24.0.2+12"),
                            line("21", "2023-09-19", "2029-12-31", "21.0.8+9"))),
            Instant.parse("2026-10-22T00:00:00Z"));

    static final String POM = """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>%s</version>
                </parent>
                <properties>
                    <java.version>%s</java.version>
                    <spring-ai.version>2.0.0</spring-ai.version>
                </properties>
            </project>
            """;

    @Nested
    class SpringBootSupport {

        final SpringBootSupportCheck check = new SpringBootSupportCheck(TODAY, () -> Optional.of(CALENDAR));

        @Test
        void usesThePublishedEndOfSupport() {
            Finding finding = CheckTester.of(check)
                    .springBootVersion("3.5.16")
                    .file("pom.xml", POM.formatted("3.5.16", "21"))
                    .singleFinding();

            assertThat(finding.whatsWrong().description())
                    .isEqualTo("Spring Boot 3.5 left open-source support on 2026-06-30");
            assertThat(finding.confidence()).isEqualTo(Confidence.HIGH);
            assertThat(finding.location().lines().start()).isEqualTo(5);
        }

        @Test
        void warnsThreeMonthsAhead() {
            assertThat(CheckTester.of(check)
                            .springBootVersion("4.0.8")
                            .file("pom.xml", POM.formatted("4.0.8", "21"))
                            .singleFinding()
                            .whatsWrong()
                            .description())
                    .isEqualTo("Spring Boot 4.0 leaves open-source support on 2026-12-31, in 70 days");
            assertThat(CheckTester.of(check)
                            .springBootVersion("4.1.1")
                            .file("pom.xml", POM.formatted("4.1.1", "21"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void estimatesLinesTheCalendarDoesNotList() {
            Finding finding = CheckTester.of(check)
                    .springBootVersion("3.3.5")
                    .file("pom.xml", POM.formatted("3.3.5", "21"))
                    .singleFinding();

            assertThat(finding.confidence()).isEqualTo(Confidence.MEDIUM);
            assertThat(finding.whatsWrong().description()).contains("most likely out of open-source support");
        }
    }

    @Nested
    class NewerPatch {

        final NewerPatchCheck check = new NewerPatchCheck(() -> Optional.of(CALENDAR));

        @Test
        void namesTheNewestPatchOfSpringBootAndSpringAi() {
            List<Finding> findings = CheckTester.of(check)
                    .springBootVersion("4.0.1")
                    .springAiVersion("2.0.0")
                    .file("pom.xml", POM.formatted("4.0.1", "25"))
                    .findings();

            assertThat(findings)
                    .extracting(finding -> finding.whatsWrong().description())
                    .containsExactly(
                            "Spring Boot 4.0.1 is used; 4.0.8 is the newest 4.0 release",
                            "Spring AI 2.0.0 is used; 2.0.1 is the newest 2.0 release");
            assertThat(findings.getFirst().howToFix().explanation()).startsWith("Change the version to 4.0.8");
            assertThat(findings.getFirst().category()).isEqualTo(Category.MIGRATION);
            assertThat(check.id().value()).isEqualTo("migration.newer-patch");
        }

        @Test
        void staysQuietWhenCurrentUnknownOrWithoutCalendar() {
            assertThat(CheckTester.of(check)
                            .springBootVersion("4.1.1")
                            .file("pom.xml", POM.formatted("4.1.1", "25"))
                            .findings())
                    .isEmpty();
            assertThat(CheckTester.of(check)
                            .springBootVersion("9.9.0")
                            .file("pom.xml", POM.formatted("9.9.0", "25"))
                            .findings())
                    .isEmpty();
            assertThat(CheckTester.of(new NewerPatchCheck())
                            .springBootVersion("4.0.1")
                            .file("pom.xml", POM.formatted("4.0.1", "25"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class JavaSupport {

        final JavaSupportCheck check = new JavaSupportCheck(TODAY, () -> Optional.of(CALENDAR));

        @Test
        void flagsAJavaLineWithoutFreeUpdatesAndSuggestsTheLongestSupported() {
            Finding finding = CheckTester.of(check)
                    .javaVersion("24")
                    .file("pom.xml", POM.formatted("4.1.1", "24"))
                    .singleFinding();

            assertThat(finding.whatsWrong().description())
                    .isEqualTo("Java 24 no longer gets free updates (they ended on 2025-09-22)");
            assertThat(finding.location().lines().start()).isEqualTo(8);
            assertThat(finding.howToFix().explanation()).isEqualTo("Move to Java 25, which is supported for longest.");
            assertThat(check.id().value()).isEqualTo("migration.java-support");
        }

        @Test
        void acceptsSupportedLinesAndStaysQuietWithoutData() {
            assertThat(CheckTester.of(check)
                            .javaVersion("21")
                            .file("pom.xml", POM.formatted("4.1.1", "21"))
                            .findings())
                    .isEmpty();
            assertThat(CheckTester.of(new JavaSupportCheck())
                            .javaVersion("24")
                            .file("pom.xml", POM.formatted("4.1.1", "24"))
                            .findings())
                    .isEmpty();
            assertThat(CheckTester.of(check)
                            .javaVersion("24")
                            .file("src/main/java/A.java", "class A {}")
                            .findings())
                    .isEmpty();
        }
    }

    @Test
    void calendarFindsLinesByProduct() {
        assertThat(CALENDAR.line(ReleaseCalendar.SPRING_BOOT, "4.1"))
                .hasValueSatisfying(line -> assertThat(line.latest()).contains("4.1.1"));
        assertThat(CALENDAR.line(ReleaseCalendar.SPRING_BOOT, "9.9")).isEmpty();
        assertThat(CALENDAR.line("kotlin", "2.0")).isEmpty();
    }

    static ReleaseLine line(String line, String released, String supportEnds, String latest) {
        return new ReleaseLine(
                line,
                Optional.of(LocalDate.parse(released)),
                Optional.of(LocalDate.parse(supportEnds)),
                Optional.of(latest));
    }
}
