package io.github.fereshtehdev.springinspector.intellij.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunTest {

    static final String REPORT = """
            {"tool": {"name": "spring-inspector"}, "score": 100, "gate": {"blocks": false}, "categories": []}
            """;

    @TempDir
    Path temp;

    @Test
    void runsTheBundledJarWithAJsonReportInUtf8() {
        InspectorCommand command = new InspectorCommand(
                Path.of("jdk", "bin", "java"), Path.of("plugin", "cli", "spring-inspector.jar"), Path.of("project"),
                InspectionScope.STAGED);

        assertThat(command.arguments())
                .containsExactly(
                        Path.of("jdk", "bin", "java").toString(),
                        "-Dstdout.encoding=UTF-8",
                        "-Dstderr.encoding=UTF-8",
                        "-jar",
                        Path.of("plugin", "cli", "spring-inspector.jar").toString(),
                        "check",
                        "--format",
                        "json",
                        "--project",
                        "project",
                        "--staged");
        assertThat(InspectionScope.PROJECT.option()).isEqualTo("--all");
        assertThat(new InspectorCommand(
                                Path.of("java"),
                                Path.of("cli.jar"),
                                Path.of("project"),
                                InspectionScope.FILES,
                                List.of(Path.of("src", "A.java"), Path.of("pom.xml")))
                        .arguments())
                .endsWith("--files", Path.of("src", "A.java").toString(), "pom.xml");
        assertThatThrownBy(() -> new InspectorCommand(
                        Path.of("java"), Path.of("cli.jar"), Path.of("project"), InspectionScope.FILES, List.of()))
                .hasMessageContaining("Name the files");
    }

    @Test
    void passesLongFileListsThroughAnArgumentFile() {
        InspectorCommand command = new InspectorCommand(
                Path.of("java"),
                Path.of("cli.jar"),
                Path.of("project"),
                InspectionScope.FILES,
                List.of(Path.of("src", "My File.java"), Path.of("pom.xml")));

        assertThat(command.arguments(Optional.of(Path.of("files.txt"))))
                .endsWith("--files", "@files.txt")
                .doesNotContain("pom.xml");
        assertThat(command.argumentFileContent()).isEqualTo("\"src/My File.java\"\n\"pom.xml\"\n");
    }

    @Test
    void prefersTheConfiguredJavaThenTheIdesOwnWhenItIsNewEnough() throws IOException {
        Path configured = executable(temp.resolve("jdk-25"), "java.exe");
        Path ide = executable(temp.resolve("jbr"), "java.exe");

        assertThat(JavaRuntimes.find(temp.resolve("jdk-25").toString(), temp.resolve("jbr"), 21, true))
                .contains(configured);
        assertThat(JavaRuntimes.find(" " + configured + " ", temp.resolve("jbr"), 21, true))
                .contains(configured);
        assertThat(JavaRuntimes.find("", temp.resolve("jbr"), 25, true)).contains(ide);
        assertThat(JavaRuntimes.find("", temp.resolve("jbr"), 21, true)).isEmpty();
        assertThat(JavaRuntimes.find(temp.resolve("missing").toString(), temp.resolve("jbr"), 25, true))
                .isEmpty();
    }

    @Test
    void findsJavaWithoutTheWindowsSuffixElsewhere() throws IOException {
        Path java = executable(temp.resolve("jdk"), "java");

        assertThat(JavaRuntimes.find("", temp.resolve("jdk"), 26, false)).contains(java);
    }

    @Test
    void readsTheReportOfAPassedOrBlockedRunWithItsWarnings() {
        RunOutcome passed = RunOutcome.of(0, REPORT, "warning: spring-inspector.yml: unknown setting 'x'\n\n");
        RunOutcome blocked = RunOutcome.of(1, REPORT, "");

        assertThat(passed).isInstanceOfSatisfying(RunOutcome.Completed.class, completed -> {
            assertThat(completed.report().score()).isEqualTo(100);
            assertThat(completed.warnings()).containsExactly("spring-inspector.yml: unknown setting 'x'");
        });
        assertThat(blocked).isInstanceOf(RunOutcome.Completed.class);
    }

    @Test
    void explainsRunsThatEndedWithoutAReport() {
        assertThat(RunOutcome.of(2, "", "error: Unknown category 'x'"))
                .isEqualTo(new RunOutcome.Failed("Spring Inspector could not finish: error: Unknown category 'x'"));
        assertThat(RunOutcome.of(3, "", "")).isEqualTo(new RunOutcome.Failed("Spring Inspector could not finish: exit code 3"));
        assertThat(RunOutcome.of(0, "Picked up JAVA_TOOL_OPTIONS", ""))
                .isInstanceOfSatisfying(RunOutcome.Failed.class, failed -> assertThat(failed.message())
                        .startsWith("Spring Inspector's report could not be read"));
    }

    private static Path executable(Path home, String name) throws IOException {
        Path java = Files.createDirectories(home.resolve("bin")).resolve(name);
        return Files.writeString(java, "");
    }
}
