package io.github.fereshtehdev.springinspector.core.engine;

import static io.github.fereshtehdev.springinspector.core.testing.FindingBuilder.aFinding;
import static io.github.fereshtehdev.springinspector.core.testing.Inputs.APP_FILE;
import static io.github.fereshtehdev.springinspector.core.testing.Inputs.anInput;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.domain.report.Score;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.core.testing.FakeCheck;
import io.github.fereshtehdev.springinspector.core.testing.Inputs;
import java.util.List;
import org.junit.jupiter.api.Test;

class InspectionEngineSuppressionTest {

    private static final Finding SQL_ON_LINE_3 = aFinding()
            .rule("security.sql")
            .severity(Severity.BLOCKER)
            .at(APP_FILE, 3)
            .build();

    private static ReportCard inspect(InspectionInput input, Finding... findings) {
        return new InspectionEngine(List.of(FakeCheck.reporting("CHECK", Category.SECURITY, findings))).inspect(input);
    }

    private static Inputs withApp(String source) {
        return anInput().withSource(APP_FILE, source);
    }

    @Test
    void aSuppressionWithAReasonOnTheLineAboveSilencesTheFinding() {
        ReportCard card = inspect(withApp("""
                                class App {
                                    // inspector:ignore security.sql reason="table name comes from an enum"
                                    String q = "select " + table;
                                }
                                """).build(), SQL_ON_LINE_3);

        assertThat(card.category(Category.SECURITY).orElseThrow().findings()).isEmpty();
        assertThat(card.suppressed()).singleElement().satisfies(suppressed -> {
            assertThat(suppressed.finding()).isEqualTo(SQL_ON_LINE_3);
            assertThat(suppressed.reason()).isEqualTo("table name comes from an enum");
        });
        assertThat(card.gate().blocks()).isFalse();
        assertThat(card.score()).isEqualTo(Score.PERFECT);
    }

    @Test
    void aSuppressionAtTheEndOfTheSameLineWorksToo() {
        ReportCard card = inspect(withApp("""
                                class App {

                                    String q = "select " + table; // inspector:ignore security.sql reason="trusted"
                                }
                                """).build(), SQL_ON_LINE_3);

        assertThat(card.suppressed()).hasSize(1);
    }

    @Test
    void aSuppressionWithoutAReasonIsNotHonouredAndIsReported() {
        ReportCard card = inspect(withApp("""
                                class App {
                                    // inspector:ignore security.sql reason=" "
                                    String q = "select " + table;
                                }
                                """).build(), SQL_ON_LINE_3);

        assertThat(card.suppressed()).isEmpty();
        assertThat(card.gate().blocks()).isTrue();
        assertThat(card.suppressionsWithoutReason()).containsExactly(Location.of(APP_FILE, LineRange.line(2)));
    }

    @Test
    void aSuppressionOnlySilencesTheRuleItNames() {
        ReportCard card = inspect(withApp("""
                                class App {
                                    // inspector:ignore security.other reason="not this one"
                                    String q = "select " + table;
                                }
                                """).build(), SQL_ON_LINE_3);

        assertThat(card.suppressed()).isEmpty();
        assertThat(card.suppressionsWithoutReason()).isEmpty();
    }

    @Test
    void aSuppressionTwoLinesAboveDoesNotReach() {
        ReportCard card = inspect(withApp("""
                                // inspector:ignore security.sql reason="too far away"

                                String q = "select " + table;
                                """).build(), SQL_ON_LINE_3);

        assertThat(card.suppressed()).isEmpty();
    }

    @Test
    void findingsInFilesThatCannotBeReadAreKept() {
        ReportCard card = inspect(anInput().build(), SQL_ON_LINE_3);

        assertThat(card.category(Category.SECURITY).orElseThrow().findings()).containsExactly(SQL_ON_LINE_3);
    }

    @Test
    void ignoredFilesAreNeitherInspectedNorReported() {
        String generated = "target/generated-sources/Gen.java";
        FakeCheck check = FakeCheck.behaving(
                "CHECK",
                Category.SECURITY,
                input -> input.changeSet().files().stream()
                        .map(file -> aFinding()
                                .rule("seen")
                                .at(file.path().toString(), 1)
                                .build())
                        .toList());
        InspectionInput input = anInput()
                .withChanges(new ChangeSet(List.of(FileChange.entireFile(APP_FILE), FileChange.entireFile(generated))))
                .ignoring("**/generated-sources/**")
                .build();

        ReportCard card = new InspectionEngine(List.of(check)).inspect(input);

        assertThat(card.category(Category.SECURITY).orElseThrow().findings())
                .extracting(finding -> finding.location().file().toString().replace('\\', '/'))
                .containsExactly(APP_FILE);
    }

    @Test
    void findingsAChecksReportsInIgnoredFilesAreDropped() {
        ReportCard card = inspect(
                anInput().reportingAllLines().ignoring("src/legacy/**").build(),
                aFinding().rule("legacy").at("src/legacy/Old.java", 1).build());

        assertThat(card.category(Category.SECURITY).orElseThrow().findings()).isEmpty();
    }
}
