package io.github.fereshtehdev.springinspector.rules.springai;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedConversationIdCheckTest {

    private static final String FIXTURES = "fixtures/springai.shared-conversation-id/";

    private final SharedConversationIdCheck check = new SharedConversationIdCheck();

    @Test
    void identifiesItselfAsASpringAiRule() {
        assertThat(check.id()).isEqualTo(SharedConversationIdCheck.ID);
        assertThat(check.category()).isEqualTo(Category.SPRING_AI);
    }

    @Test
    void reportsFixedConversationIdsWrittenEitherWay() {
        List<Finding> findings = CheckTester.of(check)
                .source(resource(FIXTURES + "bad/Assistant.java"))
                .findings();

        assertThat(findings)
                .extracting(finding -> finding.location().lines().start())
                .containsExactly(21, 29);
        assertThat(findings).allMatch(finding -> finding.severity() == Severity.MAJOR);
        assertThat(findings.getFirst().whatsWrong().description())
                .isEqualTo("Chat memory uses the fixed conversation id \"default\"");
    }

    @Test
    void recognisesTheKeyByItsValueAndIdsHeldInLocals() {
        assertThat(CheckTester.of(check).source("""
                                package com.acme;

                                class Memory {
                                    void configure(AdvisorSpec a) {
                                        String id = "shared";
                                        a.param("chat_memory_conversation_id", id);
                                    }
                                }
                                """).findings()).hasSize(1);
    }

    @Test
    void recognisesAWildcardStaticImportOfChatMemory() {
        assertThat(CheckTester.of(check)
                        .source("""
                                package com.acme;

                                import static org.springframework.ai.chat.memory.ChatMemory.*;

                                class Memory {
                                    void configure(AdvisorSpec a, String id) {
                                        a.param(CONVERSATION_ID, 42);
                                        a.param(CONVERSATION_ID, id);
                                        a.param("other", "x");
                                        a.param(CONVERSATION_ID);
                                    }
                                }
                                """)
                        .singleFinding()
                        .location()
                        .lines()
                        .start())
                .isEqualTo(7);
    }

    @Test
    void acceptsAnIdThatBelongsToTheCaller() {
        assertThat(CheckTester.of(check)
                        .source(resource(FIXTURES + "good/Assistant.java"))
                        .findings())
                .isEmpty();
    }

    @Test
    void ignoresAConstantNamedLikeTheKeyFromAnotherClass() {
        assertThat(CheckTester.of(check).source("""
                                package com.acme;

                                class Other {
                                    void configure(Spec a) {
                                        a.param(Keys.CONVERSATION_ID, "x");
                                        a.param(CONVERSATION_ID, "y");
                                    }
                                }
                                """).findings()).isEmpty();
    }
}
