package io.github.fereshtehdev.springinspector.rules.springai;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpringAiSafetyRulesTest {

    private static String fixture(String rule, String path) {
        return resource("fixtures/" + rule + "/" + path);
    }

    private static List<Integer> lines(List<Finding> findings) {
        return findings.stream()
                .map(finding -> finding.location().lines().start())
                .toList();
    }

    private static void assertSpringAiRule(Check check, String id) {
        assertThat(check.id().value()).isEqualTo(id);
        assertThat(check.category()).isEqualTo(Category.SPRING_AI);
    }

    @Nested
    class DestructiveTools {

        private static final String RULE = "springai.unconfirmed-destructive-tool";
        private final DestructiveToolCheck check = new DestructiveToolCheck();

        @Test
        void reportsToolsThatChangeDataWithoutChecks() {
            String bad = fixture(RULE, "bad/AccountTools.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertSpringAiRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactly(lineOf(bad, "void deleteAccount"), lineOf(bad, "void refundOrder"));
            assertThat(findings.getFirst().whatsWrong().description())
                    .isEqualTo("Tool deleteAccount changes data without authorization or confirmation");
        }

        @Test
        void acceptsAuthorizedOrConfirmedToolsAndPlainMethods() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/AccountTools.java"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void authorizationOnTheClassCoversItsTools() {
            assertThat(CheckTester.of(check).source("""
                                    package com.acme;

                                    import org.springframework.ai.tool.annotation.Tool;
                                    import org.springframework.security.access.annotation.Secured;

                                    @Secured("ROLE_ADMIN")
                                    class AdminTools {
                                        @Tool
                                        void purgeCache() {}
                                    }
                                    """).findings()).isEmpty();
        }
    }

    @Nested
    class FullPromptLogging {

        private static final String RULE = "springai.full-prompt-logging";
        private final FullPromptLoggingCheck check = new FullPromptLoggingCheck();

        @Test
        void reportsTheDefaultLoggerAdvisorWhicheverWayItIsBuilt() {
            String bad = fixture(RULE, "bad/ChatConfig.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertSpringAiRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "new SimpleLoggerAdvisor()"), lineOf(bad, "SimpleLoggerAdvisor.builder()"));
        }

        @Test
        void acceptsAnAdvisorWithItsOwnFormatting() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/ChatConfig.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class LlmOutputToSink {

        private static final String RULE = "springai.llm-output-to-sink";
        private final LlmOutputToSinkCheck check = new LlmOutputToSinkCheck();

        @Test
        void reportsModelOutputUsedAsSqlCommandsAndPaths() {
            String bad = fixture(RULE, "bad/ReportAssistant.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertSpringAiRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "jdbc.queryForList(sql)"),
                            lineOf(bad, "Runtime.getRuntime().exec"),
                            lineOf(bad, "new ProcessBuilder"),
                            lineOf(bad, "Path.of(\"/srv/files\""));
            assertThat(findings)
                    .extracting(finding -> finding.whatsWrong().description())
                    .contains(
                            "Model output is used as SQL",
                            "Model output is used as a shell command",
                            "Model output is used as a file path");
        }

        @Test
        void acceptsValidatedStructuredOutputAndTextReturnedToTheUser() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/ReportAssistant.java"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void tellsModelOutputFromOtherContentAndCoversEveryPathSink() {
            String source = """
                    package com.acme;

                    import java.nio.file.Path;
                    import java.nio.file.Paths;
                    import org.springframework.ai.chat.client.ChatClient;
                    import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;

                    class Files {
                        Object open(ChatClient chat, Message message, Path base) {
                            Paths.get(message.content());
                            base.resolve(chat.prompt().call().content());
                            new SimpleLoggerAdvisor(request -> "r", response -> "s", 0);
                            return Paths.get("a", chat.prompt().user("x").call().content());
                        }
                    }
                    """;

            assertThat(CheckTester.of(check).source(source).findings()).hasSize(2);
            assertThat(CheckTester.of(new FullPromptLoggingCheck())
                            .source(source)
                            .findings())
                    .isEmpty();
        }

        @Test
        void followsOutputIntoFileObjectsAndClassLoading() {
            assertThat(CheckTester.of(check).source("""
                                    package com.acme;

                                    import java.io.File;
                                    import org.springframework.ai.chat.client.ChatClient;

                                    class Loader {
                                        Object load(ChatClient chat) throws Exception {
                                            String name = chat.prompt().user("pick").call().content();
                                            new File(name).delete();
                                            return Class.forName(name);
                                        }
                                    }
                                    """).findings()).hasSize(2);
        }
    }
}
