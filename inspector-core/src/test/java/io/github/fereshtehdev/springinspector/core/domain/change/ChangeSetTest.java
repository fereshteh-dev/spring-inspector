package io.github.fereshtehdev.springinspector.core.domain.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeSetTest {

    private static final String APP = "src/main/java/App.java";

    private static FileChange hunks(String path, Hunk... hunks) {
        return new FileChange(Path.of(path), ChangedLines.hunks(List.of(hunks)));
    }

    @Test
    void treatsEveryLineOfAWholeFileChangeAsChanged() {
        ChangeSet changes = ChangeSet.of(FileChange.entireFile(APP));

        assertThat(changes.touches(Location.of(APP, LineRange.line(500)))).isTrue();
    }

    @Test
    void touchesOnlyLinesInsideAHunk() {
        ChangeSet changes = ChangeSet.of(hunks(APP, Hunk.lines(3, 5), Hunk.lines(20, 20)));

        assertThat(changes.touches(Location.of(APP, LineRange.of(1, 3)))).isTrue();
        assertThat(changes.touches(Location.of(APP, LineRange.line(20)))).isTrue();
        assertThat(changes.touches(Location.of(APP, LineRange.of(6, 19)))).isFalse();
    }

    @Test
    void doesNotTouchFilesOutsideTheChange() {
        ChangeSet changes = ChangeSet.of(FileChange.entireFile(APP));

        assertThat(changes.touches(Location.of("src/main/java/Other.java", LineRange.line(1))))
                .isFalse();
    }

    @Test
    void findsFilesByNormalisedPath() {
        ChangeSet changes = ChangeSet.of(FileChange.entireFile(APP));

        assertThat(changes.find(Path.of("src/main/./java/App.java"))).isPresent();
        assertThat(changes.find(Path.of("src/main/java/Missing.java"))).isEmpty();
    }

    @Test
    void rejectsTheSameFileTwice() {
        assertThatThrownBy(() -> ChangeSet.of(
                        FileChange.entireFile(APP), FileChange.entireFile("src/main/java/../java/App.java")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void rejectsAChangedFileWithoutHunks() {
        assertThatThrownBy(() -> ChangedLines.hunks(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsChangesOutsideTheProject() {
        assertThatThrownBy(() -> FileChange.entireFile("../other/App.java"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cutsSnippetsByLine() {
        SourceFile source = new SourceFile(Path.of(APP), "one\ntwo\nthree\n");

        assertThat(source.snippet(LineRange.line(2))).isEqualTo("two");
        assertThat(source.snippet(LineRange.of(2, 3))).isEqualTo("two\nthree");
        assertThat(source.snippet(LineRange.of(3, 10))).isEqualTo("three");
        assertThat(source.snippet(LineRange.line(9))).isEmpty();
    }
}
