package io.github.fereshtehdev.springinspector.javamodel.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LineIndexTest {

    @Test
    void splitsLinesOnEveryKindOfLineBreakAndKeepsTheBreaks() {
        LineIndex index = LineIndex.of("a\nb\r\nc\rd");

        assertThat(index.lines()).containsExactly("a\n", "b\r\n", "c\r", "d");
    }

    @Test
    void anEmptyTextHasNoLines() {
        LineIndex index = LineIndex.of("");

        assertThat(index.lineCount()).isZero();
        assertThat(index.lineOf(0)).isZero();
    }

    @Test
    void theEndOfATextEndingWithALineBreakIsTheLineAfterTheLast() {
        assertThat(LineIndex.of("a\nb\n").lineOf(4)).isEqualTo(2);
        assertThat(LineIndex.of("a\nb").lineOf(3)).isEqualTo(1);
    }

    @Test
    void findsTheLineOfAnyOffset() {
        LineIndex index = LineIndex.of("ab\ncd\n");

        assertThat(index.lineOf(0)).isZero();
        assertThat(index.lineOf(2)).isZero();
        assertThat(index.lineOf(3)).isEqualTo(1);
    }

    @Test
    void convertsOneBasedPositionsToOffsets() {
        LineIndex index = LineIndex.of("ab\ncd\n");

        assertThat(index.offset(2, 2)).isEqualTo(4);
    }

    @Test
    void rejectsPositionsAndOffsetsOutsideTheText() {
        LineIndex index = LineIndex.of("ab\n");

        assertThatThrownBy(() -> index.offset(2, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.offset(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.lineOf(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsTheLineSeparatorInUse() {
        assertThat(LineIndex.of("a\r\nb").lineSeparator()).isEqualTo("\r\n");
        assertThat(LineIndex.of("a\nb").lineSeparator()).isEqualTo("\n");
        assertThat(LineIndex.of("a\rb").lineSeparator()).isEqualTo("\r");
        assertThat(LineIndex.of("a").lineSeparator()).isEqualTo("\n");
    }
}
