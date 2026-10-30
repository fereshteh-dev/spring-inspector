package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.advisories.AdvisoryStore;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater.Outcome;
import io.github.fereshtehdev.springinspector.advisories.ReleaseCalendarStore;
import io.github.fereshtehdev.springinspector.advisories.ReleaseCalendarUpdater;
import io.github.fereshtehdev.springinspector.advisories.VulnerableDependencyCheck;
import io.github.fereshtehdev.springinspector.cli.render.Style;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.git.HookInstaller;
import io.github.fereshtehdev.springinspector.project.BuildFileProjectDetector;
import io.github.fereshtehdev.springinspector.project.architecture.JavaImportScanner;
import io.github.fereshtehdev.springinspector.project.dependencies.LocalRepository;
import io.github.fereshtehdev.springinspector.rules.architecture.AiInWebLayerCheck;
import io.github.fereshtehdev.springinspector.rules.architecture.DomainFrameworkCheck;
import io.github.fereshtehdev.springinspector.rules.architecture.FeatureInternalsCheck;
import io.github.fereshtehdev.springinspector.rules.architecture.HexagonalDirectionCheck;
import io.github.fereshtehdev.springinspector.rules.architecture.LayerViolationCheck;
import io.github.fereshtehdev.springinspector.rules.architecture.NewFeatureDependencyCheck;
import io.github.fereshtehdev.springinspector.rules.architecture.PackageCycleCheck;
import io.github.fereshtehdev.springinspector.rules.spring.JavaSupportCheck;
import io.github.fereshtehdev.springinspector.rules.spring.NewerPatchCheck;
import io.github.fereshtehdev.springinspector.rules.spring.SpringBootSupportCheck;
import java.io.Console;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
        AdvisoryStore advisories = new AdvisoryStore(home.advisories());
        ReleaseCalendarStore releases = new ReleaseCalendarStore(home.advisories());
        List<Check> checks = withProjectMap(withReleaseCalendar(checks(), releases::load), projectMaps());
        checks.add(new VulnerableDependencyCheck(advisories, LocalRepository.forUser(userHome, environment)));
        Inspector inspector =
                new Inspector(new BuildFileProjectDetector(), checks, environment, home, advisories::lastUpdated);
        Supplier<Optional<String>> launcher = launcherOverride.orElse(() -> Launcher.current(home));
        String version = Version.current();
        CommandLine commandLine = new CommandLine(new SpringInspector(err))
                .addSubcommand(new CheckCommand(inspector, workingDirectory, style, version, out, err))
                .addSubcommand(new InitCommand(workingDirectory, out, err))
                .addSubcommand(new ArchitectureCommand(
                        workingDirectory, new BuildFileProjectDetector(), JavaImportScanner::scan, style, out))
                .addSubcommand(new UpdateCommand(
                        maxAge -> {
                            AdvisoryUpdater updater = new AdvisoryUpdater(advisories, environment);
                            Outcome outcome =
                                    maxAge.map(updater::updateIfOlderThan).orElseGet(updater::update);
                            updateReleaseCalendar(new ReleaseCalendarUpdater(releases, environment), maxAge, err);
                            return outcome;
                        },
                        advisories.file(),
                        out,
                        err))
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

    /** Rules that know release dates read them from the downloaded calendar instead of their built-in table. */
    static List<Check> withReleaseCalendar(List<Check> checks, Supplier<Optional<ReleaseCalendar>> calendar) {
        Clock clock = Clock.systemUTC();
        List<Check> replacements = List.of(
                new SpringBootSupportCheck(clock, calendar),
                new NewerPatchCheck(calendar),
                new JavaSupportCheck(clock, calendar));
        List<Check> result = new ArrayList<>();
        for (Check check : checks) {
            result.add(replacements.stream()
                    .filter(replacement -> replacement.id().equals(check.id()))
                    .findFirst()
                    .orElse(check));
        }
        return result;
    }

    /** Architecture rules read the map of the whole project, scanned once per run and shared between them. */
    static List<Check> withProjectMap(List<Check> checks, Function<ProjectContext, ImportGraph> maps) {
        List<Check> replacements = List.of(
                new LayerViolationCheck(maps),
                new PackageCycleCheck(maps),
                new DomainFrameworkCheck(maps),
                new FeatureInternalsCheck(maps),
                new NewFeatureDependencyCheck(maps),
                new HexagonalDirectionCheck(maps),
                new AiInWebLayerCheck(maps));
        return checks.stream()
                .map(check -> replacements.stream()
                        .filter(replacement -> replacement.id().equals(check.id()))
                        .findFirst()
                        .orElse(check))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Function<ProjectContext, ImportGraph> projectMaps() {
        Map<Path, ImportGraph> scanned = new ConcurrentHashMap<>();
        return project -> scanned.computeIfAbsent(project.root(), JavaImportScanner::scan);
    }

    /** A release calendar that cannot be downloaded is not worth failing the update for; the last one stays. */
    private static void updateReleaseCalendar(
            ReleaseCalendarUpdater updater, Optional<Duration> maxAge, PrintWriter err) {
        try {
            maxAge.ifPresentOrElse(updater::updateIfOlderThan, updater::update);
        } catch (RuntimeException failure) {
            err.println("warning: could not update the release calendar: " + failure.getMessage());
            err.flush();
        }
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
