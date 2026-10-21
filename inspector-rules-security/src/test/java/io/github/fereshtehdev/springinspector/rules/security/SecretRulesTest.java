package io.github.fereshtehdev.springinspector.rules.security;

import static io.github.fereshtehdev.springinspector.rules.security.Fixtures.STRIPE_KEY;
import static io.github.fereshtehdev.springinspector.rules.security.Fixtures.fixture;
import static io.github.fereshtehdev.springinspector.rules.security.Fixtures.lines;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SecretRulesTest {

    @Nested
    class HardcodedSecrets {

        private static final String RULE = "security.hardcoded-secret";
        private final HardcodedSecretCheck check = new HardcodedSecretCheck();

        @Test
        void reportsKnownTokensAndSecretsUnderSecretNamesWithoutRepeatingThem() {
            String bad = fixture(RULE, "bad/PaymentClient.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id()).isEqualTo(HardcodedSecretCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings))
                    .containsExactly(
                            lineOf(bad, "API_KEY"),
                            lineOf(bad, "AWS_KEY"),
                            lineOf(bad, "dbPassword"),
                            lineOf(bad, "clientSecret = "));
            assertThat(findings)
                    .extracting(Finding::severity)
                    .containsExactly(Severity.BLOCKER, Severity.BLOCKER, Severity.MAJOR, Severity.MAJOR);
            assertThat(findings.getFirst().whatsWrong().description())
                    .isEqualTo("A Stripe secret key is written in the code");
            assertThat(findings)
                    .allSatisfy(finding -> assertThat(finding.whatsWrong().snippet())
                            .doesNotContain(STRIPE_KEY, Fixtures.AWS_KEY, "Summer2024!x", "c9f2-77ab-41d0-xyz9")
                            .contains("****"));
        }

        @Test
        void acceptsHeadersPropertyNamesUrlsAndInjectedValues() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/PaymentClient.java"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void leavesTestSourcesAlone() {
            assertThat(CheckTester.of(check)
                            .file(
                                    "src/test/java/com/acme/PaymentClientTest.java",
                                    fixture(RULE, "bad/PaymentClient.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class SecretsInSettings {

        private static final String RULE = "security.secret-in-settings";
        private static final String SETTINGS = "src/main/resources/application.yml";
        private final SecretInSettingsCheck check = new SecretInSettingsCheck();

        @Test
        void reportsPasswordsAndKnownTokensWithMaskedSnippets() {
            String bad = fixture(RULE, "bad/application.yml");

            List<Finding> findings = CheckTester.of(check).file(SETTINGS, bad).findings();

            assertThat(check.id()).isEqualTo(SecretInSettingsCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings)).containsExactly(lineOf(bad, "password:"), lineOf(bad, "api-key:"));
            assertThat(findings).extracting(Finding::severity).containsExactly(Severity.MAJOR, Severity.BLOCKER);
            assertThat(findings)
                    .allSatisfy(finding ->
                            assertThat(finding.whatsWrong().snippet()).doesNotContain("Prod-Db-2024!x", STRIPE_KEY));
        }

        @Test
        void movesThePasswordToAnEnvironmentVariableSpringBindsByItself() {
            CheckTester tester = CheckTester.of(check).file(SETTINGS, fixture(RULE, "bad/application.yml"));

            assertThat(tester.fixed(tester.findings().getFirst())).contains("password: ${SPRING_DATASOURCE_PASSWORD}");
        }

        @Test
        void acceptsPlaceholdersUrlsAndDevelopmentProfiles() {
            assertThat(CheckTester.of(check)
                            .file(SETTINGS, fixture(RULE, "good/application.yml"))
                            .findings())
                    .isEmpty();
            assertThat(CheckTester.of(check)
                            .file(
                                    "src/main/resources/application-local.properties",
                                    "spring.datasource.password=Local-Pass-1!\n")
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class SecretsInLogs {

        private static final String RULE = "security.secret-logged";
        private final SecretLoggedCheck check = new SecretLoggedCheck();

        @Test
        void reportsSecretsPassedToLoggersAndTheConsole() {
            String bad = fixture(RULE, "bad/LoginService.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id()).isEqualTo(SecretLoggedCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings)).containsExactly(lineOf(bad, "log.debug"), lineOf(bad, "System.out.println"));
            assertThat(findings)
                    .extracting(finding -> finding.whatsWrong().description())
                    .containsExactly(
                            "password is written to the log", "credentials.getAccessToken() is written to the log");
        }

        @Test
        void acceptsConstantsCountsAndMessagesWithoutSecrets() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/LoginService.java"))
                            .findings())
                    .isEmpty();
        }
    }
}
