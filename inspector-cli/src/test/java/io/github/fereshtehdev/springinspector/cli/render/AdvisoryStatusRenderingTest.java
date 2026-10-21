package io.github.fereshtehdev.springinspector.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.report.AdvisoryStatus;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.port.ReportRenderer;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AdvisoryStatusRenderingTest {

    static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    static final AdvisoryStatus FRESH = new AdvisoryStatus(Optional.of(NOW.minus(Duration.ofDays(1))), NOW);
    static final AdvisoryStatus STALE = new AdvisoryStatus(Optional.of(NOW.minus(Duration.ofDays(30))), NOW);
    static final AdvisoryStatus MISSING = new AdvisoryStatus(Optional.empty(), NOW);

    @Test
    void terminalShowsTheDateOrTheWarning() throws IOException {
        assertThat(text(new TerminalRenderer(Style.PLAIN, Optional.empty()), FRESH))
                .contains("  Vulnerability advisories from 2026-10-20\n");
        assertThat(text(new TerminalRenderer(Style.PLAIN, Optional.empty()), STALE))
                .contains("  ! The vulnerability advisories are 30 days old")
                .doesNotContain("advisories from");
        assertThat(text(new TerminalRenderer(Style.PLAIN, Optional.empty()), MISSING))
                .contains("  ! No vulnerability advisories on this machine");
    }

    @Test
    void terminalSaysNothingWhenDependenciesAreNotChecked() throws IOException {
        StringBuilder out = new StringBuilder();
        new TerminalRenderer(Style.PLAIN, Optional.empty()).render(SampleReports.clean(), out);

        assertThat(out.toString()).doesNotContain("advisories");
    }

    @Test
    void jsonCarriesTheDateStalenessAndWarning() throws IOException {
        JsonNode fresh = json(new JsonRenderer("1.0"), FRESH).path("advisories");
        JsonNode missing = json(new JsonRenderer("1.0"), MISSING).path("advisories");

        assertThat(fresh.path("updatedAt").asString()).isEqualTo("2026-10-20T12:00:00Z");
        assertThat(fresh.path("stale").asBoolean()).isFalse();
        assertThat(fresh.path("warning").isNull()).isTrue();
        assertThat(missing.path("updatedAt").isNull()).isTrue();
        assertThat(missing.path("stale").asBoolean()).isTrue();
        assertThat(missing.path("warning").asString()).startsWith("No vulnerability advisories");
        assertThat(json(new JsonRenderer("1.0"), null).has("advisories")).isFalse();
    }

    @Test
    void sarifRecordsTheDateAndRaisesANotificationWhenStale() throws IOException {
        JsonNode stale = json(new SarifRenderer("1.0"), STALE).path("runs").get(0);
        JsonNode fresh = json(new SarifRenderer("1.0"), FRESH).path("runs").get(0);

        assertThat(stale.path("properties").path("advisoriesUpdatedAt").asString())
                .isEqualTo("2026-09-21T12:00:00Z");
        assertThat(stale.path("invocations")
                        .get(0)
                        .path("toolExecutionNotifications")
                        .toString())
                .contains("30 days old");
        assertThat(fresh.path("invocations")
                        .get(0)
                        .path("toolExecutionNotifications")
                        .toString())
                .doesNotContain("advisories");
    }

    private static String text(ReportRenderer renderer, AdvisoryStatus status) throws IOException {
        StringBuilder out = new StringBuilder();
        renderer.render(SampleReports.clean().withAdvisories(status), out);
        return out.toString();
    }

    private static JsonNode json(ReportRenderer renderer, @Nullable AdvisoryStatus status) throws IOException {
        ReportCard card =
                status == null ? SampleReports.clean() : SampleReports.clean().withAdvisories(status);
        StringBuilder out = new StringBuilder();
        renderer.render(card, out);
        return JsonRenderer.MAPPER.readTree(out.toString());
    }
}
