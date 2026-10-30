package io.github.fereshtehdev.springinspector.rules.security;

import static io.github.fereshtehdev.springinspector.rules.security.Fixtures.fixture;
import static io.github.fereshtehdev.springinspector.rules.security.Fixtures.lines;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InjectionAndExposureRulesTest {

    private static final String SETTINGS = "src/main/resources/application.yml";

    private static void assertSecurityRule(Check check, String id) {
        assertThat(check.id().value()).isEqualTo(id);
        assertThat(check.category()).isEqualTo(Category.SECURITY);
    }

    private static List<Finding> findings(Check check, String rule, String file) {
        return CheckTester.of(check).source(fixture(rule, file)).findings();
    }

    private static List<Finding> settingsFindings(Check check, String rule, String file) {
        return CheckTester.of(check).file(SETTINGS, fixture(rule, file)).findings();
    }

    @Nested
    class CommandInjection {

        private static final String RULE = "security.command-injection";
        private final CommandInjectionCheck check = new CommandInjectionCheck();

        @Test
        void reportsCommandsBuiltFromRequestInputAsBlockers() {
            String bad = fixture(RULE, "bad/Tools.java");

            List<Finding> found = CheckTester.of(check).source(bad).findings();

            assertSecurityRule(check, RULE);
            assertThat(lines(found))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "exec(\"ping"),
                            lineOf(bad, "new ProcessBuilder"),
                            lineOf(bad, "exec(command)"));
            assertThat(found).allMatch(finding -> finding.severity() == Severity.BLOCKER);
        }

        @Test
        void acceptsFixedCommandsNumericInputAndOtherExecMethods() {
            assertThat(findings(check, RULE, "good/Tools.java")).isEmpty();
        }
    }

    @Nested
    class SpelInjection {

        private static final String RULE = "security.spel-injection";
        private final SpelInjectionCheck check = new SpelInjectionCheck();

        @Test
        void reportsExpressionsParsedFromRequestInput() {
            String bad = fixture(RULE, "bad/Rules.java");

            Finding finding = CheckTester.of(check).source(bad).singleFinding();

            assertSecurityRule(check, RULE);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "parseExpression"));
            assertThat(finding.severity()).isEqualTo(Severity.BLOCKER);
        }

        @Test
        void acceptsFixedExpressions() {
            assertThat(findings(check, RULE, "good/Rules.java")).isEmpty();
        }

        @Test
        void ignoresSameNamedMethodsOutsideSpringExpressions() {
            assertThat(CheckTester.of(check).source("""
                                    package com.acme;

                                    import org.springframework.web.bind.annotation.*;

                                    class Calculator {
                                        Object run(Parser parser, @RequestParam String formula) {
                                            return parser.parseExpression(formula);
                                        }
                                    }
                                    """).findings()).isEmpty();
        }
    }

    @Nested
    class OpenRedirect {

        private static final String RULE = "security.open-redirect";
        private final OpenRedirectCheck check = new OpenRedirectCheck();

        @Test
        void reportsRedirectsToTargetsFromTheRequest() {
            String bad = fixture(RULE, "bad/Login.java");

            List<Finding> found = CheckTester.of(check).source(bad).findings();

            assertSecurityRule(check, RULE);
            assertThat(lines(found))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "\"redirect:\" + next"),
                            lineOf(bad, "response.sendRedirect(next)"),
                            lineOf(bad, "new RedirectView(next)"),
                            lineOf(bad, "\"/\" + next"));
        }

        @Test
        void acceptsFixedPathsFixedHostsAndNumericValues() {
            assertThat(findings(check, RULE, "good/Login.java")).isEmpty();
        }
    }

    @Nested
    class CorsCredentials {

        private static final String RULE = "security.cors-any-origin-with-credentials";
        private final CorsCredentialsCheck check = new CorsCredentialsCheck();

        @Test
        void reportsAnyOriginPatternWithCredentials() {
            String bad = fixture(RULE, "bad/Cors.java");

            List<Finding> found = CheckTester.of(check).source(bad).findings();

            assertSecurityRule(check, RULE);
            assertThat(lines(found))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "registry.addMapping"),
                            lineOf(bad, "setAllowedOriginPatterns"),
                            lineOf(bad, "@CrossOrigin(originPatterns"));
        }

        @Test
        void acceptsListedOriginsAndPublicApisWithoutCredentials() {
            assertThat(findings(check, RULE, "good/Cors.java")).isEmpty();
        }
    }

    @Nested
    class PlaintextPassword {

        private static final String RULE = "security.plaintext-password";
        private final PlaintextPasswordCheck check = new PlaintextPasswordCheck();

        @Test
        void reportsNoOpEncodersDemoUsersAndNoopValues() {
            String bad = fixture(RULE, "bad/Users.java");

            List<Finding> found = CheckTester.of(check).source(bad).findings();

            assertSecurityRule(check, RULE);
            assertThat(lines(found))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "NoOpPasswordEncoder.getInstance"),
                            lineOf(bad, "withDefaultPasswordEncoder"),
                            lineOf(bad, "{noop}"));
        }

        @Test
        void acceptsRealEncodersAndOtherGetInstanceMethods() {
            assertThat(findings(check, RULE, "good/Users.java")).isEmpty();
        }

        @Test
        void leavesTestCodeAlone() {
            assertThat(CheckTester.of(check)
                            .file("src/test/java/com/acme/Users.java", fixture(RULE, "bad/Users.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class TlsVerificationDisabled {

        private static final String RULE = "security.tls-verification-disabled";
        private final TlsVerificationDisabledCheck check = new TlsVerificationDisabledCheck();

        @Test
        void reportsEmptyTrustManagersAlwaysTrueVerifiersAndInsecureHelpers() {
            String bad = fixture(RULE, "bad/Client.java");

            List<Finding> found = CheckTester.of(check).source(bad).findings();

            assertSecurityRule(check, RULE);
            assertThat(lines(found))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "NoopHostnameVerifier;"),
                            lineOf(bad, "void checkServerTrusted"),
                            lineOf(bad, "public boolean verify"),
                            lineOf(bad, "(host, session) -> true"));
        }

        @Test
        void acceptsRealChecksAndUnrelatedVerifyMethods() {
            assertThat(findings(check, RULE, "good/Client.java")).isEmpty();
        }

        @Test
        void leavesTestCodeAlone() {
            assertThat(CheckTester.of(check)
                            .file("src/test/java/com/acme/Client.java", fixture(RULE, "bad/Client.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class ErrorDetailsExposed {

        private static final String RULE = "security.error-details-exposed";
        private final ErrorDetailsExposedCheck check = new ErrorDetailsExposedCheck();

        @Test
        void reportsEveryAlwaysOnErrorDetail() {
            assertSecurityRule(check, RULE);
            assertThat(settingsFindings(check, RULE, "bad/application.yml"))
                    .extracting(Finding::severity)
                    .containsExactly(Severity.MAJOR, Severity.MAJOR, Severity.MINOR, Severity.MINOR);
        }

        @Test
        void acceptsSafeValuesAndDevelopmentProfiles() {
            assertThat(settingsFindings(check, RULE, "good/application.yml")).isEmpty();
        }

        @Test
        void fixesTheSettingWithAPatch() {
            CheckTester tester = CheckTester.of(check)
                    .file("src/main/resources/application.properties", "server.error.include-stacktrace=always\n");

            assertThat(tester.fixed(tester.singleFinding())).isEqualTo("server.error.include-stacktrace=never\n");
        }
    }

    @Nested
    class InsecureSessionCookie {

        private static final String RULE = "security.insecure-session-cookie";
        private final InsecureSessionCookieCheck check = new InsecureSessionCookieCheck();

        @Test
        void reportsCookiesWithoutHttpOnlyOrSecure() {
            assertSecurityRule(check, RULE);
            assertThat(settingsFindings(check, RULE, "bad/application.yml"))
                    .extracting(finding -> finding.whatsWrong().description())
                    .containsExactly(
                            "Scripts in the page can read the session cookie",
                            "The session cookie can be sent over unencrypted HTTP");
        }

        @Test
        void acceptsSafeValuesAndDevelopmentProfiles() {
            assertThat(settingsFindings(check, RULE, "good/application.yml")).isEmpty();
        }

        @Test
        void fixesTheSettingWithAPatch() {
            CheckTester tester = CheckTester.of(check)
                    .file(
                            "src/main/resources/application.properties",
                            "server.servlet.session.cookie.http-only=false\n");

            assertThat(tester.fixed(tester.singleFinding()))
                    .isEqualTo("server.servlet.session.cookie.http-only=true\n");
        }
    }
}
