package io.github.fereshtehdev.springinspector.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.javamodel.patch.Patches;
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UnifiedDiffsTest {

    private static String roundTrip(String original, List<TextEdit> edits) {
        Patch patch = Patches.unifiedDiff(new SourceFile(Path.of("A.java"), original), edits);
        return UnifiedDiffs.apply(original, patch);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n", "a\r\nb\r\nc\r\n", "a\nb", "x\n"})
    void applyingAGeneratedPatchGivesTheEditedText(String original) {
        List<TextEdit> edits = List.of(
                TextEdit.insert(0, "first\n"), new TextEdit(0, 1, "Z"), TextEdit.insert(original.length(), "last\n"));

        assertThat(roundTrip(original, edits)).isEqualTo(Patches.apply(original, edits));
    }

    @Test
    void removesAndAddsLinesInSeveralHunks() {
        String original = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n";
        List<TextEdit> edits = List.of(TextEdit.delete(2, 4), TextEdit.insert(24, "x\ny\n"));

        assertThat(roundTrip(original, edits)).isEqualTo(Patches.apply(original, edits));
    }

    @Test
    void refusesAPatchWhoseContextDoesNotMatch() {
        Patch patch =
                Patches.unifiedDiff(new SourceFile(Path.of("A.java"), "a\nb\n"), List.of(TextEdit.insert(2, "c")));

        assertThatThrownBy(() -> UnifiedDiffs.apply("a\nX\n", patch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void refusesTextThatIsNotAPatch() {
        assertThatThrownBy(() -> UnifiedDiffs.apply("a\n", new Patch("hello\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no file header");
        assertThatThrownBy(() -> UnifiedDiffs.apply("a\n", new Patch("--- a/A\n+++ b/A\nnot a hunk\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hunk header");
    }
}
