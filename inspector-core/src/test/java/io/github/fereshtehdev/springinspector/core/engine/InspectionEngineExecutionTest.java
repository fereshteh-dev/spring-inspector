package io.github.fereshtehdev.springinspector.core.engine;

import static io.github.fereshtehdev.springinspector.core.testing.FindingBuilder.aFinding;
import static io.github.fereshtehdev.springinspector.core.testing.Inputs.anInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedVersions;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.project.VersionRange;
import io.github.fereshtehdev.springinspector.core.domain.report.CheckProblem;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.testing.FakeCheck;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class InspectionEngineExecutionTest {

    private static final Applicability SPRING_AI_2 =
            Applicability.always().requiringSpringAi(VersionRange.between(Version.of(2, 0, 0), Version.of(3, 0, 0)));

    @Test
    void runsChecksConcurrently() {
        CyclicBarrier bothStarted = new CyclicBarrier(2);
        FakeCheck first = FakeCheck.behaving("A", Category.SECURITY, input -> {
            bothStarted.await(2, TimeUnit.SECONDS);
            return List.of();
        });
        FakeCheck second = FakeCheck.behaving("B", Category.SECURITY, input -> {
            bothStarted.await(2, TimeUnit.SECONDS);
            return List.of();
        });

        ReportCard card =
                new InspectionEngine(List.of(first, second)).inspect(anInput().build());

        assertThat(card.problems()).isEmpty();
    }

    @Test
    void runsChecksOnVirtualThreads() {
        AtomicBoolean virtual = new AtomicBoolean();
        FakeCheck check = FakeCheck.behaving("A", Category.SECURITY, input -> {
            virtual.set(Thread.currentThread().isVirtual());
            return List.of();
        });

        new InspectionEngine(List.of(check)).inspect(anInput().build());

        assertThat(virtual).isTrue();
    }

    @Test
    void reportsCheckThatExceedsTheTimeoutAndKeepsOtherFindings() {
        FakeCheck slow = FakeCheck.behaving("SLOW", Category.SECURITY, input -> {
            Thread.sleep(Duration.ofSeconds(10));
            return List.of();
        });
        FakeCheck fast = FakeCheck.reporting(
                "FAST", Category.SECURITY, aFinding().rule("FAST").build());

        ReportCard card = new InspectionEngine(List.of(slow, fast))
                .inspect(anInput().withCheckTimeout(Duration.ofMillis(200)).build());

        assertThat(card.problems())
                .containsExactly(new CheckProblem.TimedOut(RuleId.of("SLOW"), Duration.ofMillis(200)));
        assertThat(card.category(Category.SECURITY).orElseThrow().findings())
                .extracting(finding -> finding.ruleId().value())
                .containsExactly("FAST");
        assertThat(card.isComplete()).isFalse();
    }

    @Test
    void returnsOnTimeEvenWhenCheckIgnoresInterruption() {
        AtomicBoolean release = new AtomicBoolean();
        Check stubborn = FakeCheck.behaving("STUBBORN", Category.SECURITY, input -> {
            while (!release.get()) {
                Thread.onSpinWait();
            }
            return List.of();
        });
        try {
            long start = System.nanoTime();

            ReportCard card = new InspectionEngine(List.of(stubborn))
                    .inspect(anInput().withCheckTimeout(Duration.ofMillis(200)).build());

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
            assertThat(card.problems()).singleElement().isInstanceOf(CheckProblem.TimedOut.class);
        } finally {
            release.set(true);
        }
    }

    @Test
    void reportsFailingCheckWithItsErrorAndKeepsGoing() {
        FakeCheck broken = FakeCheck.behaving("BROKEN", Category.SECURITY, input -> {
            throw new IllegalStateException("boom");
        });
        FakeCheck silent = FakeCheck.behaving("SILENT", Category.SECURITY, input -> {
            throw new UnsupportedOperationException();
        });
        FakeCheck healthy = FakeCheck.reporting(
                "OK", Category.SECURITY, aFinding().rule("OK").build());

        ReportCard card = new InspectionEngine(List.of(broken, silent, healthy))
                .inspect(anInput().build());

        assertThat(card.problems())
                .containsExactly(
                        new CheckProblem.Failed(RuleId.of("BROKEN"), "IllegalStateException: boom"),
                        new CheckProblem.Failed(RuleId.of("SILENT"), "UnsupportedOperationException"));
        assertThat(card.category(Category.SECURITY).orElseThrow().counts().total())
                .isEqualTo(1);
    }

    @Test
    void stopsAndKeepsTheInterruptFlagWhenTheCallerIsInterrupted() {
        FakeCheck slow = FakeCheck.behaving("SLOW", Category.SECURITY, input -> {
            Thread.sleep(Duration.ofSeconds(10));
            return List.of();
        });
        InspectionEngine engine = new InspectionEngine(List.of(slow));

        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> engine.inspect(anInput().build())).isInstanceOf(CancellationException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void rejectsTwoChecksWithTheSameRuleId() {
        List<Check> checks =
                List.of(FakeCheck.reporting("DUP", Category.SECURITY), FakeCheck.reporting("DUP", Category.CLEAN_CODE));

        assertThatThrownBy(() -> new InspectionEngine(checks))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DUP");
    }

    @Test
    void skipsDisabledRules() {
        FakeCheck disabled = FakeCheck.reporting("OFF", Category.SECURITY);

        ReportCard card = new InspectionEngine(List.of(disabled))
                .inspect(anInput().disablingRule("OFF").build());

        assertThat(disabled.invocations()).isZero();
        assertThat(card.categories()).isEmpty();
    }

    @Test
    void skipsChecksInDisabledCategories() {
        FakeCheck cleanCode = FakeCheck.reporting("CC", Category.CLEAN_CODE);
        FakeCheck security = FakeCheck.reporting("SEC", Category.SECURITY);

        new InspectionEngine(List.of(cleanCode, security))
                .inspect(anInput().disablingCategory(Category.CLEAN_CODE).build());

        assertThat(cleanCode.invocations()).isZero();
        assertThat(security.invocations()).isOne();
    }

    @Test
    void skipsChecksWhoseVersionRangeDoesNotApply() {
        FakeCheck springAiCheck =
                FakeCheck.reporting("AI-1", Category.SPRING_AI).applicableWhen(SPRING_AI_2);

        new InspectionEngine(List.of(springAiCheck))
                .inspect(anInput().withVersions(DetectedVersions.none()).build());

        assertThat(springAiCheck.invocations()).isZero();
    }

    @Test
    void runsChecksWhoseVersionRangeApplies() {
        FakeCheck springAiCheck =
                FakeCheck.reporting("AI-1", Category.SPRING_AI).applicableWhen(SPRING_AI_2);
        DetectedVersions springAi201 =
                new DetectedVersions(Optional.empty(), Optional.empty(), Optional.of(Version.parse("2.0.1")));

        new InspectionEngine(List.of(springAiCheck))
                .inspect(anInput().withVersions(springAi201).build());

        assertThat(springAiCheck.invocations()).isOne();
    }
}
