package io.github.fereshtehdev.springinspector.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.port.ReportRenderer;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class MachineReadableRendererTest {

    private static JsonNode render(ReportRenderer renderer, ReportCard card) throws IOException {
        StringBuilder out = new StringBuilder();
        renderer.render(card, out);
        return JsonRenderer.MAPPER.readTree(out.toString());
    }

    @Test
    void jsonCarriesTheWholeCard() throws IOException {
        JsonNode json = render(new JsonRenderer("1.2.3"), SampleReports.blocked());

        assertThat(json.at("/tool/version").asString()).isEqualTo("1.2.3");
        assertThat(json.at("/score").asInt()).isEqualTo(55);
        assertThat(json.at("/gate/reasons/0").asString()).isEqualTo("score 55 is below 70");
        assertThat(json.at("/categories/0/counts/major").asInt()).isEqualTo(1);
        assertThat(json.at("/categories/0/findings/0/references/0/label").asString())
                .isEqualTo("CWE-327");
        assertThat(json.at("/categories/0/findings/0/patch").asString()).startsWith("--- a/src/main/java/A.java");
        assertThat(json.at("/categories/1/hiddenFindings").asInt()).isEqualTo(2);
        assertThat(json.at("/categories/1/findings/0/patch").isNull()).isTrue();
        assertThat(json.at("/problems/0/kind").asString()).isEqualTo("failed");
        assertThat(json.at("/problems/1/kind").asString()).isEqualTo("timedOut");
        assertThat(json.at("/problems/1/timeoutMillis").asLong()).isEqualTo(5000);
    }

    @Test
    void jsonOfAPassingRunHasNoGateReasons() throws IOException {
        JsonNode json = render(new JsonRenderer("1.2.3"), SampleReports.clean());

        assertThat(json.at("/gate/blocks").asBoolean()).isFalse();
        assertThat(json.at("/gate/reasons").isEmpty()).isTrue();
    }

    @Test
    void sarifDescribesEachRuleOnceAndMapsSeveritiesToLevels() throws IOException {
        JsonNode run =
                render(new SarifRenderer("1.2.3"), SampleReports.blocked()).at("/runs/0");

        assertThat(run.at("/tool/driver/rules")).hasSize(3);
        assertThat(run.at("/tool/driver/rules/0/properties/security-severity").asString())
                .isEqualTo("7.0");
        assertThat(run.at("/tool/driver/rules/1/properties/security-severity").isMissingNode())
                .isTrue();
        assertThat(run.at("/tool/driver/rules/1/helpUri").isMissingNode()).isTrue();
        assertThat(run.at("/results/0/level").asString()).isEqualTo("error");
        assertThat(run.at("/results/1/level").asString()).isEqualTo("warning");
        assertThat(run.at("/results/2/level").asString()).isEqualTo("note");
        assertThat(run.at("/results/1/ruleIndex").asInt()).isEqualTo(1);
        assertThat(run.at("/results/0/properties/patch").isString()).isTrue();
        assertThat(run.at("/invocations/0/executionSuccessful").asBoolean()).isFalse();
        assertThat(run.at("/invocations/0/toolExecutionNotifications/1/message/text")
                        .asString())
                .contains("spring.slow was stopped after 5 s");
    }
}
