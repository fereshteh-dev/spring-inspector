package io.github.fereshtehdev.springinspector.document.design;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.document.SampleProject;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Endpoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectSourcesTest {

    @TempDir
    Path project;

    @Test
    void readsTheDesignOfAProjectOnDisk() throws IOException {
        SampleProject shop = SampleProject.shop();
        for (var file : shop.files().entrySet()) {
            write(file.getKey(), file.getValue());
        }

        SystemDesign design = ProjectSources.scan(project, shop.graph());

        assertThat(design).isEqualTo(shop.design());
        assertThat(design.endpoints()).extracting(Endpoint::path).contains("/api/orders");
    }

    @Test
    void findsSettingsOfEveryModuleButNotOfTestsBuildOutputOrHiddenFolders() throws IOException {
        write("src/main/resources/application.yml", "server:\n  port: 8080\n");
        write("billing/src/main/resources/application-prod.properties", "server.port=9090\n");
        write("src/test/resources/application.properties", "server.port=1\n");
        write("target/classes/application.properties", "server.port=2\n");
        write(".idea/application.properties", "server.port=3\n");
        write("src/main/resources/messages.properties", "hello=world\n");

        assertThat(ProjectSources.settingsFiles(project))
                .extracting(SourceFile::path)
                .containsExactlyInAnyOrder(
                        Path.of("src/main/resources/application.yml"),
                        Path.of("billing/src/main/resources/application-prod.properties"));
    }

    @Test
    void skipsJavaFilesThatHaveDisappeared() {
        ImportGraph graph =
                new ImportGraph(List.of(JavaUnit.parse(Path.of("src/main/java/Gone.java"), "class Gone {}")));

        assertThat(ProjectSources.javaSources(project, graph)).isEmpty();
    }

    private void write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
