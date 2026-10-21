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

class MavenDependencyResolutionTest {

    @TempDir
    Path temp;

    Path project;
    Path repository;

    @BeforeEach
    void cacheSpringBootAndAnotherBom() {
        project = temp.resolve("project");
        repository = temp.resolve("m2");
        cachedPom(
                repository,
                "org.springframework.boot",
                "spring-boot-dependencies",
                "4.1.1",
                "<properties><jackson-bom.version>3.0.2</jackson-bom.version>"
                        + "<log4j2.version>2.25.2</log4j2.version></properties>"
                        + "<dependencyManagement><dependencies>"
                        + managed("org.springframework.boot", "spring-boot-starter-web", "4.1.1")
                        + managed("org.apache.logging.log4j", "log4j-core", "${log4j2.version}")
                        + "<dependency><groupId>tools.jackson</groupId><artifactId>jackson-bom</artifactId>"
                        + "<version>${jackson-bom.version}</version><type>pom</type><scope>import</scope></dependency>"
                        + "</dependencies></dependencyManagement>");
        cachedPom(
                repository,
                "org.springframework.boot",
                "spring-boot-starter-parent",
                "4.1.1",
                "<parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>"
                        + "<version>4.1.1</version></parent>");
        cachedPom(
                repository,
                "tools.jackson",
                "jackson-bom",
                "3.0.2",
                "<dependencyManagement><dependencies>"
                        + managed("tools.jackson.core", "jackson-databind", "3.0.2")
                        + "</dependencies></dependencyManagement>");
    }

    @Test
    void resolvesVersionsFromTheParentChainAndItsImportedBoms() {
        write(project, "pom.xml", """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.1.1</version>
                    </parent>
                    <artifactId>demo</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>tools.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        ProjectDependencies found = resolve(List.of());

        assertThat(found.unresolved()).isEmpty();
        assertThat(found.resolved())
                .extracting(Dependency::coordinate, Dependency::version, Dependency::origin, Dependency::line)
                .containsExactly(
                        tuple("org.springframework.boot:spring-boot-starter-web", "4.1.1", Origin.MANAGED, 11),
                        tuple("tools.jackson.core:jackson-databind", "3.0.2", Origin.MANAGED, 15));
        assertThat(found.resolved())
                .extracting(dependency -> dependency.managedBy().orElseThrow())
                .containsExactly("spring-boot-starter-parent 4.1.1", "jackson-bom 3.0.2");
    }

    @Test
    void aPropertyInTheProjectOverridesTheVersionItsParentManages() {
        write(project, "pom.xml", """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.1.1</version>
                    </parent>
                    <artifactId>demo</artifactId>
                    <properties>
                        <log4j2.version>2.25.4</log4j2.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-core</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        assertThat(resolve(List.of()).resolved())
                .singleElement()
                .extracting(Dependency::version)
                .isEqualTo("2.25.4");
    }

    @Test
    void importsBomsDeclaredByTheProjectAndPrefersItsOwnManagedVersions() {
        write(project, "pom.xml", """
                <project>
                    <groupId>demo</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <!-- <artifactId>jackson-databind</artifactId> in a comment is not a declaration -->
                            <dependency>
                                <groupId>tools.jackson.core</groupId>
                                <artifactId>jackson-databind</artifactId>
                                <version>3.0.3</version>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>4.1.1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>tools.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-core</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);

        List<Dependency> resolved = resolve(List.of()).resolved();

        assertThat(resolved).extracting(Dependency::version).containsExactly("3.0.3", "2.25.2");
        assertThat(resolved).extracting(Dependency::line).containsExactly(25, 29);
        assertThat(resolved.get(0).managedBy()).contains("demo 1.0");
        assertThat(resolved.get(1).managedBy()).contains("spring-boot-dependencies 4.1.1");
        assertThat(resolved.get(1).scope()).contains("test");
    }

    @Test
    void resolvesModulesWithTheirParentInTheProjectAndSkipsDependenciesOnEachOther() {
        write(project, "pom.xml", """
                <project>
                    <groupId>demo</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0</version>
                    <modules><module>app</module><module>lib/pom.xml</module><module>../escape</module></modules>
                    <properties><log4j.version>2.14.1</log4j.version></properties>
                </project>
                """);
        write(project, "app/pom.xml", """
                <project>
                    <parent><groupId>demo</groupId><artifactId>parent</artifactId><version>1.0</version></parent>
                    <artifactId>app</artifactId>
                    <dependencies>
                        <dependency><groupId>demo</groupId><artifactId>lib</artifactId><version>${project.version}</version></dependency>
                        <dependency>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-core</artifactId>
                            <version>${log4j.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        write(project, "lib/pom.xml", """
                <project>
                    <parent><groupId>demo</groupId><artifactId>parent</artifactId><version>1.0</version></parent>
                    <artifactId>lib</artifactId>
                </project>
                """);

        ProjectDependencies found = resolve(List.of(Path.of("app"), Path.of("lib")));

        assertThat(found.resolved()).singleElement().satisfies(dependency -> {
            assertThat(dependency.coordinate()).isEqualTo("org.apache.logging.log4j:log4j-core");
            assertThat(dependency.version()).isEqualTo("2.14.1");
            assertThat(dependency.origin()).isEqualTo(Origin.DECLARED);
            assertThat(dependency.declaredIn()).isEqualTo(Path.of("app", "pom.xml"));
            assertThat(dependency.line()).isEqualTo(8);
        });
    }

    @Test
    void reportsVersionsItCannotWorkOut() {
        write(project, "pom.xml", """
                <project>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>not-downloaded</artifactId>
                        <version>1.0</version>
                    </parent>
                    <artifactId>demo</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>managed-somewhere</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>typo</artifactId>
                            <version>${missing.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        ProjectDependencies found = resolve(List.of());

        assertThat(found.resolved()).isEmpty();
        assertThat(found.unresolved())
                .extracting(Unresolved::coordinate, Unresolved::line)
                .containsExactly(tuple("com.example:managed-somewhere", 11), tuple("com.example:typo", 15));
        assertThat(found.unresolved().get(0).reason()).contains("not in the local Maven or Gradle cache");
        assertThat(found.unresolved().get(1).reason()).contains("${missing.version}");
    }

    @Test
    void survivesBrokenAndCyclicBuildFiles() {
        write(project, "pom.xml", """
                <project>
                    <groupId>demo</groupId>
                    <artifactId>loop</artifactId>
                    <version>1</version>
                    <parent><groupId>demo</groupId><artifactId>loop</artifactId><version>1</version></parent>
                    <modules><module>broken</module><module>.</module></modules>
                    <dependencyManagement><dependencies>
                        <dependency><groupId>demo</groupId><artifactId>self-bom</artifactId><version>1</version><scope>import</scope></dependency>
                    </dependencies></dependencyManagement>
                </project>
                """);
        write(project, "broken/pom.xml", "<project><unclosed></project>");
        cachedPom(
                repository,
                "demo",
                "self-bom",
                "1",
                "<dependencyManagement><dependencies><dependency><groupId>demo</groupId><artifactId>self-bom</artifactId>"
                        + "<version>1</version><scope>import</scope></dependency></dependencies></dependencyManagement>");

        assertThat(resolve(List.of()).resolved()).isEmpty();
    }

    @Test
    void findsNothingWithoutABuildFile() {
        ProjectDependencies found = new ProjectDependencyResolver(LocalRepository.none())
                .resolve(Files2.project(temp, BuildTool.UNKNOWN, List.of(), Optional.empty()));

        assertThat(found.resolved()).isEmpty();
        assertThat(found.unresolved()).isEmpty();
    }

    private ProjectDependencies resolve(List<Path> modules) {
        return new ProjectDependencyResolver(new LocalRepository(List.of(repository), List.of()))
                .resolve(Files2.project(project, BuildTool.MAVEN, modules, Optional.empty()));
    }
}
