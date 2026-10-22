package io.github.fereshtehdev.springinspector.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(IsolatedGitConfig.class)
class HookInstallerTest {

    private static final String COMMAND = "spring-inspector";

    @TempDir
    Path temp;

    TestRepo repo;
    HookInstaller installer;
    Path defaultHook;

    @BeforeEach
    void createRepository() throws IOException {
        repo = TestRepo.init(Files.createDirectory(temp.resolve("repo")));
        installer = new HookInstaller(repo.environment());
        defaultHook = repo.root().resolve(".git/hooks/pre-commit");
    }

    @AfterEach
    void closeRepository() {
        repo.close();
    }

    @Test
    void installsAHookThatChecksStagedChanges() throws IOException {
        HookInstallation result = installer.install(repo.root(), COMMAND, false);

        assertThat(result).isEqualTo(new HookInstallation.Installed(defaultHook));
        assertThat(Files.readString(defaultHook))
                .startsWith("#!/bin/sh\n")
                .contains(HookInstaller.MARKER)
                .contains("--no-verify")
                .contains("spring-inspector update --max-age 1d --quiet || true\n")
                .endsWith("exec spring-inspector check --staged\n")
                .doesNotContain("\r");
    }

    @Test
    void makesTheHookExecutable() throws IOException {
        installer.install(repo.root(), COMMAND, false);

        PosixFileAttributeView posix = Files.getFileAttributeView(defaultHook, PosixFileAttributeView.class);
        assumeTrue(posix != null, "POSIX permissions are not available on this file system");
        assertThat(posix.readAttributes().permissions()).contains(PosixFilePermission.OWNER_EXECUTE);
    }

    @Test
    void updatesAHookItInstalledBefore() throws IOException {
        installer.install(repo.root(), COMMAND, false);

        HookInstallation result = installer.install(repo.root(), "java -jar /opt/inspector.jar", false);

        assertThat(result).isEqualTo(new HookInstallation.Updated(defaultHook));
        assertThat(Files.readString(defaultHook)).contains("exec java -jar /opt/inspector.jar check --staged");
    }

    @Test
    void leavesSomeoneElsesHookAloneUnlessForced() throws IOException {
        repo.write(".git/hooks/pre-commit", "#!/bin/sh\nrun-linter\n");

        HookInstallation result = installer.install(repo.root(), COMMAND, false);

        assertThat(result).isInstanceOf(HookInstallation.Refused.class);
        assertThat(((HookInstallation.Refused) result).reason()).contains("--force");
        assertThat(Files.readString(defaultHook)).isEqualTo("#!/bin/sh\nrun-linter\n");
    }

    @Test
    void replacesSomeoneElsesHookWhenForcedAndKeepsABackup() throws IOException {
        repo.write(".git/hooks/pre-commit", "#!/bin/sh\nfirst-linter\n");
        installer.install(repo.root(), COMMAND, true);
        repo.write(".git/hooks/pre-commit", "#!/bin/sh\nsecond-linter\n");

        HookInstallation result = installer.install(repo.root(), COMMAND, true);

        Path firstBackup = defaultHook.resolveSibling("pre-commit" + HookInstaller.BACKUP_SUFFIX);
        Path secondBackup = defaultHook.resolveSibling("pre-commit" + HookInstaller.BACKUP_SUFFIX + ".1");
        assertThat(result).isEqualTo(new HookInstallation.Replaced(defaultHook, secondBackup));
        assertThat(Files.readString(firstBackup)).contains("first-linter");
        assertThat(Files.readString(secondBackup)).contains("second-linter");
        assertThat(Files.readString(defaultHook)).contains(HookInstaller.MARKER);
    }

    @Test
    void installsIntoTheConfiguredHooksPath() {
        repo.configure("core", "hooksPath", ".githooks");

        HookInstallation result = installer.install(repo.root(), COMMAND, false);

        assertThat(result.hook()).isEqualTo(repo.root().resolve(".githooks/pre-commit"));
        assertThat(defaultHook).doesNotExist();
    }

    @Test
    void changesIntoTheProjectWhenItLivesInASubdirectory() throws IOException {
        Path project = Files.createDirectories(repo.root().resolve("it's a service"));

        installer.install(project, COMMAND, false);

        assertThat(Files.readString(defaultHook)).contains("cd -- 'it'\\''s a service' || exit 1\n");
    }

    @Test
    void producesAScriptThatTheShellRunsFromTheProjectDirectory() throws Exception {
        Path project = Files.createDirectories(repo.root().resolve("service"));
        installer.install(project, "sh -c pwd", false);

        Process process;
        try {
            process = new ProcessBuilder("sh", defaultHook.toString())
                    .directory(repo.root().toFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            assumeTrue(false, "no POSIX shell available: " + e.getMessage());
            return;
        }
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();

        assertThat(process.exitValue()).isZero();
        assertThat(output).endsWith("/service");
    }

    @Test
    void rejectsCommandsThatWouldInjectExtraLines() {
        assertThatThrownBy(() -> installer.install(repo.root(), "spring-inspector\nrm -rf /", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> installer.install(repo.root(), " ", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(defaultHook).doesNotExist();
    }
}
