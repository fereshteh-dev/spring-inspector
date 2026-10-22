package io.github.fereshtehdev.springinspector.intellij.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.intellij.report.Report;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdvisoryRefreshTest {

    static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    @Test
    void refreshesMissingAndDayOldAdvisories() {
        assertThat(AdvisoryRefresh.isDue(report(Optional.empty(), Optional.of("No vulnerability advisories")), NOW))
                .isTrue();
        assertThat(AdvisoryRefresh.isDue(report(Optional.of("2026-10-21T11:00:00Z"), Optional.empty()), NOW))
                .isTrue();
        assertThat(AdvisoryRefresh.isDue(report(Optional.of("not a date"), Optional.empty()), NOW))
                .isTrue();
    }

    @Test
    void leavesRecentAdvisoriesAndRunsWithoutDependencyChecksAlone() {
        assertThat(AdvisoryRefresh.isDue(report(Optional.of("2026-10-22T08:00:00Z"), Optional.empty()), NOW))
                .isFalse();
        assertThat(AdvisoryRefresh.isDue(report(Optional.empty(), Optional.empty()), NOW))
                .isFalse();
    }

    static Report report(Optional<String> updatedAt, Optional<String> warning) {
        return new Report(100, false, List.of(), List.of(), List.of(), updatedAt, warning);
    }
}
