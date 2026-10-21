package io.github.fereshtehdev.springinspector.intellij.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import io.github.fereshtehdev.springinspector.intellij.settings.InspectorSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Runs the bundled command line on the project in a separate process and reads its report. */
public final class InspectorRunner {

    public static final PluginId PLUGIN_ID = PluginId.getId("io.github.fereshtehdev.spring-inspector");
    static final Duration TIMEOUT = Duration.ofMinutes(10);

    private InspectorRunner() {}

    public static RunOutcome run(Path project, InspectionScope scope, List<Path> files, ProgressIndicator indicator) {
        Optional<Path> java = JavaRuntimes.find(
                InspectorSettings.getInstance().javaPath(),
                Path.of(System.getProperty("java.home")),
                Runtime.version().feature(),
                SystemInfo.isWindows);
        if (java.isEmpty()) {
            return new RunOutcome.Failed("Spring Inspector needs Java " + JavaRuntimes.REQUIRED_FEATURE
                    + " or newer, and this IDE runs on Java " + Runtime.version().feature()
                    + ". Choose one in Settings | Tools | Spring Inspector.");
        }
        Optional<Path> jar = bundledJar();
        if (jar.isEmpty()) {
            return new RunOutcome.Failed("The plugin is incomplete: cli/spring-inspector.jar is missing. Reinstall it.");
        }
        GeneralCommandLine command = new GeneralCommandLine(
                        new InspectorCommand(java.get(), jar.get(), project, scope, files).arguments())
                .withWorkDirectory(project.toFile())
                .withCharset(StandardCharsets.UTF_8);
        try {
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
        } catch (ExecutionException e) {
            return new RunOutcome.Failed("Spring Inspector could not be started: " + e.getMessage());
        }
    }

    private static Optional<Path> bundledJar() {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PLUGIN_ID);
        if (plugin == null) {
            return Optional.empty();
        }
        Path jar = plugin.getPluginPath().resolve("cli").resolve("spring-inspector.jar");
        return Files.isRegularFile(jar) ? Optional.of(jar) : Optional.empty();
    }
}
