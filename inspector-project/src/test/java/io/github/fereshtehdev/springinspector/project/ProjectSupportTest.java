package io.github.fereshtehdev.springinspector.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectSupportTest {

    @TempDir
    Path workspace;

    @ParameterizedTest
    @CsvSource({
        "25, 25.0.0",
        "1.8, 8.0.0",
        "1_8, 8.0.0",
        "'[25,)', 25.0.0",
        "temurin-25.0.1, 25.0.1",
        "openjdk64-21.0.2, 21.0.2",
        "21.0.1+12, 21.0.1"
    })
    void normalisesJavaVersionNotations(String text, String expected) {
        assertThat(JavaVersions.parse(text)).contains(Version.parse(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "latest", "openjdk"})
    void findsNoJavaVersionInTextWithoutOne(String text) {
        assertThat(JavaVersions.parse(text)).isEmpty();
    }

    @Test
    void resolvesNestedPlaceholders() {
        Map<String, String> values = Map.of("boot", "${boot.major}.1.1", "boot.major", "4");

        assertThat(Placeholders.resolveBraced("${boot}", values)).contains("4.1.1");
    }

    @Test
    void givesUpOnUnknownOrCyclicPlaceholders() {
        assertThat(Placeholders.resolveBraced("${missing}", Map.of())).isEmpty();
        assertThat(Placeholders.resolveBraced("${a}", Map.of("a", "${a}"))).isEmpty();
        assertThat(Placeholders.resolveBraced("  ", Map.of())).isEmpty();
    }

    @Test
    void resolvesBareGroovyStyleVariables() {
        assertThat(Placeholders.resolveBracedOrBare("$springAiVersion", Map.of("springAiVersion", "2.0.1")))
                .contains("2.0.1");
    }

    @Test
    void keepsModulePathsInsideTheProject() {
        Path root = Path.of("");

        assertThat(ModulePaths.insideProject(root, "services/api")).contains(Path.of("services", "api"));
        assertThat(ModulePaths.insideProject(root, "a/../b")).contains(Path.of("b"));
        assertThat(ModulePaths.insideProject(root, "../escape")).isEmpty();
        assertThat(ModulePaths.insideProject(root, workspace.toAbsolutePath().toString()))
                .isEmpty();
        assertThat(ModulePaths.insideProject(root, " ")).isEmpty();
        assertThat(ModulePaths.insideProject(root, "bad\0name")).isEmpty();
    }

    @Test
    void readsBuildFilesInsideTheProject() throws IOException {
        Files.writeString(workspace.resolve("pom.xml"), "<project/>");
        BuildFileReader reader = new BuildFileReader(workspace);

        assertThat(reader.exists(Path.of("pom.xml"))).isTrue();
        assertThat(reader.read(Path.of("pom.xml"))).contains("<project/>");
        assertThat(reader.read(Path.of("missing.xml"))).isEmpty();
        assertThat(reader.warnings()).isEmpty();
    }

    @Test
    void skipsBuildFilesLargerThanTheLimit() throws IOException {
        Files.write(workspace.resolve("pom.xml"), new byte[(int) BuildFileReader.MAX_BYTES + 1]);
        BuildFileReader reader = new BuildFileReader(workspace);

        assertThat(reader.read(Path.of("pom.xml"))).isEmpty();
        assertThat(reader.warnings()).singleElement().asString().contains("larger than");
    }

    @Test
    void skipsBuildFilesThatLinkOutsideTheProject(@TempDir Path elsewhere) throws IOException {
        Path secret = Files.writeString(elsewhere.resolve("secret.txt"), "token");
        Path project = Files.createDirectory(workspace.resolve("project"));
        try {
            Files.createSymbolicLink(project.resolve("pom.xml"), secret);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available: " + e.getMessage());
        }
        BuildFileReader reader = new BuildFileReader(project);

        assertThat(reader.read(Path.of("pom.xml"))).isEmpty();
        assertThat(reader.warnings()).singleElement().asString().contains("outside the project");
    }
}
