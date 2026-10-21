package io.github.fereshtehdev.springinspector.project.dependencies;

import static io.github.fereshtehdev.springinspector.project.dependencies.Files2.bootJar;
import static io.github.fereshtehdev.springinspector.project.dependencies.Files2.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.project.ProjectDependencyResolver;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency.Origin;
import io.github.fereshtehdev.springinspector.project.dependencies.PackagedLibraries.Library;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackagedLibrariesTest {

    @TempDir
    Path project;

    @Test
    void readsTheLibrariesInsideBuiltApplicationArchives() {
        bootJar(
                project.resolve("target/demo-1.0.jar"),
                "BOOT-INF/lib/",
                List.of("org.springframework:spring-webmvc:6.1.5", "org.yaml:snakeyaml:1.33"));
        bootJar(project.resolve("app/build/libs/app.war"), "WEB-INF/lib/", List.of("commons-io:commons-io:2.6"));
        bootJar(project.resolve("target/original-demo-1.0.jar"), "BOOT-INF/lib/", List.of("ignored:original:1"));
        bootJar(project.resolve("app/build/libs/app-plain.jar"), "BOOT-INF/lib/", List.of("ignored:plain:1"));

        List<Library> libraries = PackagedLibraries.find(project, List.of(Path.of("app")));

        assertThat(libraries)
                .extracting(Library::coordinate, Library::version, Library::module, Library::archive)
                .containsExactly(
                        tuple(
                                "org.springframework:spring-webmvc",
                                "6.1.5",
                                Path.of(""),
                                Path.of("target", "demo-1.0.jar")),
                        tuple("org.yaml:snakeyaml", "1.33", Path.of(""), Path.of("target", "demo-1.0.jar")),
                        tuple(
                                "commons-io:commons-io",
                                "2.6",
                                Path.of("app"),
                                Path.of("app", "build", "libs", "app.war")));
    }

    @Test
    void ignoresFilesThatAreNotArchives() {
        write(project, "target/broken.jar", "not a zip");

        assertThat(PackagedLibraries.find(project, List.of())).isEmpty();
    }

    @Test
    void completesWhatTheBuildFilesLeaveOpenWithoutOverridingThem() {
        write(project, "pom.xml", """
                <project>
                    <groupId>demo</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId><version>2.2</version></dependency>
                        <dependency><groupId>commons-io</groupId><artifactId>commons-io</artifactId></dependency>
                    </dependencies>
                </project>
                """);
        bootJar(
                project.resolve("target/demo-1.0.jar"),
                "BOOT-INF/lib/",
                List.of(
                        "org.yaml:snakeyaml:1.33",
                        "commons-io:commons-io:2.6",
                        "org.springframework:spring-webmvc:6.1.5"));
        bootJar(
                project.resolve("target/second-copy.jar"),
                "BOOT-INF/lib/",
                List.of("org.springframework:spring-webmvc:6.1.5"));

        ProjectDependencies found = new ProjectDependencyResolver(LocalRepository.none())
                .resolve(Files2.project(project, BuildTool.MAVEN, List.of(), Optional.empty()));

        assertThat(found.unresolved()).isEmpty();
        assertThat(found.resolved())
                .extracting(Dependency::coordinate, Dependency::version, Dependency::origin, Dependency::line)
                .containsExactly(
                        tuple("org.yaml:snakeyaml", "2.2", Origin.DECLARED, 6),
                        tuple("commons-io:commons-io", "2.6", Origin.PACKAGED, 7),
                        tuple("org.springframework:spring-webmvc", "6.1.5", Origin.PACKAGED, 1));
        assertThat(found.resolved().get(2).declaredIn()).isEqualTo(Path.of("pom.xml"));
        assertThat(found.resolved().get(2).packagedIn()).contains(Path.of("target", "demo-1.0.jar"));
    }

    @Test
    void localRepositoryReadsOnlyPlainCoordinates() {
        Files2.cachedPom(project.resolve("m2"), "com.example", "lib", "1.0", "");
        LocalRepository repository = new LocalRepository(List.of(project.resolve("m2")), List.of());

        assertThat(repository.pom("com.example", "lib", "1.0")).isPresent();
        assertThat(repository.pom("com.example", "lib", "2.0")).isEmpty();
        assertThat(repository.pom("..", "lib", "1.0")).isEmpty();
        assertThat(repository.pom("com.example", "../lib", "1.0")).isEmpty();
        assertThat(repository.pom("com.example", "lib", "1.0/../../x")).isEmpty();
    }

    @Test
    void localRepositoryUsesTheUsualLocationsAndGradleUserHome() {
        Path home = project.resolve("home");
        Files2.cachedPom(home.resolve(".m2/repository"), "com.example", "lib", "1.0", "");
        write(project, "gradle-home/caches/modules-2/files-2.1/com.example/other/1.0/abc/other-1.0.pom", "<project/>");

        LocalRepository repository = LocalRepository.forUser(
                home, Map.of("GRADLE_USER_HOME", project.resolve("gradle-home").toString()));

        assertThat(repository.pom("com.example", "lib", "1.0")).isPresent();
        assertThat(repository.pom("com.example", "other", "1.0")).contains("<project/>");
        assertThat(LocalRepository.forUser(home, Files2.noEnvironment()).pom("com.example", "other", "1.0"))
                .isEmpty();
    }
}
