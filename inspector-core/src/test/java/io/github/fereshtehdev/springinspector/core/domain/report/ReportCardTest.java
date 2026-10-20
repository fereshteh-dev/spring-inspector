package io.github.fereshtehdev.springinspector.core.domain.report;

import static io.github.fereshtehdev.springinspector.core.testing.FindingBuilder.aFinding;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportCardTest {

    private static final CategoryResult SECURITY_PASSED =
            new CategoryResult(Category.SECURITY, CategoryStatus.PASSED, new SeverityCounts(0, 0, 0, 0), List.of(), 0);

    @Test
    void rejectsScoresOutsideZeroToHundred() {
        assertThatThrownBy(() -> new Score(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Score(101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Score.PERFECT.value()).isEqualTo(100);
    }

    @Test
    void requiresAReasonToBlock() {
        assertThatThrownBy(() -> new GateDecision.Block(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blocksOnlyWithABlockDecision() {
        assertThat(GateDecision.pass().blocks()).isFalse();
        assertThat(new GateDecision.Block(List.of("BLOCKER SEC-001")).blocks()).isTrue();
    }

    @Test
    void countsFindingsBySeverity() {
        SeverityCounts counts = SeverityCounts.of(List.of(
                aFinding().rule("A").severity(Severity.BLOCKER).build(),
                aFinding().rule("B").severity(Severity.MAJOR).build(),
                aFinding().rule("C").severity(Severity.MINOR).build(),
                aFinding().rule("D").severity(Severity.INFO).build(),
                aFinding().rule("E").severity(Severity.INFO).build()));

        assertThat(counts).isEqualTo(new SeverityCounts(1, 1, 1, 2));
        assertThat(counts.total()).isEqualTo(5);
        assertThat(counts.issues()).isEqualTo(2);
    }

    @Test
    void rejectsNegativeHiddenFindings() {
        assertThatThrownBy(() -> new CategoryResult(
                        Category.SECURITY, CategoryStatus.PASSED, new SeverityCounts(0, 0, 0, 0), List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void looksUpCategoriesThatWereInspected() {
        ReportCard card = new ReportCard(List.of(SECURITY_PASSED), Score.PERFECT, GateDecision.pass(), List.of());

        assertThat(card.category(Category.SECURITY)).contains(SECURITY_PASSED);
        assertThat(card.category(Category.SPRING_AI)).isEmpty();
    }

    @Test
    void isCompleteOnlyWhenEveryCheckFinished() {
        CheckProblem timedOut = new CheckProblem.TimedOut(RuleId.of("SLOW"), Duration.ofSeconds(5));

        assertThat(new ReportCard(List.of(), Score.PERFECT, GateDecision.pass(), List.of()).isComplete())
                .isTrue();
        assertThat(new ReportCard(List.of(), Score.PERFECT, GateDecision.pass(), List.of(timedOut)).isComplete())
                .isFalse();
    }
}
