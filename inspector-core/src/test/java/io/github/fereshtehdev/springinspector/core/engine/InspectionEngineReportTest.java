package io.github.fereshtehdev.springinspector.core.engine;

import static io.github.fereshtehdev.springinspector.core.testing.FindingBuilder.aFinding;
import static io.github.fereshtehdev.springinspector.core.testing.Inputs.APP_FILE;
import static io.github.fereshtehdev.springinspector.core.testing.Inputs.anInput;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangedLines;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.change.Hunk;
import io.github.fereshtehdev.springinspector.core.domain.report.CategoryResult;
import io.github.fereshtehdev.springinspector.core.domain.report.CategoryStatus;
import io.github.fereshtehdev.springinspector.core.domain.report.GateDecision;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.core.testing.FakeCheck;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class InspectionEngineReportTest {

    private static ReportCard inspect(InspectionInput input, Finding... findings) {
        return new InspectionEngine(List.of(FakeCheck.reporting("CHECK", Category.SECURITY, findings))).inspect(input);
    }

    private static ReportCard inspect(Finding... findings) {
        return inspect(anInput().build(), findings);
    }

    private static List<String> ruleIdsIn(CategoryResult result) {
        return result.findings().stream().map(f -> f.ruleId().value()).toList();
    }

    @Test
    void keepsOnlyFindingsOnChangedLinesByDefault() {
        ChangeSet lines10To12 =
                ChangeSet.of(new FileChange(Path.of(APP_FILE), ChangedLines.hunks(List.of(Hunk.lines(10, 12)))));

        ReportCard card = inspect(
                anInput().withChanges(lines10To12).build(),
                aFinding().rule("ON-CHANGE").at(APP_FILE, 11).build(),
                aFinding().rule("OFF-CHANGE").at(APP_FILE, 30).build(),
                aFinding()
                        .rule("OTHER-FILE")
                        .at("src/main/java/Untouched.java", 11)
                        .build());

        assertThat(ruleIdsIn(card.category(Category.SECURITY).orElseThrow())).containsExactly("ON-CHANGE");
    }

    @Test
    void keepsFindingsOutsideTheChangeWhenAllLinesAreRequested() {
        ReportCard card = inspect(
                anInput().reportingAllLines().build(),
                aFinding()
                        .rule("OTHER-FILE")
                        .at("src/main/java/Untouched.java", 3)
                        .build());

        assertThat(ruleIdsIn(card.category(Category.SECURITY).orElseThrow())).containsExactly("OTHER-FILE");
    }

    @Test
    void ranksBySeverityThenConfidenceThenSourceThenLocation() {
        ReportCard card = inspect(
                aFinding().rule("MINOR").severity(Severity.MINOR).build(),
                aFinding()
                        .rule("MAJOR-LOW")
                        .severity(Severity.MAJOR)
                        .confidence(Confidence.LOW)
                        .build(),
                aFinding()
                        .rule("MAJOR-AI")
                        .severity(Severity.MAJOR)
                        .source(FindingSource.AI)
                        .build(),
                aFinding()
                        .rule("MAJOR-L9")
                        .severity(Severity.MAJOR)
                        .at(APP_FILE, 9)
                        .build(),
                aFinding()
                        .rule("MAJOR-L2")
                        .severity(Severity.MAJOR)
                        .at(APP_FILE, 2)
                        .build(),
                aFinding().rule("BLOCKER").severity(Severity.BLOCKER).build());

        assertThat(ruleIdsIn(card.category(Category.SECURITY).orElseThrow()))
                .containsExactly("BLOCKER", "MAJOR-L2", "MAJOR-L9", "MAJOR-AI", "MAJOR-LOW", "MINOR");
    }

    @Test
    void reportsTheSameRuleAtTheSameLocationOnce() {
        Finding finding = aFinding().rule("DUP").build();

        ReportCard card = inspect(finding, finding);

        assertThat(card.category(Category.SECURITY).orElseThrow().counts().total())
                .isOne();
    }

    @Test
    void capsShownFindingsPerCategoryButCountsAll() {
        ReportCard card = inspect(
                anInput().showingAtMost(2).build(),
                aFinding().rule("A").severity(Severity.MINOR).build(),
                aFinding().rule("B").severity(Severity.BLOCKER).build(),
                aFinding().rule("C").severity(Severity.MAJOR).build());

        CategoryResult security = card.category(Category.SECURITY).orElseThrow();
        assertThat(ruleIdsIn(security)).containsExactly("B", "C");
        assertThat(security.hiddenFindings()).isOne();
        assertThat(security.counts().total()).isEqualTo(3);
    }

    @Test
    void failsCategoryWithMajorOrBlockerFindings() {
        ReportCard card = inspect(aFinding().severity(Severity.MAJOR).build());

        assertThat(card.category(Category.SECURITY).orElseThrow().status()).isEqualTo(CategoryStatus.FAILED);
    }

    @Test
    void givesHintsForCategoryWithOnlyMinorOrInfoFindings() {
        ReportCard card = inspect(
                aFinding().rule("A").severity(Severity.MINOR).build(),
                aFinding().rule("B").severity(Severity.INFO).build());

        assertThat(card.category(Category.SECURITY).orElseThrow().status()).isEqualTo(CategoryStatus.HINTS);
    }

    @Test
    void passesInspectedCategoryWithoutFindings() {
        ReportCard card = inspect();

        assertThat(card.categories()).singleElement().satisfies(result -> {
            assertThat(result.category()).isEqualTo(Category.SECURITY);
            assertThat(result.status()).isEqualTo(CategoryStatus.PASSED);
        });
    }

    @Test
    void listsCategoriesInCatalogueOrder() {
        ReportCard card = new InspectionEngine(List.of(
                        FakeCheck.reporting("ARCH", Category.ARCHITECTURE),
                        FakeCheck.reporting("SEC", Category.SECURITY),
                        FakeCheck.reporting("BOOT", Category.SPRING_BOOT)))
                .inspect(anInput().build());

        assertThat(card.categories())
                .extracting(CategoryResult::category)
                .containsExactly(Category.SECURITY, Category.SPRING_BOOT, Category.ARCHITECTURE);
    }

    @Test
    void reportsCategoryOfFindingEvenWhenItDiffersFromTheCheck() {
        ReportCard card = inspect(aFinding().category(Category.MIGRATION).build());

        assertThat(card.categories())
                .extracting(CategoryResult::category)
                .containsExactly(Category.SECURITY, Category.MIGRATION);
    }

    @Test
    void appliesConfiguredSeverityOverrides() {
        ReportCard card = inspect(
                anInput().overridingSeverity("RAISED", Severity.BLOCKER).build(),
                aFinding().rule("RAISED").severity(Severity.MINOR).build());

        assertThat(card.category(Category.SECURITY).orElseThrow().counts().blocker())
                .isOne();
        assertThat(card.gate().blocks()).isTrue();
    }

    @Test
    void neverOverridesSeverityOfAiFindings() {
        ReportCard card = inspect(
                anInput().overridingSeverity("AI-1", Severity.BLOCKER).build(),
                aFinding()
                        .rule("AI-1")
                        .severity(Severity.MINOR)
                        .source(FindingSource.AI)
                        .build());

        assertThat(card.category(Category.SECURITY).orElseThrow().counts().minor())
                .isOne();
    }

    @Test
    void scoresAPerfectHundredWithoutFindings() {
        assertThat(inspect().score().value()).isEqualTo(100);
    }

    @Test
    void deductsPointsBySeverity() {
        ReportCard card = inspect(
                aFinding().rule("B").severity(Severity.BLOCKER).build(),
                aFinding().rule("MA").severity(Severity.MAJOR).build(),
                aFinding().rule("MI").severity(Severity.MINOR).build(),
                aFinding().rule("I").severity(Severity.INFO).build());

        assertThat(card.score().value()).isEqualTo(100 - 20 - 8 - 2);
    }

    @Test
    void neverScoresBelowZero() {
        Finding[] blockers = new Finding[6];
        for (int i = 0; i < blockers.length; i++) {
            blockers[i] = aFinding().rule("B" + i).severity(Severity.BLOCKER).build();
        }

        assertThat(inspect(blockers).score().value()).isZero();
    }

    @Test
    void leavesAiFindingsOutOfTheScore() {
        ReportCard card = inspect(
                aFinding().severity(Severity.MAJOR).source(FindingSource.AI).build());

        assertThat(card.score().value()).isEqualTo(100);
    }

    @Test
    void letsTheCommitThroughWhenNothingReachesTheGateSeverity() {
        ReportCard card = inspect(aFinding().severity(Severity.MAJOR).build());

        assertThat(card.gate()).isEqualTo(GateDecision.pass());
    }

    @Test
    void blocksOnBlockerFindingsByDefaultAndSaysWhy() {
        ReportCard card = inspect(aFinding()
                .rule("SEC-001")
                .severity(Severity.BLOCKER)
                .at(APP_FILE, 4)
                .build());

        assertThat(card.gate())
                .isEqualTo(new GateDecision.Block(List.of("BLOCKER SEC-001 at src/main/java/App.java:4")));
    }

    @Test
    void blocksOnLowerSeverityWhenConfigured() {
        ReportCard card = inspect(
                anInput().blockingOn(Severity.MAJOR).build(),
                aFinding().severity(Severity.MAJOR).build());

        assertThat(card.gate().blocks()).isTrue();
    }

    @Test
    void neverBlocksOnAiFindings() {
        ReportCard card = inspect(
                anInput().blockingOn(Severity.MINOR).build(),
                aFinding().severity(Severity.MAJOR).source(FindingSource.AI).build());

        assertThat(card.gate().blocks()).isFalse();
    }

    @Test
    void blocksWhenScoreFallsBelowTheConfiguredMinimum() {
        ReportCard card = inspect(
                anInput().requiringMinimumScore(95).build(),
                aFinding().severity(Severity.MAJOR).build());

        assertThat(card.gate()).isEqualTo(new GateDecision.Block(List.of("Score 92 is below the minimum of 95")));
    }

    @Test
    void passesWhenScoreMeetsTheConfiguredMinimum() {
        ReportCard card = inspect(
                anInput().requiringMinimumScore(92).build(),
                aFinding().severity(Severity.MAJOR).build());

        assertThat(card.gate().blocks()).isFalse();
    }
}
