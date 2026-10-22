package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.cli.render.JsonRenderer;
import io.github.fereshtehdev.springinspector.cli.render.Style;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.port.ProjectDetector;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.Hotspot;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.Part;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.StyleMatch;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.Suggestion;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Command(
        name = "architecture",
        description = "Show the project's architecture: the styles it follows, its parts and their dependencies,"
                + " cycles, overloaded classes, and suggestions for a cleaner structure.",
        sortOptions = false)
final class ArchitectureCommand implements Callable<Integer> {

    enum Format {
        TERMINAL,
        MARKDOWN,
        JSON
    }

    @Option(names = "--project", paramLabel = "<dir>", description = "Project directory. Default: current directory.")
    @Nullable
    Path project;

    @Option(
            names = "--format",
            paramLabel = "<format>",
            description = "terminal, markdown (with a Mermaid diagram) or json. Default: terminal.")
    Format format = Format.TERMINAL;

    private final Path workingDirectory;
    private final ProjectDetector detector;
    private final Function<Path, ImportGraph> scanner;
    private final Style style;
    private final PrintWriter out;

    ArchitectureCommand(
            Path workingDirectory,
            ProjectDetector detector,
            Function<Path, ImportGraph> scanner,
            Style style,
            PrintWriter out) {
        this.workingDirectory = workingDirectory;
        this.detector = detector;
        this.scanner = scanner;
        this.style = style;
        this.out = out;
    }

    @Override
    public Integer call() {
        Path root = (project == null ? workingDirectory : workingDirectory.resolve(project))
                .toAbsolutePath()
                .normalize();
        int modules = detector.detect(root).modules().size();
        ArchitectureReport report = ArchitectureReport.of(scanner.apply(root), modules);
        out.print(
                switch (format) {
                    case TERMINAL -> terminal(report);
                    case MARKDOWN -> markdown(report);
                    case JSON -> json(report);
                });
        out.flush();
        return ExitCodes.OK;
    }

    private String terminal(ArchitectureReport report) {
        StringBuilder text = new StringBuilder();
        text.append("Architecture of ")
                .append(report.rootPackage().isEmpty() ? "the project" : report.rootPackage())
                .append(" (")
                .append(report.classes())
                .append(" classes)\n\n");
        text.append("Styles\n");
        if (report.styles().isEmpty()) {
            text.append("  none recognised\n");
        }
        for (StyleMatch match : report.styles()) {
            text.append("  ")
                    .append(style.symbol("✔ ", "+ "))
                    .append(match.style().title())
                    .append(" (")
                    .append(match.confidence())
                    .append("): ")
                    .append(String.join("; ", match.evidence()))
                    .append('\n');
        }
        text.append("\nParts\n");
        for (Part part : report.parts()) {
            text.append("  ")
                    .append(part.name())
                    .append(" (")
                    .append(part.classes())
                    .append(part.classes() == 1 ? " class)" : " classes)");
            if (!part.dependsOn().isEmpty()) {
                text.append(" uses ").append(dependencies(part));
            }
            text.append('\n');
        }
        if (!report.cycles().isEmpty()) {
            text.append("\nCycles\n");
            report.cycles()
                    .forEach(cycle ->
                            text.append("  ").append(String.join(" ⇄ ", cycle)).append('\n'));
        }
        if (!report.hotspots().isEmpty()) {
            text.append("\nClasses that do too much\n");
            for (Hotspot hotspot : report.hotspots()) {
                text.append("  ")
                        .append(hotspot.type())
                        .append(": used by ")
                        .append(hotspot.usedBy())
                        .append(", uses ")
                        .append(hotspot.uses())
                        .append('\n');
            }
        }
        text.append("\nSuggestions\n");
        if (report.suggestions().isEmpty()) {
            text.append("  None: the structure is consistent.\n");
        }
        int number = 1;
        for (Suggestion suggestion : report.suggestions()) {
            text.append("  ")
                    .append(number++)
                    .append(". ")
                    .append(suggestion.title())
                    .append('\n');
            text.append("     ").append(suggestion.why()).append('\n');
            suggestion
                    .steps()
                    .forEach(step -> text.append("     - ").append(step.strip()).append('\n'));
        }
        return style.symbol(text.toString(), text.toString().replace("⇄", "<->").replace("→", "->"));
    }

    static String markdown(ArchitectureReport report) {
        StringBuilder md = new StringBuilder("# Architecture of ")
                .append(report.rootPackage().isEmpty() ? "the project" : "`" + report.rootPackage() + "`")
                .append("\n\n")
                .append(report.classes())
                .append(" classes, analysed by Spring Inspector.\n\n## Styles\n\n");
        if (report.styles().isEmpty()) {
            md.append("No common style recognised.\n");
        } else {
            md.append("| Style | Confidence | Evidence |\n|-------|------------|----------|\n");
            report.styles()
                    .forEach(match -> md.append("| ")
                            .append(match.style().title())
                            .append(" | ")
                            .append(match.confidence())
                            .append(" | ")
                            .append(String.join("; ", match.evidence()))
                            .append(" |\n"));
        }
        md.append("\n## Parts and dependencies\n\nArrows show which part uses which, with the number of classes that"
                        + " do; red arrows are part of a cycle.\n\n```mermaid\n")
                .append(report.diagram())
                .append("```\n");
        if (!report.cycles().isEmpty()) {
            md.append("\n## Cycles\n\n");
            report.cycles()
                    .forEach(cycle ->
                            md.append("- ").append(String.join(" ⇄ ", cycle)).append('\n'));
        }
        if (!report.hotspots().isEmpty()) {
            md.append("\n## Classes that do too much\n\n| Class | Used by | Uses |\n|-------|---------|------|\n");
            report.hotspots()
                    .forEach(hotspot -> md.append("| `")
                            .append(hotspot.type())
                            .append("` | ")
                            .append(hotspot.usedBy())
                            .append(" | ")
                            .append(hotspot.uses())
                            .append(" |\n"));
        }
        md.append("\n## Suggestions\n\n");
        if (report.suggestions().isEmpty()) {
            md.append("None: the structure is consistent.\n");
        }
        for (Suggestion suggestion : report.suggestions()) {
            md.append("### ")
                    .append(suggestion.title())
                    .append("\n\n")
                    .append(suggestion.why())
                    .append("\n\n");
            suggestion
                    .steps()
                    .forEach(step -> md.append(step.startsWith("  ") ? "  - " : "- ")
                            .append(step.strip())
                            .append('\n'));
            md.append('\n');
        }
        return md.toString();
    }

    private static String json(ArchitectureReport report) {
        ObjectNode root = JsonRenderer.MAPPER.createObjectNode();
        root.put("rootPackage", report.rootPackage()).put("classes", report.classes());
        ArrayNode styles = root.putArray("styles");
        for (StyleMatch match : report.styles()) {
            ObjectNode node = styles.addObject()
                    .put("style", match.style().name())
                    .put("title", match.style().title())
                    .put("description", match.style().description())
                    .put("confidence", match.confidence());
            match.evidence().forEach(node.putArray("evidence")::add);
        }
        ArrayNode parts = root.putArray("parts");
        for (Part part : report.parts()) {
            ObjectNode node = parts.addObject().put("name", part.name()).put("classes", part.classes());
            ObjectNode dependsOn = node.putObject("dependsOn");
            part.dependsOn().forEach(dependsOn::put);
        }
        ArrayNode cycles = root.putArray("cycles");
        report.cycles().forEach(cycle -> cycle.forEach(cycles.addArray()::add));
        ArrayNode hotspots = root.putArray("hotspots");
        report.hotspots()
                .forEach(hotspot -> hotspots.addObject()
                        .put("type", hotspot.type())
                        .put("usedBy", hotspot.usedBy())
                        .put("uses", hotspot.uses()));
        ArrayNode suggestions = root.putArray("suggestions");
        for (Suggestion suggestion : report.suggestions()) {
            ObjectNode node =
                    suggestions.addObject().put("title", suggestion.title()).put("why", suggestion.why());
            suggestion.steps().forEach(node.putArray("steps")::add);
        }
        root.put("diagram", report.diagram());
        root.put("markdown", markdown(report));
        return JsonRenderer.MAPPER.writeValueAsString(root) + "\n";
    }

    private static String dependencies(Part part) {
        return part.dependsOn().entrySet().stream()
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
    }
}
