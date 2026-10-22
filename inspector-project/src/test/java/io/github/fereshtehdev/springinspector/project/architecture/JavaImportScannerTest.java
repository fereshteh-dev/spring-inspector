package io.github.fereshtehdev.springinspector.project.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaImportScannerTest {

    @TempDir
    Path project;

    @Test
    void mapsTheApplicationSourcesAndSkipsTestsBuildOutputAndHiddenFolders() throws IOException {
        write("src/main/java/com/acme/App.java", "package com.acme;\nimport com.acme.web.Api;\nclass App {}\n");
        write("module/src/main/java/com/acme/web/Api.java", "package com.acme.web;\nclass Api {}\n");
        write("src/main/java/com/acme/package-info.java", "package com.acme;\n");
        write("src/test/java/com/acme/AppTest.java", "package com.acme;\nclass AppTest {}\n");
        write("target/generated-sources/Gen.java", "class Gen {}\n");
        write(".idea/Ignored.java", "class Ignored {}\n");
        write("docs/test/Example.java", "package docs;\nclass Example {}\n");

        ImportGraph graph = JavaImportScanner.scan(project);

        assertThat(graph.units())
                .extracting(JavaUnit::qualifiedName)
                .containsExactlyInAnyOrder("com.acme.App", "com.acme.web.Api", "docs.Example");
        assertThat(graph.unit("com.acme.App"))
                .hasValueSatisfying(
                        app -> assertThat(app.file()).isEqualTo(Path.of("src/main/java/com/acme/App.java")));
    }

    @Test
    void returnsNothingForAProjectWithoutJava() {
        assertThat(JavaImportScanner.scan(project).units()).isEmpty();
    }

    private void write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
