package io.github.fereshtehdev.springinspector.rules.security;

import static io.github.fereshtehdev.springinspector.rules.security.Fixtures.fixture;
import static io.github.fereshtehdev.springinspector.rules.security.Fixtures.lines;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpringSecurityConfigurationRulesTest {

    @Nested
    class PermitAllOnSensitivePaths {

        private static final String RULE = "security.permit-all-sensitive";
        private final PermitAllSensitivePathCheck check = new PermitAllSensitivePathCheck();

        @Test
        void reportsAdminPathsAndEveryActuatorEndpoint() {
            String bad = fixture(RULE, "bad/SecurityConfig.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id()).isEqualTo(PermitAllSensitivePathCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "\"/admin/**\").permitAll()"), lineOf(bad, "toAnyEndpoint()).permitAll()"));
            assertThat(findings)
                    .extracting(finding -> finding.whatsWrong().description())
                    .containsExactlyInAnyOrder(
                            "permitAll() opens /admin/** to anyone",
                            "permitAll() opens every actuator endpoint to anyone");
        }

        @Test
        void acceptsPublicPathsAndProtectedAdminPaths() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/SecurityConfig.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class CsrfDisabled {

        private static final String RULE = "security.csrf-disabled";
        private final CsrfDisabledCheck check = new CsrfDisabledCheck();

        @Test
        void reportsDisabledCsrfWithHigherConfidenceForFormLogin() {
            String bad = fixture(RULE, "bad/WebSecurity.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id()).isEqualTo(CsrfDisabledCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(findings)
                    .extracting(finding -> finding.location().lines().start(), Finding::confidence)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    lineOf(bad, "AbstractHttpConfigurer::disable"), Confidence.HIGH),
                            org.assertj.core.groups.Tuple.tuple(
                                    lineOf(bad, "http.csrf().disable()"), Confidence.MEDIUM));
        }

        @Test
        void acceptsStatelessApisAndEnabledCsrf() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/ApiSecurity.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class ActuatorExposure {

        private static final String RULE = "security.actuator-exposed";
        private static final String SETTINGS = "src/main/resources/application.yml";
        private final ActuatorExposureCheck check = new ActuatorExposureCheck();

        @Test
        void reportsEveryEndpointSensitiveOnesAndShutdownOutsideDevelopment() {
            String bad = fixture(RULE, "bad/application.yml");

            List<Finding> findings = CheckTester.of(check).file(SETTINGS, bad).findings();

            assertThat(check.id()).isEqualTo(ActuatorExposureCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings))
                    .containsExactly(
                            lineOf(bad, "include: \"*\""),
                            lineOf(bad, "enabled: true"),
                            lineOf(bad, "- env"),
                            lineOf(bad, "- heapdump"));
            assertThat(findings.getFirst().whatsWrong().description())
                    .isEqualTo("Settings expose every actuator endpoint over HTTP");
        }

        @Test
        void narrowsAWildcardToHealthAndInfo() {
            CheckTester tester = CheckTester.of(check)
                    .file("src/main/resources/application.properties", "management.endpoints.web.exposure.include=*\n");

            assertThat(tester.fixed(tester.singleFinding()))
                    .isEqualTo("management.endpoints.web.exposure.include=health,info\n");
        }

        @Test
        void disablesTheShutdownEndpoint() {
            CheckTester tester = CheckTester.of(check)
                    .file("src/main/resources/application.properties", "management.endpoint.shutdown.enabled=true\n");

            assertThat(tester.fixed(tester.singleFinding())).isEqualTo("management.endpoint.shutdown.enabled=false\n");
        }

        @Test
        void acceptsMonitoringEndpointsAndDevelopmentProfiles() {
            assertThat(CheckTester.of(check)
                            .file(
                                    "src/main/resources/application.properties",
                                    fixture(RULE, "good/application.properties"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class H2Console {

        private static final String RULE = "security.h2-console-enabled";
        private static final String SETTINGS = "src/main/resources/application.properties";
        private final H2ConsoleEnabledCheck check = new H2ConsoleEnabledCheck();

        @Test
        void reportsTheConsoleAndRemoteAccessToIt() {
            String bad = fixture(RULE, "bad/application.properties");

            List<Finding> findings = CheckTester.of(check).file(SETTINGS, bad).findings();

            assertThat(check.id()).isEqualTo(H2ConsoleEnabledCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(findings).extracting(Finding::severity).containsExactly(Severity.MAJOR, Severity.BLOCKER);
            assertThat(lines(findings))
                    .containsExactly(lineOf(bad, "console.enabled"), lineOf(bad, "web-allow-others"));
        }

        @Test
        void switchesTheConsoleOff() {
            CheckTester tester = CheckTester.of(check).file(SETTINGS, fixture(RULE, "bad/application.properties"));

            assertThat(tester.fixed(tester.findings().getFirst())).contains("spring.h2.console.enabled=false");
        }

        @Test
        void acceptsTheConsoleInADevelopmentProfile() {
            assertThat(CheckTester.of(check)
                            .file("src/main/resources/application-dev.yml", fixture(RULE, "good/application-dev.yml"))
                            .findings())
                    .isEmpty();
        }
    }
}
