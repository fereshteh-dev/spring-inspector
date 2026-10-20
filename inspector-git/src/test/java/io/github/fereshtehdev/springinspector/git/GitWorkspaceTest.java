package io.github.fereshtehdev.springinspector.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.fereshtehdev.springinspector.core.domain.change.ChangeScope;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangedLines;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.change.Hunk;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.port.CollectedChanges;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(IsolatedGitConfig.class)
class GitWorkspaceTest {

    private static final String APP = "src/main/java/App.java";
    private static final String FIVE_LINES = "a\nb\nc\nd\ne\n";

    @TempDir
    Path temp;

    TestRepo repo;

    @BeforeEach
    void createRepository() throws IOException {
        repo = TestRepo.init(Files.createDirectory(temp.resolve("repo")));
    }

    @AfterEach
    void closeRepository() {
        repo.close();
    }

    private CollectedChanges collect(ChangeScope scope) {
        return collect(repo.root(), repo.environment(), scope);
    }

    private static CollectedChanges collect(Path projectRoot, Map<String, String> environment, ChangeScope scope) {
        try (GitWorkspace workspace = GitWorkspace.open(projectRoot, environment)) {
            return workspace.collect(scope);
        }
    }

    private static String content(CollectedChanges changes, String path) {
        return changes.sources().read(Path.of(path)).map(SourceFile::content).orElseThrow();
    }

    private static FileChange hunks(String path, Hunk... hunks) {
        return new FileChange(Path.of(path), ChangedLines.hunks(List.of(hunks)));
    }

    @Nested
    class Staged {

        @Test
        void reportsANewlyStagedFileAsEntirelyChanged() {
            repo.writeAndStage("README.md", "hello\n").commit("init");
            repo.writeAndStage(APP, "class App {}\n");

            assertThat(collect(ChangeScope.staged()).changeSet()).isEqualTo(ChangeSet.of(FileChange.entireFile(APP)));
        }

        @Test
        void reportsOnlyTheChangedLinesOfAModifiedFile() {
            repo.writeAndStage(APP, FIVE_LINES).commit("init");
            repo.writeAndStage(APP, "a\nb\nC\nd\ne\nf\n");

            assertThat(collect(ChangeScope.staged()).changeSet())
                    .isEqualTo(ChangeSet.of(hunks(APP, Hunk.lines(3, 3), Hunk.lines(6, 6))));
        }

        @Test
        void readsTheStagedVersionRatherThanTheWorkingTree() {
            repo.writeAndStage(APP, "staged\n").write(APP, "not staged yet\n");

            assertThat(content(collect(ChangeScope.staged()), APP)).isEqualTo("staged\n");
        }

        @Test
        void ignoresFilesThatAreNotStaged() {
            repo.writeAndStage("README.md", "hello\n").commit("init");
            repo.write(APP, "class App {}\n");

            assertThat(collect(ChangeScope.staged()).changeSet().files()).isEmpty();
        }

        @Test
        void leavesOutDeletedFilesAndPureDeletions() {
            repo.writeAndStage(APP, FIVE_LINES)
                    .writeAndStage("Gone.java", "x\n")
                    .commit("init");
            repo.remove("Gone.java").writeAndStage(APP, "a\nb\nd\ne\n");

            assertThat(collect(ChangeScope.staged()).changeSet().files()).isEmpty();
        }

        @Test
        void reportsARenamedFileUnderItsNewPathWithOnlyTheEditedLines() {
            String body = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\n";
            repo.writeAndStage("Old.java", body).commit("init");
            repo.remove("Old.java").writeAndStage("New.java", body.replace("five", "FIVE"));

            assertThat(collect(ChangeScope.staged()).changeSet())
                    .isEqualTo(ChangeSet.of(hunks("New.java", Hunk.lines(5, 5))));
        }

        @Test
        void skipsBinaryFiles() {
            repo.writeBytes("logo.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0, 1})
                    .stage("logo.png");

            assertThat(collect(ChangeScope.staged()).changeSet().files()).isEmpty();
        }

        @Test
        void worksBeforeTheFirstCommit() {
            repo.writeAndStage(APP, "class App {}\n");

            assertThat(collect(ChangeScope.staged()).changeSet()).isEqualTo(ChangeSet.of(FileChange.entireFile(APP)));
        }

        @Test
        void usesTheIndexFileGitPassesToHooks() throws IOException {
            repo.writeAndStage(APP, "first\n");
            Path gitDir = repo.root().resolve(".git");
            Files.copy(gitDir.resolve("index"), gitDir.resolve("partial-commit-index"));
            repo.writeAndStage(APP, "second\n");
            Map<String, String> hookEnvironment = Map.of(
                    Repositories.CEILING_VARIABLE,
                    Objects.requireNonNull(repo.root().getParent()).toString(),
                    Repositories.INDEX_FILE_VARIABLE,
                    ".git/partial-commit-index");

            assertThat(content(collect(repo.root(), hookEnvironment, ChangeScope.staged()), APP))
                    .isEqualTo("first\n");
        }

        @Test
        void returnsNothingForFilesThatAreNotInTheIndex() {
            repo.writeAndStage(APP, "x\n");

            assertThat(collect(ChangeScope.staged()).sources().read(Path.of("Missing.java")))
                    .isEmpty();
        }
    }

    @Nested
    class SubdirectoryProject {

        @Test
        void reportsPathsRelativeToTheProjectAndIgnoresTheRestOfTheRepository() {
            repo.writeAndStage("service/" + APP, "class App {}\n")
                    .writeAndStage("other/Other.java", "class Other {}\n");

            CollectedChanges changes =
                    collect(repo.root().resolve("service"), repo.environment(), ChangeScope.staged());

            assertThat(changes.changeSet()).isEqualTo(ChangeSet.of(FileChange.entireFile(APP)));
            assertThat(content(changes, APP)).isEqualTo("class App {}\n");
        }

        @Test
        void listsOnlyFilesInsideTheProjectForAll() {
            repo.write("service/" + APP, "class App {}\n").write("other/Other.java", "class Other {}\n");

            CollectedChanges changes = collect(repo.root().resolve("service"), repo.environment(), ChangeScope.all());

            assertThat(changes.changeSet()).isEqualTo(ChangeSet.of(FileChange.entireFile(APP)));
        }
    }

    @Nested
    class AgainstBase {

        @Test
        void reportsChangesMadeSinceTheBranchLeftTheBase() {
            repo.writeAndStage(APP, FIVE_LINES).commit("init");
            repo.newBranch("feature").writeAndStage(APP, "a\nB\nc\nd\ne\n").commit("feature work");
            repo.checkout("main")
                    .writeAndStage("Later.java", "class Later {}\n")
                    .commit("main moved on");
            repo.checkout("feature");

            CollectedChanges changes = collect(ChangeScope.againstBase("main"));

            assertThat(changes.changeSet()).isEqualTo(ChangeSet.of(hunks(APP, Hunk.lines(2, 2))));
            assertThat(content(changes, APP)).isEqualTo("a\nB\nc\nd\ne\n");
            assertThat(changes.sources().read(Path.of("Later.java"))).isEmpty();
        }

        @Test
        void rejectsAnUnknownBaseRef() {
            repo.writeAndStage(APP, FIVE_LINES).commit("init");

            assertThatThrownBy(() -> collect(ChangeScope.againstBase("does-not-exist")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does-not-exist");
        }

        @Test
        void rejectsHistoriesWithoutACommonAncestor() {
            repo.writeAndStage(APP, FIVE_LINES).commit("init");
            repo.orphanBranch("unrelated").writeAndStage("Other.java", "x\n").commit("unrelated root");

            assertThatThrownBy(() -> collect(ChangeScope.againstBase("main")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no common ancestor");
        }

        @Test
        void rejectsComparingBeforeTheFirstCommitOnTheBranch() {
            repo.writeAndStage(APP, FIVE_LINES).commit("init");
            repo.orphanBranch("fresh");

            assertThatThrownBy(() -> collect(ChangeScope.againstBase("main")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HEAD has no commits");
        }
    }

    @Nested
    class All {

        @Test
        void listsTrackedAndUntrackedFilesButNotIgnoredOnes() {
            repo.writeAndStage(".gitignore", "target/\n")
                    .writeAndStage(APP, "class App {}\n")
                    .commit("init");
            repo.write("src/main/java/New.java", "class New {}\n").write("target/App.class", "compiled");

            CollectedChanges changes = collect(ChangeScope.all());

            assertThat(changes.changeSet().files())
                    .extracting(FileChange::path)
                    .containsExactlyInAnyOrder(Path.of(".gitignore"), Path.of(APP), Path.of("src/main/java/New.java"));
            assertThat(content(changes, "src/main/java/New.java")).isEqualTo("class New {}\n");
        }

        @Test
        void readsSourcesFromTheWorkingTreeButNeverThroughLinksLeavingTheProject() throws IOException {
            Path secret = Files.writeString(temp.resolve("secret.txt"), "token");
            try {
                Files.createSymbolicLink(repo.root().resolve("leak.txt"), secret);
            } catch (UnsupportedOperationException | IOException e) {
                assumeTrue(false, "symbolic links are not available: " + e.getMessage());
            }

            CollectedChanges changes = collect(ChangeScope.all());

            assertThat(changes.changeSet().files()).isEmpty();
            assertThat(changes.sources().read(Path.of("leak.txt"))).isEmpty();
        }

        @Test
        void returnsNothingForMissingWorkingTreeFiles() {
            assertThat(collect(ChangeScope.all()).sources().read(Path.of("Missing.java")))
                    .isEmpty();
        }
    }

    @Nested
    class Opening {

        @Test
        void rejectsADirectoryOutsideAnyRepository() throws IOException {
            Path plain = Files.createDirectory(temp.resolve("plain"));
            Map<String, String> ceiling = Map.of(Repositories.CEILING_VARIABLE, temp.toString());

            assertThatThrownBy(() -> GitWorkspace.open(plain, ceiling))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not inside a git repository");
        }

        @Test
        void rejectsAMissingProjectDirectory() {
            assertThatThrownBy(() -> GitWorkspace.open(temp.resolve("missing"), repo.environment()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not exist");
        }
    }
}
