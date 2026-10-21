package io.github.fereshtehdev.springinspector.git;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.port.CollectedChanges;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderFilesTest {

    @TempDir
    Path project;

    @Test
    void listsTheProjectsFilesWithoutHiddenFoldersOrBuildOutput() throws IOException {
        write("pom.xml", "<project/>");
        write("src/main/java/demo/App.java", "class App {}");
        write("src/main/resources/application.yml", "server.port: 8080");
        write("target/classes/demo/App.class", "binary");
        write("build/libs/app.jar", "binary");
        write(".idea/workspace.xml", "<project/>");
        write("node_modules/x/index.js", "");
        write("big/Huge.java", "x".repeat(SourceLimits.MAX_SOURCE_BYTES + 1));

        CollectedChanges changes = FolderFiles.all(project);

        assertThat(changes.changeSet().files())
                .extracting(FileChange::path)
                .containsExactlyInAnyOrder(
                        Path.of("pom.xml"),
                        Path.of("src", "main", "java", "demo", "App.java"),
                        Path.of("src", "main", "resources", "application.yml"));
        assertThat(changes.sources().read(Path.of("src", "main", "java", "demo", "App.java")))
                .hasValueSatisfying(source -> assertThat(source.content()).isEqualTo("class App {}"));
    }

    @Test
    void readsOnlyTheNamedFilesThatExist() throws IOException {
        write("src/A.java", "class A {}");
        write("src/B.java", "class B {}");

        CollectedChanges changes = FolderFiles.only(
                project, List.of(Path.of("src/A.java"), Path.of("src/./A.java"), Path.of("src/Missing.java")));

        assertThat(changes.changeSet().files()).extracting(FileChange::path).containsExactly(Path.of("src", "A.java"));
    }

    @Test
    void findsNoRepositoryForAPlainFolder() throws IOException {
        Path plain = Files.createDirectory(project.resolve("plain"));

        assertThat(GitWorkspace.find(plain, Map.of(Repositories.CEILING_VARIABLE, project.toString())))
                .isEmpty();
    }

    private void write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
