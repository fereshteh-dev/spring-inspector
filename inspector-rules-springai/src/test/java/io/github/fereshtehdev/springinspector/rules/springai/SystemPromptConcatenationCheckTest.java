package io.github.fereshtehdev.springinspector.rules.springai;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemPromptConcatenationCheckTest {

    private static final String FIXTURES = "fixtures/springai.system-prompt-concatenation/";

    private final SystemPromptConcatenationCheck check = new SystemPromptConcatenationCheck();

    @Test
    void identifiesItselfAsASpringAiRule() {
        assertThat(check.id()).isEqualTo(SystemPromptConcatenationCheck.ID);
        assertThat(check.category()).isEqualTo(Category.SPRING_AI);
    }

    @Test
    void reportsSystemPromptsBuiltFromValuesWithHigherConfidenceForRequestInput() {
        List<Finding> findings = CheckTester.of(check)
                .source(resource(FIXTURES + "bad/SupportController.java"))
                .findings();

        assertThat(findings)
                .extracting(
                        finding -> finding.location().lines().start(),
                        Finding::confidence,
                        finding -> finding.whatsWrong().description())
                .containsExactlyInAnyOrder(
                        tuple(23, Confidence.HIGH, "Request input is concatenated into a system prompt"),
                        tuple(31, Confidence.MEDIUM, "The system prompt is built from runtime values"),
                        tuple(38, Confidence.MEDIUM, "The system prompt is built from runtime values"));
    }

    @Test
    void acceptsAConstantSystemPromptWithDataInTheUserMessage() {
        assertThat(CheckTester.of(check)
                        .source(resource(FIXTURES + "good/SupportController.java"))
                        .findings())
                .isEmpty();
    }

    @Test
    void coversDefaultSystemPromptsButNotUserMessagesOrConstants() {
        List<Finding> findings = CheckTester.of(check).source("""
                        package com.acme;

                        import org.springframework.ai.chat.client.ChatClient;
                        import org.springframework.ai.chat.messages.SystemMessage;

                        class Config {
                            ChatClient client(ChatClient.Builder builder, Settings settings, String question) {
                                new SystemMessage("constant");
                                new SystemMessage(settings.rules());
                                builder.defaultUser(u -> u.text("Question: " + question));
                                return builder.defaultSystem("Company: " + settings.company()).build();
                            }
                        }
                        """).findings();

        assertThat(findings)
                .extracting(finding -> finding.location().lines().start(), Finding::confidence)
                .containsExactly(tuple(11, Confidence.MEDIUM));
    }

    @Test
    void parametersWithoutRequestBindingKeepMediumConfidence() {
        assertThat(CheckTester.of(check).source("""
                                package com.acme;

                                import org.springframework.ai.chat.client.ChatClient;
                                import org.springframework.web.bind.annotation.RequestParam;

                                class Service {
                                    String ask(ChatClient chat, String topic, @RequestParam String unused) {
                                        return chat.prompt().system("Topic: " + topic).call().content();
                                    }
                                }
                                """).singleFinding().confidence())
                .isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void ignoresFilesThatDoNotUseSpringAi() {
        assertThat(CheckTester.of(check).source("""
                                package com.acme;

                                class Shell {
                                    void run(Os os, String command) {
                                        os.system("sh -c " + command);
                                    }
                                }
                                """).findings()).isEmpty();
    }
}
