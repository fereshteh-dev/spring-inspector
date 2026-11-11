package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeScope;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedProject;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.port.ProjectDetector;
import io.github.fereshtehdev.springinspector.document.DocumentComposer;
import io.github.fereshtehdev.springinspector.document.ProjectFacts;
import io.github.fereshtehdev.springinspector.document.content.Document;
import io.github.fereshtehdev.springinspector.document.design.ProjectSources;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign;
import io.github.fereshtehdev.springinspector.document.render.HtmlWriter;
import io.github.fereshtehdev.springinspector.document.render.MarkdownWriter;
import io.github.fereshtehdev.springinspector.document.render.PdfWriter;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "document",
        description = "Write a document about the project as PDF, HTML or Markdown: what it is, its architecture"
                + " with diagrams, how requests flow, its API, data model, security and AI use, and what to fix.",
        sortOptions = false)
final class DocumentCommand implements Callable<Integer> {

    enum Format {
        PDF("pdf"),
        HTML("html"),
        MARKDOWN("md");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }
    }

    static final String FILE_NAME = "spring-inspector-document";

    @Option(names = "--project", paramLabel = "<dir>", description = "Project directory. Default: current directory.")
    @Nullable
    Path project;

    @Option(names = "--format", paramLabel = "<format>", description = "pdf, html or markdown. Default: pdf.")
    Format format = Format.PDF;

    @Option(
            names = "--output",
            paramLabel = "<file>",
            description = "File to write. Default: " + FILE_NAME + ".pdf, .html or .md in the project directory.")
    @Nullable
    Path output;

    @Option(
            names = "--no-findings",
            description = "Leave out the code quality chapter. Faster, because the project is not inspected.")
    boolean noFindings;

    private final Inspector inspector;
    private final ProjectDetector detector;
    private final Function<Path, ImportGraph> scanner;
    private final Path workingDirectory;
    private final String version;
    private final Clock clock;
    private final PrintWriter out;

    DocumentCommand(
            Inspector inspector,
            ProjectDetector detector,
            Function<Path, ImportGraph> scanner,
            Path workingDirectory,
            String version,
            Clock clock,
            PrintWriter out) {
        this.inspector = inspector;
        this.detector = detector;
        this.scanner = scanner;
        this.workingDirectory = workingDirectory;
        this.version = version;
        this.clock = clock;
        this.out = out;
    }

    @Override
    public Integer call() throws IOException {
        Path root = (project == null ? workingDirectory : workingDirectory.resolve(project))
                .toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }
        Optional<ReportCard> quality = Optional.empty();
        DetectedProject detected;
        if (noFindings) {
            detected = detector.detect(root);
        } else {
            InspectionRun run = inspector.inspect(root, ChangeScope.all(), true, Optional.empty());
            detected = run.project();
            quality = Optional.of(run.report());
        }
        ImportGraph graph = scanner.apply(root);
        SystemDesign design = ProjectSources.scan(root, graph);
        ProjectFacts facts = new ProjectFacts(
                design.settings().applicationName().orElse(folderName(root)),
                detected.versions(),
                detected.buildTool(),
                detected.modules().size(),
                ArchitectureReport.of(graph, detected.modules().size()),
                design,
                quality);
        Document document = new DocumentComposer(version, clock).compose(facts);
        Path target = output == null
                ? root.resolve(FILE_NAME + "." + format.extension)
                : workingDirectory.resolve(output).toAbsolutePath().normalize();
        Path folder = target.getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }
        Files.write(target, bytes(document));
        out.println("Wrote " + target);
        out.flush();
        return ExitCodes.OK;
    }

    private byte[] bytes(Document document) {
        return switch (format) {
            case PDF -> PdfWriter.write(document);
            case HTML -> HtmlWriter.write(document).getBytes(StandardCharsets.UTF_8);
            case MARKDOWN -> MarkdownWriter.write(document).getBytes(StandardCharsets.UTF_8);
        };
    }

    private static String folderName(Path root) {
        Path name = root.getFileName();
        return name == null ? "Project" : name.toString();
    }
}
