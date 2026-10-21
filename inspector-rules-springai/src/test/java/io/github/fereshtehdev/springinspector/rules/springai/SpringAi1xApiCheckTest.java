package io.github.fereshtehdev.springinspector.rules.springai;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import org.junit.jupiter.api.Test;

class SpringAi1xApiCheckTest {

    private static final String FIXTURES = "fixtures/migration.spring-ai-1x-api/";

    private final SpringAi1xApiCheck check = new SpringAi1xApiCheck();

    @Test
    void reportsClassesRemovedInSpringAi2() {
        String bad = resource(FIXTURES + "bad/ChatSetup.java");

        Finding finding =
                CheckTester.of(check).springAiVersion("2.0.1").source(bad).singleFinding();

        assertThat(check.id().value()).isEqualTo("migration.spring-ai-1x-api");
        assertThat(check.category()).isEqualTo(Category.MIGRATION);
        assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "PromptChatMemoryAdvisor;"));
        assertThat(finding.whatsWrong().description())
                .isEqualTo("PromptChatMemoryAdvisor was removed in Spring AI 2.0");
    }

    @Test
    void appliesToSpringAi2AndAcceptsItsApis() {
        assertThat(CheckTester.of(check).springAiVersion("1.1.7").applies()).isFalse();
        assertThat(CheckTester.of(check)
                        .springAiVersion("2.0.1")
                        .source(resource(FIXTURES + "good/ChatSetup.java"))
                        .findings())
                .isEmpty();
    }
}
