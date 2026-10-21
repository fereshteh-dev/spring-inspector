package io.github.fereshtehdev.springinspector.javamodel.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatchesTest {

    private static final String TEN_LINES = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n";

    private static SourceFile file(String content) {
        return new SourceFile(Path.of("src", "A.java"), content);
    }

    private static int offsetOfLine(String text, int line) {
        return LineIndex.of(text).start(line - 1);
    }

    @Test
    void replacingOneLineProducesAHunkWithThreeLinesOfContext() {
        int start = offsetOfLine(TEN_LINES, 5);

        String diff = Patches.unifiedDiff(file(TEN_LINES), List.of(new TextEdit(start, start + 1, "five")))
                .unifiedDiff();

        assertThat(diff).isEqualTo("""
                        --- a/src/A.java
                        +++ b/src/A.java
                        @@ -2,7 +2,7 @@
                         2
                         3
                         4
                        -5
                        +five
                         6
                         7
                         8
                        """);
    }

    @Test
    void distantEditsBecomeSeparateHunksWithShiftedNewLineNumbers() {
        int second = offsetOfLine(TEN_LINES, 2);
        int ninth = offsetOfLine(TEN_LINES, 9);

        String diff = Patches.unifiedDiff(
                        file(TEN_LINES), List.of(TextEdit.insert(second, "1.5\n"), TextEdit.delete(ninth, ninth + 2)))
                .unifiedDiff();

        assertThat(diff)
                .contains("@@ -1,4 +1,5 @@\n 1\n+1.5\n 2\n 3\n 4\n")
                .contains("@@ -6,5 +7,4 @@\n 6\n 7\n 8\n-9\n 10\n");
    }

    @Test
    void closeEditsShareOneHunkWithUnchangedLinesAsContext() {
        int second = offsetOfLine(TEN_LINES, 2);
        int fourth = offsetOfLine(TEN_LINES, 4);

        String diff = Patches.unifiedDiff(
                        file(TEN_LINES),
                        List.of(new TextEdit(second, second + 1, "two"), new TextEdit(fourth, fourth + 1, "four")))
                .unifiedDiff();

        assertThat(diff).contains("@@ -1,7 +1,7 @@\n 1\n-2\n+two\n 3\n-4\n+four\n 5\n 6\n 7\n");
    }

    @Test
    void appendingToAFileWithoutFinalLineBreakMarksTheMissingBreak() {
        String text = "a\nb";

        String diff = Patches.unifiedDiff(file(text), List.of(TextEdit.insert(text.length(), "\nc\n")))
                .unifiedDiff();

        assertThat(diff).endsWith("@@ -1,2 +1,3 @@\n a\n-b\n\\ No newline at end of file\n+b\n+c\n");
    }

    @Test
    void insertingAtTheEndOfAFileThatEndsWithALineBreakAddsLines() {
        String diff = Patches.unifiedDiff(file("a\n"), List.of(TextEdit.insert(2, "b\n")))
                .unifiedDiff();

        assertThat(diff).endsWith("@@ -1 +1,2 @@\n a\n+b\n");
    }

    @Test
    void windowsLineBreaksArePreservedInRemovedAndAddedLines() {
        String text = "a\r\nb\r\n";

        String diff = Patches.unifiedDiff(file(text), List.of(new TextEdit(3, 4, "B")))
                .unifiedDiff();

        assertThat(diff).endsWith(" a\r\n-b\r\n+B\r\n");
    }

    @Test
    void editsThatChangeNothingAreRejected() {
        assertThatThrownBy(() -> Patches.unifiedDiff(file("a\n"), List.of(new TextEdit(0, 1, "a"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must change something");
    }

    @Test
    void overlappingEditsAreRejected() {
        assertThatThrownBy(() -> Patches.apply("abcdef", List.of(new TextEdit(0, 3, "x"), new TextEdit(2, 4, "y"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void editsBeyondTheTextAreRejected() {
        assertThatThrownBy(() -> Patches.apply("abc", List.of(new TextEdit(2, 9, "x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applyingEditsInAnyOrderGivesTheSameText() {
        List<TextEdit> edits = List.of(TextEdit.insert(6, "!"), new TextEdit(0, 5, "Howdy"));

        assertThat(Patches.apply("Hello world", edits)).isEqualTo("Howdy !world");
    }

    @Test
    void negativeOrBackwardRangesAreNotEdits() {
        assertThatThrownBy(() -> new TextEdit(-1, 0, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextEdit(3, 2, "")).isInstanceOf(IllegalArgumentException.class);
    }
}
