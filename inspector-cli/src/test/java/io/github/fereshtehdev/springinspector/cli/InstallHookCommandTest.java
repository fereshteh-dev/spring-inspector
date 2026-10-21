package io.github.fereshtehdev.springinspector.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.cli.CliRun.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallHookCommandTest {

    private static final String LAUNCHER = "'/opt/java/bin/java' -jar '/opt/spring inspector/spring-inspector.jar'";

    @TempDir
    Path temp;

    CliRun cli;

    @BeforeEach
    void createRepository() {
        cli = CliRun.inNewRepository(temp).launcher(() -> Optional.of(LAUNCHER));
    }

    @AfterEach
    void closeRepository() {
        cli.close();
    }

    private Path hook() {
        return cli.project().resolve(".git").resolve("hooks").resolve("pre-commit");
    }

    @Test
    void installsAHookThatChecksStagedChangesWithThisInstallation() throws IOException {
        Result result = cli.run("install-hook");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(result.out()).contains("Installed the pre-commit hook");
        assertThat(Files.readString(hook())).contains("exec " + LAUNCHER + " check --staged");
    }

    @Test
    void updatesItsOwnHook() {
        cli.run("install-hook");

        Result result = cli.run("install-hook");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(result.out()).contains("Updated the pre-commit hook");
    }

    @Test
    void leavesAForeignHookAloneUnlessForced() throws IOException {
        Files.createDirectories(hook().getParent());
        Files.writeString(hook(), "#!/bin/sh\necho mine\n");

        Result refused = cli.run("install-hook");
        Result forced = cli.run("install-hook", "--force");

        assertThat(refused.exitCode()).isEqualTo(ExitCodes.BLOCKED);
        assertThat(refused.err()).contains("use --force");
        assertThat(forced.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(forced.out()).contains("previous hook kept as");
    }

    @Test
    void needsToRunFromTheJar() {
        Result result = cli.launcher(Optional::empty).run("install-hook");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
        assertThat(result.err()).contains("must run from the spring-inspector jar");
        assertThat(hook()).doesNotExist();
    }
}
