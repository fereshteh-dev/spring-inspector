package io.github.fereshtehdev.springinspector.project.dependencies;

import static io.github.fereshtehdev.springinspector.project.dependencies.Files2.cachedPom;
import static io.github.fereshtehdev.springinspector.project.dependencies.Files2.managed;
import static io.github.fereshtehdev.springinspector.project.dependencies.Files2.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.project.ProjectDependencyResolver;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency.Origin;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleDependencyResolutionTest {

    @TempDir
    Path temp;

    Path project;
    Path gradleCache;

    @BeforeEach
    void cacheBomsTheWayGradleStoresThem() {
        project = temp.resolve("project");
        gradleCache = temp.resolve("files-2.1");
        Path hashed = temp.resolve("layout");
        cachedPom(
                hashed,
                "org.springframework.boot",
                "spring-boot-dependencies",
                "4.1.1",
                "<dependencyManagement><dependencies>"
                        + managed("org.springframework.boot", "spring-boot-starter-web", "4.1.1")
                        + "</dependencies></dependencyManagement>");
        cachedPom(
                hashed,
                "com.example",
                "example-bom",
                "2.0",
                "<dependencyManagement><dependencies>" + managed("com.example", "from-platform", "2.0.1")
                        + "</dependencies></dependencyManagement>");
        moveToGradleLayout(hashed, "org.springframework.boot", "spring-boot-dependencies", "4.1.1");
        moveToGradleLayout(hashed, "com.example", "example-bom", "2.0");
    }

    @Test
    void readsTheCommonNotationsAndResolvesVersionsThroughBoms() {
        write(project, "gradle.properties", "jacksonVersion=2.9.8\n");
        write(project, "gradle/libs.versions.toml", """
                [versions]
                log4j = "2.14.1"

                [libraries]
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version.ref = "log4j" }
                example-bom = "com.example:example-bom:2.0"
                """);
        write(project, "build.gradle.kts", """
                plugins {
                    id("org.springframework.boot") version "4.1.1"
                }
                val guavaVersion = "31.0-jre"
                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter-web")
                    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
                    implementation("com.google.guava:guava:${guavaVersion}")
                    implementation(libs.log4j.core)
                    implementation(platform(libs.example.bom))
                    implementation("com.example:from-platform")
                    // implementation("com.example:commented-out:1.0")
                    testImplementation(group = "junit", name = "junit", version = "4.12")
                    implementation("com.example:dynamic:1.+")
                    implementation("com.example:unknown")
                }
                """);

        ProjectDependencies found = resolve(Optional.of("4.1.1"));

        assertThat(found.resolved())
                .extracting(Dependency::coordinate, Dependency::version, Dependency::origin, Dependency::line)
                .containsExactlyInAnyOrder(
                        tuple("org.springframework.boot:spring-boot-starter-web", "4.1.1", Origin.MANAGED, 6),
                        tuple("com.fasterxml.jackson.core:jackson-databind", "2.9.8", Origin.DECLARED, 7),
                        tuple("com.google.guava:guava", "31.0-jre", Origin.DECLARED, 8),
                        tuple("org.apache.logging.log4j:log4j-core", "2.14.1", Origin.DECLARED, 9),
                        tuple("com.example:from-platform", "2.0.1", Origin.MANAGED, 11),
                        tuple("junit:junit", "4.12", Origin.DECLARED, 13));
        assertThat(found.resolved())
                .filteredOn(dependency -> dependency.origin() == Origin.MANAGED)
                .extracting(dependency -> dependency.managedBy().orElseThrow())
                .containsExactlyInAnyOrder("spring-boot-dependencies 4.1.1", "example-bom 2.0");
        assertThat(found.unresolved())
                .extracting(Unresolved::coordinate, Unresolved::line)
                .containsExactly(tuple("com.example:dynamic", 14), tuple("com.example:unknown", 15));
    }

    @Test
    void readsModuleScriptsAndMavenBomImports() {
        write(project, "settings.gradle", "include 'app'\n");
        write(project, "app/build.gradle", """
                dependencyManagement {
                    imports {
                        mavenBom "com.example:example-bom:2.0"
                    }
                }
                dependencies {
                    implementation 'com.example:from-platform'
                }
                """);

        ProjectDependencies found = resolve(Optional.empty());

        assertThat(found.resolved()).singleElement().satisfies(dependency -> {
            assertThat(dependency.version()).isEqualTo("2.0.1");
            assertThat(dependency.declaredIn()).isEqualTo(Path.of("app", "build.gradle"));
            assertThat(dependency.line()).isEqualTo(7);
        });
    }

    private ProjectDependencies resolve(Optional<String> springBoot) {
        List<Path> modules = project.resolve("app").toFile().isDirectory() ? List.of(Path.of("app")) : List.of();
        return new ProjectDependencyResolver(new LocalRepository(List.of(), List.of(gradleCache)))
                .resolve(Files2.project(project, BuildTool.GRADLE, modules, springBoot));
    }

    /** Gradle keeps {@code group/name/version/<hash>/name-version.pom}, with the group not split into folders. */
    private void moveToGradleLayout(Path maven, String groupId, String artifactId, String version) {
        String file = artifactId + "-" + version + ".pom";
        Path source = maven.resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version)
                .resolve(file);
        write(gradleCache, groupId + "/" + artifactId + "/" + version + "/0a1b2c/" + file, read(source));
    }

    private static String read(Path file) {
        try {
            return java.nio.file.Files.readString(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
