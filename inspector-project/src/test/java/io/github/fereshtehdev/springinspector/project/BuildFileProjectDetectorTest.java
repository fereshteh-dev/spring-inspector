package io.github.fereshtehdev.springinspector.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedProject;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildFileProjectDetectorTest {

    @TempDir
    Path workspace;

    private DetectedProject detect(String fixture) {
        return new BuildFileProjectDetector().detect(Fixtures.copyProject(fixture, workspace));
    }

    private static Version v(String version) {
        return Version.parse(version);
    }

    @Test
    void readsVersionsFromBootParentAndBomProperties() {
        DetectedProject project = detect("maven-boot-parent");

        assertThat(project.buildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(project.versions().java()).contains(v("25"));
        assertThat(project.versions().springBoot()).contains(v("4.1.1"));
        assertThat(project.versions().springAi()).contains(v("2.0.1"));
        assertThat(project.warnings()).isEmpty();
    }

    @Test
    void readsBootFromImportedBomWhenTheParentIsNotBoot() {
        DetectedProject project = detect("maven-bom-import");

        assertThat(project.versions().springBoot()).contains(v("3.5.6"));
        assertThat(project.versions().springAi()).isEmpty();
    }

    @Test
    void prefersTheBuildFileOverDotJavaVersion() {
        assertThat(detect("maven-bom-import").versions().java()).contains(v("21"));
    }

    @Test
    void walksModulesAndInheritsPropertiesFromTheRoot() {
        DetectedProject project = detect("maven-multi-module");

        assertThat(project.modules()).containsExactly(Path.of("app"), Path.of("lib"));
        assertThat(project.versions().java()).contains(v("24"));
        assertThat(project.versions().springBoot()).contains(v("3.4.1"));
        assertThat(project.versions().springAi()).contains(v("1.1.2"));
    }

    @Test
    void skipsModulesThatPointOutsideTheProject() {
        DetectedProject project = detect("maven-multi-module");

        assertThat(project.warnings()).singleElement().asString().contains("../outside");
    }

    @Test
    void readsJavaFromTheToolchainsPlugin() {
        assertThat(detect("maven-toolchain").versions().java()).contains(v("25"));
    }

    @Test
    void refusesDoctypesSoBuildFilesCannotPullInOtherFiles() {
        DetectedProject project = detect("maven-xxe");

        assertThat(project.versions().java()).isEmpty();
        assertThat(project.warnings()).singleElement().asString().contains("pom.xml could not be parsed");
    }

    @Test
    void readsGroovyPluginsToolchainAndExtVariablesIgnoringComments() {
        DetectedProject project = detect("gradle-groovy");

        assertThat(project.buildTool()).isEqualTo(BuildTool.GRADLE);
        assertThat(project.versions().springBoot()).contains(v("3.4.2"));
        assertThat(project.versions().java()).contains(v("21"));
        assertThat(project.versions().springAi()).contains(v("1.0.0"));
        assertThat(project.modules()).containsExactly(Path.of("web"), Path.of("core"));
    }

    @Test
    void readsKotlinScriptsAndTheVersionCatalog() {
        DetectedProject project = detect("gradle-kotlin-catalog");

        assertThat(project.versions().springBoot()).contains(v("4.0.2"));
        assertThat(project.versions().springAi()).contains(v("2.0.0"));
        assertThat(project.versions().java()).contains(v("25"));
        assertThat(project.modules()).containsExactly(Path.of("api"), Path.of("services", "billing"));
    }

    @Test
    void resolvesGradlePropertiesAndPrefersReleaseOverSourceCompatibility() {
        DetectedProject project = detect("gradle-properties");

        assertThat(project.versions().springBoot()).contains(v("3.3.5"));
        assertThat(project.versions().java()).contains(v("22"));
    }

    @Test
    void fallsBackToDotJavaVersionWithoutABuildFile() {
        DetectedProject project = detect("java-version-file");

        assertThat(project.buildTool()).isEqualTo(BuildTool.UNKNOWN);
        assertThat(project.versions().java()).contains(v("25.0.1"));
        assertThat(project.versions().springBoot()).isEmpty();
    }

    @Test
    void prefersMavenWhenBothBuildToolsArePresent() throws IOException {
        Fixtures.copyProject("maven-toolchain", workspace);
        Files.writeString(workspace.resolve("build.gradle"), "plugins { id 'java' }\n");

        DetectedProject project = new BuildFileProjectDetector().detect(workspace);

        assertThat(project.buildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(project.warnings()).singleElement().asString().contains("using Maven");
    }

    @Test
    void reportsNothingForAnEmptyDirectory() {
        DetectedProject project = new BuildFileProjectDetector().detect(workspace);

        assertThat(project.buildTool()).isEqualTo(BuildTool.UNKNOWN);
        assertThat(project.versions().java()).isEmpty();
        assertThat(project.modules()).isEmpty();
    }

    @Test
    void neverExecutesTheBuild() throws IOException {
        Path marker = workspace.resolve("executed");
        Files.writeString(
                workspace.resolve("build.gradle"),
                "new File('" + marker.toString().replace('\\', '/') + "').text = 'ran'\n"
                        + "plugins { id 'org.springframework.boot' version '4.1.1' }\n");

        DetectedProject project = new BuildFileProjectDetector().detect(workspace);

        assertThat(project.versions().springBoot()).contains(v("4.1.1"));
        assertThat(marker).doesNotExist();
    }
}
