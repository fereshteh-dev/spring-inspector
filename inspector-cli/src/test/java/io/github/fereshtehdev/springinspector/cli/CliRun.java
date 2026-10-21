package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.cli.render.Style;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;

/** Runs the real command line in-process against a temporary git repository, capturing output. */
final class CliRun {

    record Result(int exitCode, String out, String err) {}

    private final Path temp;
    private final Path project;
    private final Path home;
    private final Git git;
    private Supplier<Optional<String>> launcher = Optional::empty;

    private CliRun(Path temp, Path project, Path home, Git git) {
        this.temp = temp;
        this.project = project;
        this.home = home;
        this.git = git;
    }

    static CliRun inNewRepository(Path temp) {
        try {
            Path project = Files.createDirectories(temp.resolve("project"));
            Path home = Files.createDirectories(temp.resolve("home"));
            Files.writeString(project.resolve("pom.xml"), """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>4.1.1</version>
                        </parent>
                        <artifactId>demo</artifactId>
                        <properties>
                            <java.version>25</java.version>
                        </properties>
                    </project>
                    """);
            return new CliRun(
                    temp,
                    project,
                    home,
                    Git.init().setDirectory(project.toFile()).call());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }

    Path project() {
        return project;
    }

    /** The per-user directory this run uses, also its {@code SPRING_INSPECTOR_HOME}. */
    Path home() {
        return home;
    }

    CliRun launcher(Supplier<Optional<String>> launcher) {
        this.launcher = launcher;
        return this;
    }

    CliRun write(String path, String content) {
        try {
            Path file = project.resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    CliRun stage() {
        try {
            git.add().addFilepattern(".").call();
            return this;
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }

    CliRun commit(String message) {
        try {
            PersonIdent tester = new PersonIdent("Tester", "tester@example.com");
            stage();
            git.commit()
                    .setMessage(message)
                    .setAuthor(tester)
                    .setCommitter(tester)
                    .setSign(false)
                    .call();
            return this;
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }

    String currentBranch() {
        try {
            return Objects.requireNonNull(git.getRepository().getBranch());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    CliRun branch(String name) {
        try {
            git.checkout().setCreateBranch(true).setName(name).call();
            return this;
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }

    Result run(String... arguments) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Map<String, String> environment = Map.of(
                InspectorHome.VARIABLE,
                home.toString(),
                "GIT_CONFIG_NOSYSTEM",
                "1",
                // The temporary directory may itself sit inside a repository, such as a home directory under git.
                "GIT_CEILING_DIRECTORIES",
                temp.toString());
        int exitCode = SpringInspector.commandLine(
                        environment,
                        home,
                        project,
                        Style.PLAIN,
                        Optional.of(launcher),
                        new PrintWriter(out, true),
                        new PrintWriter(err, true))
                .execute(arguments);
        return new Result(exitCode, out.toString(), err.toString());
    }

    void close() {
        git.close();
    }
}
