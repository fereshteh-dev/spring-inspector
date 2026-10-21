package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.cli.render.Categories;
import io.github.fereshtehdev.springinspector.cli.render.JsonRenderer;
import io.github.fereshtehdev.springinspector.cli.render.SarifRenderer;
import io.github.fereshtehdev.springinspector.cli.render.Style;
import io.github.fereshtehdev.springinspector.cli.render.TerminalRenderer;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeScope;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedVersions;
import io.github.fereshtehdev.springinspector.core.port.ReportRenderer;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "check",
        description = "Inspect changes and print the report card. Exits with 1 when the gate blocks.",
        sortOptions = false)
final class CheckCommand implements Callable<Integer> {

    enum Format {
        TERMINAL,
        JSON,
        SARIF
    }

    static final class Scope {

        @Option(names = "--staged", description = "Inspect staged changes (the default).")
        boolean staged;

        @Option(
                names = "--base",
                paramLabel = "<ref>",
                description = "Inspect changes since the merge base with <ref>.")
        @Nullable
        String base;

        @Option(names = "--all", description = "Inspect every file in the project.")
        boolean all;
    }

    @ArgGroup(exclusive = true)
    @Nullable
    Scope scope;

    @Option(names = "--format", paramLabel = "<format>", description = "terminal, json or sarif. Default: terminal.")
    Format format = Format.TERMINAL;

    @Option(
            names = "--details",
            paramLabel = "<category>",
            description = "Show why and how to fix for one category, e.g. security or spring-boot.")
    @Nullable
    String details;

    @Option(names = "--project", paramLabel = "<dir>", description = "Project directory. Default: current directory.")
    @Nullable
    Path project;

    @Option(names = "--no-cache", description = "Inspect everything again instead of reusing earlier results.")
    boolean noCache;

    private final Inspector inspector;
    private final Path workingDirectory;
    private final Style style;
    private final String version;
    private final PrintWriter out;
    private final PrintWriter err;

    CheckCommand(
            Inspector inspector, Path workingDirectory, Style style, String version, PrintWriter out, PrintWriter err) {
        this.inspector = inspector;
        this.workingDirectory = workingDirectory;
        this.style = style;
        this.version = version;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() throws IOException {
        Optional<Category> category = details == null ? Optional.empty() : Optional.of(category(details));
        Path root = (project == null ? workingDirectory : workingDirectory.resolve(project))
                .toAbsolutePath()
                .normalize();
        InspectionRun run = inspector.inspect(root, changeScope(), !noCache);
        warn(run);
        if (format == Format.TERMINAL) {
            out.println(headline(run, style));
            if (run.changedFiles() == 0) {
                out.println("Nothing to inspect. Stage changes, or use --all or --base <ref>.");
            }
            out.println();
        }
        renderer(category).render(run.report(), out);
        out.flush();
        return run.report().gate().blocks() ? ExitCodes.BLOCKED : ExitCodes.OK;
    }

    private ChangeScope changeScope() {
        if (scope == null || scope.staged) {
            return ChangeScope.staged();
        }
        String baseRef = scope.base;
        return baseRef != null ? ChangeScope.againstBase(baseRef) : ChangeScope.all();
    }

    private ReportRenderer renderer(Optional<Category> category) {
        return switch (format) {
            case TERMINAL -> new TerminalRenderer(style, category);
            case JSON -> new JsonRenderer(version);
            case SARIF -> new SarifRenderer(version);
        };
    }

    private void warn(InspectionRun run) {
        run.project().warnings().forEach(warning -> err.println("warning: " + warning));
        for (Map.Entry<Path, String> file : run.unparseableFiles().entrySet()) {
            err.println("warning: could not parse " + file.getKey().toString().replace('\\', '/') + " ("
                    + file.getValue() + "); its findings are missing");
        }
        err.flush();
    }

    private static String headline(InspectionRun run, Style style) {
        List<String> parts = new ArrayList<>();
        parts.add(run.changedFiles() + (run.changedFiles() == 1 ? " changed file" : " changed files"));
        DetectedVersions versions = run.project().versions();
        versions.java().ifPresent(java -> parts.add("Java " + java.major()));
        versions.springBoot().ifPresent(boot -> parts.add("Spring Boot " + boot));
        versions.springAi().ifPresent(ai -> parts.add("Spring AI " + ai));
        return String.join(style.symbol(" · ", " - "), parts);
    }

    private static Category category(String text) {
        return Categories.parse(text)
                .orElseThrow(() -> new IllegalArgumentException("Unknown category '" + text + "'; use one of "
                        + String.join(
                                ", ",
                                List.of(Category.values()).stream()
                                        .map(Categories::option)
                                        .toList())));
    }
}
