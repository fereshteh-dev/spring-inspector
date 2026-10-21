package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.git.HookInstallation;
import io.github.fereshtehdev.springinspector.git.HookInstaller;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "install-hook",
        description = "Install a git pre-commit hook that runs 'check --staged'. Skip it once with git commit"
                + " --no-verify.",
        sortOptions = false)
final class InstallHookCommand implements Callable<Integer> {

    @Option(
            names = "--force",
            description = "Replace a pre-commit hook not managed by spring-inspector; it is kept" + " as a backup.")
    boolean force;

    @Option(names = "--project", paramLabel = "<dir>", description = "Project directory. Default: current directory.")
    @Nullable
    Path project;

    private final HookInstaller installer;
    private final Supplier<Optional<String>> launcher;
    private final Path workingDirectory;
    private final PrintWriter out;
    private final PrintWriter err;

    InstallHookCommand(
            HookInstaller installer,
            Supplier<Optional<String>> launcher,
            Path workingDirectory,
            PrintWriter out,
            PrintWriter err) {
        this.installer = installer;
        this.launcher = launcher;
        this.workingDirectory = workingDirectory;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        Optional<String> command = launcher.get();
        if (command.isEmpty()) {
            err.println("error: install-hook must run from the spring-inspector jar (java -jar spring-inspector.jar"
                    + " install-hook), so the hook knows what to start");
            err.flush();
            return ExitCodes.USAGE;
        }
        Path root = (project == null ? workingDirectory : workingDirectory.resolve(project))
                .toAbsolutePath()
                .normalize();
        HookInstallation installation = installer.install(root, command.get(), force);
        return switch (installation) {
            case HookInstallation.Installed(Path hook) -> done("Installed the pre-commit hook: " + hook);
            case HookInstallation.Updated(Path hook) -> done("Updated the pre-commit hook: " + hook);
            case HookInstallation.Replaced(Path hook, Path backup) ->
                done("Replaced the pre-commit hook: " + hook + " (previous hook kept as " + backup + ")");
            case HookInstallation.Refused(Path hook, String reason) -> {
                err.println("error: " + reason + ": " + hook);
                err.flush();
                yield ExitCodes.BLOCKED;
            }
        };
    }

    private Integer done(String message) {
        out.println(message);
        out.println("Re-run install-hook after moving or upgrading spring-inspector.jar.");
        out.flush();
        return ExitCodes.OK;
    }
}
