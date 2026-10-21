package io.github.fereshtehdev.springinspector.intellij.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.fereshtehdev.springinspector.intellij.report.Report;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the command line the plugin bundles, the way the plugin does, on a small project. */
class BundledCommandLineTest {

    static final Path JAR = Path.of("..", "inspector-cli", "target", "spring-inspector.jar");

    @TempDir
    Path temp;

    @Test
    void reportsTheProjectsFindingsToThePlugin() throws IOException, InterruptedException {
        assumeTrue(Files.isRegularFile(JAR), "build the command line first with ./mvnw package");
        assumeTrue(Runtime.version().feature() >= JavaRuntimes.REQUIRED_FEATURE, "needs Java 25 to run the jar");
        Path project = Files.createDirectories(temp.resolve("project"));
        Files.writeString(project.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");
        Path source = Files.createDirectories(project.resolve("src/main/java/demo")).resolve("Orders.java");
        Files.writeString(source, """
                package demo;

                import java.sql.Connection;
                import java.sql.ResultSet;
                import java.sql.SQLException;

                class Orders {
                    ResultSet byId(Connection connection, String id) throws SQLException {
                        return connection.createStatement().executeQuery("SELECT * FROM orders WHERE id = " + id);
                    }
                }
                """);
        Path java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        InspectorCommand command = new InspectorCommand(java, JAR.toAbsolutePath(), project, InspectionScope.PROJECT);

        Process process = new ProcessBuilder(command.arguments())
                .directory(project.toFile())
                .redirectError(temp.resolve("stderr.txt").toFile())
                .start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(2, TimeUnit.MINUTES)).isTrue();
        RunOutcome outcome =
                RunOutcome.of(process.exitValue(), stdout, Files.readString(temp.resolve("stderr.txt")));

        assertThat(outcome).isInstanceOfSatisfying(RunOutcome.Completed.class, completed -> {
            Report report = completed.report();
            assertThat(report.blocks()).isTrue();
            List<Finding> findings = report.categories().stream()
                    .flatMap(category -> category.findings().stream())
                    .toList();
            assertThat(findings)
                    .anySatisfy(finding -> {
                        assertThat(finding.ruleId()).isEqualTo("security.sql-concatenation");
                        assertThat(finding.file()).isEqualTo("src/main/java/demo/Orders.java");
                        assertThat(finding.startLine()).isEqualTo(9);
                    });
        });
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }
}
