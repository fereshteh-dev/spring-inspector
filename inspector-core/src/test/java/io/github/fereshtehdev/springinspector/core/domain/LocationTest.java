package io.github.fereshtehdev.springinspector.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocationTest {

    @Test
    void rejectsLinesBeforeTheFirst() {
        assertThatThrownBy(() -> LineRange.of(0, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRangeEndingBeforeItStarts() {
        assertThatThrownBy(() -> LineRange.of(5, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsOverlappingRanges() {
        LineRange fiveToEight = LineRange.of(5, 8);

        assertThat(fiveToEight.overlaps(LineRange.of(8, 10))).isTrue();
        assertThat(fiveToEight.overlaps(LineRange.of(1, 5))).isTrue();
        assertThat(fiveToEight.overlaps(LineRange.line(6))).isTrue();
        assertThat(fiveToEight.overlaps(LineRange.of(9, 12))).isFalse();
        assertThat(fiveToEight.overlaps(LineRange.of(1, 4))).isFalse();
    }

    @Test
    void ordersRangesByStartThenEnd() {
        assertThat(LineRange.of(2, 3)).isLessThan(LineRange.of(3, 3));
        assertThat(LineRange.of(2, 3)).isLessThan(LineRange.of(2, 4));
    }

    @Test
    void printsSingleLinesAndRanges() {
        assertThat(LineRange.line(7)).hasToString("7");
        assertThat(LineRange.of(7, 9)).hasToString("7-9");
    }

    @Test
    void rejectsAbsoluteFiles() {
        Path absolute = Path.of("src").toAbsolutePath();

        assertThatThrownBy(() -> new Location(absolute, LineRange.line(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative");
    }

    @Test
    void rejectsFilesOutsideTheProject() {
        assertThatThrownBy(() -> Location.of("src/../../secret.txt", LineRange.line(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void normalisesTheFilePath() {
        assertThat(Location.of("src/./main/../App.java", LineRange.line(1)).file())
                .isEqualTo(Path.of("src/App.java"));
    }

    @Test
    void printsFileAndLinesWithForwardSlashes() {
        assertThat(Location.of("src/main/App.java", LineRange.of(3, 4))).hasToString("src/main/App.java:3-4");
    }

    @Test
    void acceptsRuleIdsMadeOfLettersDigitsAndSeparators() {
        assertThat(RuleId.of("spring-boot.SEC_001")).hasToString("spring-boot.SEC_001");
    }

    @Test
    void rejectsRuleIdsWithSpacesOrLeadingSeparators() {
        assertThatThrownBy(() -> RuleId.of("SEC 001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuleId.of("-SEC")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuleId.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordersRuleIdsAlphabetically() {
        assertThat(RuleId.of("A-2")).isLessThan(RuleId.of("B-1"));
    }
}
