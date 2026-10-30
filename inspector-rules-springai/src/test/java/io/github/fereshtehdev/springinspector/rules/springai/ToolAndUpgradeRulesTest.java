package io.github.fereshtehdev.springinspector.rules.springai;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToolAndUpgradeRulesTest {

    private static final String SETTINGS = "src/main/resources/application.yml";

    private static String fixture(String rule, String path) {
        return resource("fixtures/" + rule + "/" + path);
    }

    private static List<Integer> lines(List<Finding> findings) {
        return findings.stream()
                .map(finding -> finding.location().lines().start())
                .toList();
    }

    @Nested
    class ToolGuardrailDisabled {

        private static final String RULE = "springai.tool-guardrail-disabled";
        private final ToolGuardrailDisabledCheck check = new ToolGuardrailDisabledCheck();

        @Test
        void reportsEachSafeguardThatIsSwitchedOff() {
            String bad = fixture(RULE, "bad/ToolsConfig.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id().value()).isEqualTo(RULE);
            assertThat(check.category()).isEqualTo(Category.SPRING_AI);
            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "unlimitedCallsPerTool"),
                            lineOf(bad, "unlimitedTotalToolCalls"),
                            lineOf(bad, "resolutionFallbackEnabled"));
        }

        @Test
        void acceptsLimitsAndADisabledFallback() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/ToolsConfig.java"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void ignoresSameNamedMethodsInFilesWithoutSpringAi() {
            assertThat(CheckTester.of(check).source("""
                                    package com.acme;

                                    class Plan {
                                        void apply(Quota quota) {
                                            quota.unlimitedCallsPerTool();
                                        }
                                    }
                                    """).findings()).isEmpty();
        }
    }

    @Nested
    class ToolResolutionFallback {

        private final ToolResolutionFallbackCheck check = new ToolResolutionFallbackCheck();

        @Test
        void reportsAndFixesTheEnabledFallback() {
            CheckTester tester = CheckTester.of(check)
                    .file(
                            "src/main/resources/application.properties",
                            "spring.ai.tools.resolution.fallback.enabled=true\n");

            assertThat(check.id().value()).isEqualTo("springai.tool-resolution-fallback");
            assertThat(tester.fixed(tester.singleFinding()))
                    .isEqualTo("spring.ai.tools.resolution.fallback.enabled=false\n");
        }

        @Test
        void acceptsTheDefaultAndDevelopmentProfiles() {
            assertThat(CheckTester.of(check)
                            .file(
                                    "src/main/resources/application.properties",
                                    "spring.ai.tools.resolution.fallback.enabled=false\n")
                            .file(
                                    "src/main/resources/application-dev.properties",
                                    "spring.ai.tools.resolution.fallback.enabled=true\n")
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class IdentityFromModel {

        private static final String RULE = "springai.identity-from-model";
        private final IdentityFromModelCheck check = new IdentityFromModelCheck();

        @Test
        void reportsToolParametersThatNameTheCaller() {
            String bad = fixture(RULE, "bad/OrderTools.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(lines(findings)).containsExactly(lineOf(bad, "String userId"), lineOf(bad, "String tenantId"));
            assertThat(findings.getFirst().whatsWrong().description())
                    .isEqualTo("The model chooses the identity userId this tool acts for");
        }

        @Test
        void acceptsToolContextAuthorizationBusinessIdsAndPlainMethods() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/OrderTools.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class ToolWithoutDescription {

        private static final String RULE = "springai.tool-without-description";
        private final ToolWithoutDescriptionCheck check = new ToolWithoutDescriptionCheck();

        @Test
        void reportsMissingAndBlankDescriptions() {
            String bad = fixture(RULE, "bad/WeatherTools.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(lines(findings))
                    .containsExactly(
                            lineOf(bad, "String forecast"),
                            lineOf(bad, "int temperature"),
                            lineOf(bad, "int humidity"));
            assertThat(findings).allMatch(finding -> finding.severity() == Severity.MINOR);
        }

        @Test
        void acceptsDescribedToolsAndPlainMethods() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/WeatherTools.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class RemovedImports {

        private final SpringAi1xApiCheck check = new SpringAi1xApiCheck();

        @Test
        void reportsMcpAndAnthropicClassesRemovedIn2_0() {
            assertThat(CheckTester.of(check)
                            .springAiVersion("2.0.2")
                            .source("""
                                    package com.acme;

                                    import org.springaicommunity.mcp.annotation.McpTool;
                                    import org.springframework.ai.anthropic.api.AnthropicApi;
                                    import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;

                                    class Setup {}
                                    """)
                            .findings())
                    .hasSize(3);
        }
    }

    @Nested
    class RemovedUsage {

        private static final String RULE = "migration.spring-ai-2-removed-usage";
        private final SpringAi2RemovedUsageCheck check = new SpringAi2RemovedUsageCheck();

        @Test
        void reportsMethodsAndConstantsRemovedIn2_0() {
            String bad = fixture(RULE, "bad/ChatSetup.java");

            List<Finding> findings =
                    CheckTester.of(check).springAiVersion("2.0.2").source(bad).findings();

            assertThat(check.category()).isEqualTo(Category.MIGRATION);
            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "internalToolExecutionEnabled"),
                            lineOf(bad, "toolNames"),
                            lineOf(bad, ".conversationId(\"a\")"),
                            lineOf(bad, "DEFAULT_CONVERSATION_ID"),
                            lineOf(bad, "toJsonString"));
        }

        @Test
        void acceptsTheCurrentApiAndOtherMethodsWithTheSameName() {
            assertThat(CheckTester.of(check)
                            .springAiVersion("2.0.2")
                            .source(fixture(RULE, "good/ChatSetup.java"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void doesNotApplyBeforeSpringAi2() {
            assertThat(CheckTester.of(check).springAiVersion("1.1.7").applies()).isFalse();
        }
    }

    @Nested
    class Settings {

        private static final String RULE = "migration.spring-ai-2-settings";
        private final SpringAi2SettingsCheck check = new SpringAi2SettingsCheck();

        @Test
        void reportsRemovedSettingsAsMajorAndDeprecatedOnesAsMinor() {
            List<Finding> findings = CheckTester.of(check)
                    .springAiVersion("2.0.2")
                    .file(SETTINGS, fixture(RULE, "bad/application.yml"))
                    .findings();

            assertThat(findings)
                    .filteredOn(finding -> finding.severity() == Severity.MAJOR)
                    .hasSize(3);
            assertThat(findings)
                    .filteredOn(finding -> finding.severity() == Severity.MINOR)
                    .hasSize(2);
        }

        @Test
        void acceptsCurrentSettings() {
            assertThat(CheckTester.of(check)
                            .springAiVersion("2.0.2")
                            .file(SETTINGS, fixture(RULE, "good/application.yml"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void doesNotApplyBeforeSpringAi2() {
            assertThat(CheckTester.of(check).springAiVersion("1.1.7").applies()).isFalse();
        }
    }
}
