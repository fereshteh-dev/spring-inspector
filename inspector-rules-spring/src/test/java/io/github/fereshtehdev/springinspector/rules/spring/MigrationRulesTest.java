package io.github.fereshtehdev.springinspector.rules.spring;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MigrationRulesTest {

    private static String fixture(String rule, String path) {
        return resource("fixtures/" + rule + "/" + path);
    }

    @Nested
    class JavaxNamespace {

        private static final String RULE = "migration.javax-namespace";
        private final JavaxNamespaceCheck check = new JavaxNamespaceCheck();

        @Test
        void reportsMovedJakartaPackagesAndRanksIgnoredLifecycleCallbacksHigher() {
            String bad = fixture(RULE, "bad/CacheWarmer.java");

            List<Finding> findings =
                    CheckTester.of(check).springBootVersion("3.5.0").source(bad).findings();

            assertThat(check.id().value()).isEqualTo(RULE);
            assertThat(check.category()).isEqualTo(Category.MIGRATION);
            assertThat(findings)
                    .extracting(finding -> finding.location().lines().start(), Finding::severity)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    lineOf(bad, "javax.annotation.PostConstruct"), Severity.MAJOR),
                            org.assertj.core.groups.Tuple.tuple(lineOf(bad, "javax.persistence"), Severity.MINOR),
                            org.assertj.core.groups.Tuple.tuple(lineOf(bad, "javax.servlet"), Severity.MINOR));
        }

        @Test
        void rewritesTheImportToJakarta() {
            CheckTester tester =
                    CheckTester.of(check).springBootVersion("3.5.0").source(fixture(RULE, "bad/CacheWarmer.java"));

            assertThat(tester.fixed(tester.findings().getFirst())).contains("import jakarta.annotation.PostConstruct;");
        }

        @Test
        void appliesFromSpringBoot3AndKeepsPackagesThatDidNotMove() {
            assertThat(CheckTester.of(check).springBootVersion("2.7.18").applies())
                    .isFalse();
            assertThat(CheckTester.of(check)
                            .springBootVersion("4.1.1")
                            .source(fixture(RULE, "good/CacheWarmer.java"))
                            .findings())
                    .isEmpty();
            assertThat(CheckTester.of(check)
                            .springBootVersion("3.0.0")
                            .source("package com.acme;\n\nimport javax.persistence.*;\n\nclass A {}\n")
                            .singleFinding()
                            .howToFix()
                            .patch())
                    .isPresent();
        }
    }

    @Nested
    class WebSecurityConfigurerAdapter {

        private static final String RULE = "migration.web-security-configurer-adapter";
        private final WebSecurityConfigurerAdapterCheck check = new WebSecurityConfigurerAdapterCheck();

        @Test
        void reportsConfigurationsExtendingTheRemovedAdapter() {
            String bad = fixture(RULE, "bad/SecurityConfig.java");

            Finding finding = CheckTester.of(check).source(bad).singleFinding();

            assertThat(check.id().value()).isEqualTo(RULE);
            assertThat(check.category()).isEqualTo(Category.MIGRATION);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "class SecurityConfig"));
        }

        @Test
        void acceptsSecurityFilterChainBeans() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/SecurityConfig.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class SpringBootSupport {

        private static final String RULE = "migration.spring-boot-support";
        private final SpringBootSupportCheck check =
                new SpringBootSupportCheck(Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC));

        @Test
        void reportsALineMostLikelyPastOpenSourceSupportOnTheBuildFile() {
            String bad = fixture(RULE, "bad/pom.xml");

            Finding finding = CheckTester.of(check)
                    .springBootVersion("3.3.5")
                    .file("pom.xml", bad)
                    .singleFinding();

            assertThat(check.id().value()).isEqualTo(RULE);
            assertThat(check.category()).isEqualTo(Category.MIGRATION);
            assertThat(finding.severity()).isEqualTo(Severity.INFO);
            assertThat(finding.confidence()).isEqualTo(Confidence.MEDIUM);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "<version>3.3.5"));
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("Spring Boot 3.3 (released 2024-05-23) is most likely out of open-source support");
        }

        @Test
        void acceptsSupportedAndUnknownLinesAndProjectsWithoutBoot() {
            String good = fixture(RULE, "good/pom.xml");

            assertThat(CheckTester.of(check)
                            .springBootVersion("4.1.1")
                            .file("pom.xml", good)
                            .findings())
                    .isEmpty();
            assertThat(CheckTester.of(check)
                            .springBootVersion("9.0.0")
                            .file("pom.xml", good)
                            .findings())
                    .isEmpty();
            assertThat(CheckTester.of(check).file("pom.xml", good).findings()).isEmpty();
        }

        @Test
        void staysQuietWhenTheBuildFileIsNotPartOfTheChange() {
            assertThat(CheckTester.of(check)
                            .springBootVersion("3.3.5")
                            .file("src/main/java/A.java", "class A {}")
                            .findings())
                    .isEmpty();
        }

        @Test
        void allowsThirteenMonthsAfterTheFirstRelease() {
            SpringBootSupportCheck early =
                    new SpringBootSupportCheck(Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC));

            assertThat(CheckTester.of(early)
                            .springBootVersion("4.0.1")
                            .file("build.gradle", "plugins { id 'org.springframework.boot' version '4.0.1' }\n")
                            .findings())
                    .isEmpty();
            assertThat(new SpringBootSupportCheck().id().value()).isEqualTo(RULE);
        }
    }
}
