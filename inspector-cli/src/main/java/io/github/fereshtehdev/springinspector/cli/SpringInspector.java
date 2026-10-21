package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.cli.render.Style;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.git.HookInstaller;
import io.github.fereshtehdev.springinspector.project.BuildFileProjectDetector;
import java.io.Console;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** The composition root: wires adapters to core and maps outcomes to exit codes. */
@Command(
        name = "spring-inspector",
        mixinStandardHelpOptions = true,
        versionProvider = SpringInspector.Version.class,
        description = "Offline code inspector for Java, Spring Boot and Spring AI projects.")
public final class SpringInspector implements Runnable {

    private final PrintWriter err;

    private SpringInspector(PrintWriter err) {
        this.err = err;
    }

    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, System.out.charset()), true);
        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, System.err.charset()), true);
        Map<String, String> environment = System.getenv();
        Style style = Style.detect(
                environment,
                Optional.ofNullable(System.console()).map(Console::isTerminal).orElse(false),
                System.out.charset().name());
        CommandLine commandLine = commandLine(
                environment,
                Path.of(System.getProperty("user.home")),
                Path.of("").toAbsolutePath(),
                style,
                Optional.empty(),
                out,
                err);
        System.exit(commandLine.execute(args));
    }

    static CommandLine commandLine(
            Map<String, String> environment,
            Path userHome,
            Path workingDirectory,
            Style style,
            Optional<Supplier<Optional<String>>> launcherOverride,
            PrintWriter out,
            PrintWriter err) {
        InspectorHome home = InspectorHome.from(environment, userHome);
        Inspector inspector = new Inspector(new BuildFileProjectDetector(), checks(), environment, home);
        Supplier<Optional<String>> launcher = launcherOverride.orElse(() -> Launcher.current(home));
        String version = Version.current();
        CommandLine commandLine = new CommandLine(new SpringInspector(err))
                .addSubcommand(new CheckCommand(inspector, workingDirectory, style, version, out, err))
                .addSubcommand(
                        new InstallHookCommand(new HookInstaller(environment), launcher, workingDirectory, out, err))
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setOut(out)
                .setErr(err);
        commandLine.setExecutionExceptionHandler((exception, command, parseResult) -> {
            if (exception instanceof IllegalArgumentException) {
                err.println("error: " + exception.getMessage());
                err.flush();
                return ExitCodes.USAGE;
            }
            err.println("spring-inspector failed: " + exception);
            err.flush();
            return ExitCodes.FAILURE;
        });
        return commandLine;
    }

    /** Every check on the class path; each rule module registers its own. */
    static List<Check> checks() {
        return ServiceLoader.load(Check.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    @Override
    public void run() {
        err.println("Missing command. Try: spring-inspector check --help");
        err.flush();
        throw new CommandLine.ParameterException(new CommandLine(this), "Missing command");
    }

    static final class Version implements CommandLine.IVersionProvider {

        static String current() {
            String version = SpringInspector.class.getPackage().getImplementationVersion();
            return version == null ? "development build" : version;
        }

        @Override
        public String[] getVersion() {
            return new String[] {"spring-inspector " + current()};
        }
    }
}
