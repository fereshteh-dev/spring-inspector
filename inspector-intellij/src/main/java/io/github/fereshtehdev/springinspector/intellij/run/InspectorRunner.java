package io.github.fereshtehdev.springinspector.intellij.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import io.github.fereshtehdev.springinspector.intellij.settings.InspectorSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Runs the bundled command line on the project in a separate process and reads its report. */
public final class InspectorRunner {

    static final Duration TIMEOUT = Duration.ofMinutes(10);
    static final Duration UPDATE_TIMEOUT = Duration.ofMinutes(5);

    private InspectorRunner() {}

    public static RunOutcome run(Path project, InspectionScope scope, List<Path> files, ProgressIndicator indicator) {
        Optional<Path> java = java();
        if (java.isEmpty()) {
            return new RunOutcome.Failed("Spring Inspector needs Java " + JavaRuntimes.REQUIRED_FEATURE
                    + " or newer, and this IDE runs on Java " + Runtime.version().feature()
                    + ". Choose one in Settings | Tools | Spring Inspector.");
        }
        Optional<Path> jar = bundledJar();
        if (jar.isEmpty()) {
            return new RunOutcome.Failed("The plugin is incomplete: cli/spring-inspector.jar is missing. Reinstall it.");
        }
        InspectorCommand inspector = new InspectorCommand(java.get(), jar.get(), project, scope, files);
        Optional<Path> argumentFile = Optional.empty();
        try {
            if (files.size() > InspectorCommand.MAX_FILES_ON_COMMAND_LINE) {
                argumentFile = Optional.of(Files.createTempFile("spring-inspector-files", ".txt"));
                Files.writeString(argumentFile.get(), inspector.argumentFileContent(), StandardCharsets.UTF_8);
            }
            GeneralCommandLine command = new GeneralCommandLine(inspector.arguments(argumentFile))
                    .withWorkDirectory(project.toFile())
                    .withCharset(StandardCharsets.UTF_8);
            ProcessOutput output = new CapturingProcessHandler(command)
                    .runProcessWithProgressIndicator(indicator, (int) TIMEOUT.toMillis());
            if (output.isCancelled()) {
                return new RunOutcome.Failed("The inspection was cancelled.");
            }
            if (output.isTimeout()) {
                return new RunOutcome.Failed("The inspection took longer than " + TIMEOUT.toMinutes()
                        + " minutes and was stopped.");
            }
            return RunOutcome.of(output.getExitCode(), output.getStdout(), output.getStderr());
        } catch (ExecutionException | IOException e) {
            return new RunOutcome.Failed("Spring Inspector could not be started: " + e.getMessage());
        } finally {
            argumentFile.ifPresent(InspectorRunner::deleteQuietly);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            file.toFile().deleteOnExit();
        }
    }

    /**
     * Refreshes the vulnerability advisories when they are older than a day.
     *
     * @return why it did not work; empty when it worked or was not needed
     */
    public static Optional<String> updateAdvisories(ProgressIndicator indicator) {
        Optional<Path> java = java();
        Optional<Path> jar = bundledJar();
        if (java.isEmpty() || jar.isEmpty()) {
            return Optional.of("Spring Inspector cannot run: no Java " + JavaRuntimes.REQUIRED_FEATURE
                    + " or the bundled command line is missing");
        }
        try {
            ProcessOutput output = new CapturingProcessHandler(new GeneralCommandLine(
                                    InspectorCommand.updateAdvisories(java.get(), jar.get()))
                            .withCharset(StandardCharsets.UTF_8))
                    .runProcessWithProgressIndicator(indicator, (int) UPDATE_TIMEOUT.toMillis());
            if (output.getExitCode() == 0 && !output.isTimeout() && !output.isCancelled()) {
                return Optional.empty();
            }
            String detail = output.getStderr().strip();
            return Optional.of(detail.isEmpty() ? "the update did not finish" : detail);
        } catch (ExecutionException e) {
            return Optional.of(String.valueOf(e.getMessage()));
        }
    }

    private static Optional<Path> java() {
        return JavaRuntimes.find(
                InspectorSettings.getInstance().javaPath(),
                Path.of(System.getProperty("java.home")),
                Runtime.version().feature(),
                SystemInfo.isWindows);
    }

    /** The jar in the plugin's {@code cli} folder, next to the {@code lib} folder that holds this class's jar. */
    private static Optional<Path> bundledJar() {
        String ownJar = PathManager.getJarPathForClass(InspectorRunner.class);
        Path lib = ownJar == null ? null : Path.of(ownJar).getParent();
        Path plugin = lib == null ? null : lib.getParent();
        if (plugin == null) {
            return Optional.empty();
        }
        Path jar = plugin.resolve("cli").resolve("spring-inspector.jar");
        return Files.isRegularFile(jar) ? Optional.of(jar) : Optional.empty();
    }
}
