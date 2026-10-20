package io.github.fereshtehdev.springinspector.core.domain;

import static io.github.fereshtehdev.springinspector.core.testing.FindingBuilder.aFinding;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FindingTest {

    @Test
    void rejectsAiFindingMarkedAsBlocker() {
        assertThatThrownBy(() -> aFinding()
                        .source(FindingSource.AI)
                        .severity(Severity.BLOCKER)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never BLOCKER");
    }

    @Test
    void acceptsAiFindingBelowBlocker() {
        Finding finding =
                aFinding().source(FindingSource.AI).severity(Severity.MAJOR).build();

        assertThat(finding.severity()).isEqualTo(Severity.MAJOR);
    }

    @Test
    void acceptsBlockerFromRules() {
        Finding finding =
                aFinding().source(FindingSource.RULE).severity(Severity.BLOCKER).build();

        assertThat(finding.severity()).isEqualTo(Severity.BLOCKER);
    }

    @Test
    void changesOnlySeverityWhenOverridden() {
        Finding original = aFinding().severity(Severity.MINOR).build();

        Finding raised = original.withSeverity(Severity.MAJOR);

        assertThat(raised.severity()).isEqualTo(Severity.MAJOR);
        assertThat(raised).usingRecursiveComparison().ignoringFields("severity").isEqualTo(original);
    }

    @Test
    void ranksSeveritiesFromBlockerDownToInfo() {
        assertThat(Severity.BLOCKER.isAtLeast(Severity.MAJOR)).isTrue();
        assertThat(Severity.MAJOR.isAtLeast(Severity.MAJOR)).isTrue();
        assertThat(Severity.MINOR.isAtLeast(Severity.MAJOR)).isFalse();
        assertThat(Severity.INFO.isAtLeast(Severity.BLOCKER)).isFalse();
    }
}
