package io.github.fereshtehdev.springinspector.core.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdvisoryStatusTest {

    static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void recentAdvisoriesNeedNoWarning() {
        AdvisoryStatus status = new AdvisoryStatus(Optional.of(NOW.minus(Duration.ofDays(7))), NOW);

        assertThat(status.isStale()).isFalse();
        assertThat(status.warning()).isEmpty();
    }

    @Test
    void warnsWhenTheAdvisoriesAreOlderThanAWeek() {
        AdvisoryStatus status = new AdvisoryStatus(Optional.of(NOW.minus(Duration.ofDays(12))), NOW);

        assertThat(status.isStale()).isTrue();
        assertThat(status.warning())
                .contains("The vulnerability advisories are 12 days old and may miss recent vulnerabilities;"
                        + " run 'spring-inspector update'");
    }

    @Test
    void warnsWhenThereAreNoAdvisories() {
        AdvisoryStatus status = new AdvisoryStatus(Optional.empty(), NOW);

        assertThat(status.isStale()).isTrue();
        assertThat(status.warning())
                .hasValueSatisfying(
                        warning -> assertThat(warning).startsWith("No vulnerability advisories on this machine"));
    }

    @Test
    void aReportCardCarriesTheStatusOnlyWhenGivenOne() {
        ReportCard card = new ReportCard(List.of(), new Score(100), GateDecision.pass(), List.of());
        AdvisoryStatus status = new AdvisoryStatus(Optional.of(NOW), NOW);

        assertThat(card.advisories()).isEmpty();
        assertThat(card.withAdvisories(status).advisories()).contains(status);
        assertThat(card.withAdvisories(status).score()).isEqualTo(card.score());
    }
}
