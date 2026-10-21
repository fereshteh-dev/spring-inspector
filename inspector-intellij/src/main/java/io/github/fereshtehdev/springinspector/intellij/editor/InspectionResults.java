package io.github.fereshtehdev.springinspector.intellij.editor;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.fereshtehdev.springinspector.intellij.report.Report;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The findings of the project's latest run, by file, for the editor: its highlights, gutter icons and the Alt+Enter
 * fix all read from here. A new run replaces them; applying a fix removes that finding.
 */
@Service(Service.Level.PROJECT)
public final class InspectionResults {

    private final Project project;
    private final Map<String, List<Finding>> byFile = new ConcurrentHashMap<>();
    private final EditorHighlights highlights;

    public InspectionResults(Project project) {
        this.project = project;
        this.highlights = new EditorHighlights(project, this);
    }

    public static InspectionResults getInstance(Project project) {
        return project.getService(InspectionResults.class);
    }

    /** Replaces the findings with a new run's and redraws every open editor. Call on the UI thread. */
    public void publish(Report report) {
        byFile.clear();
        report.categories().stream()
                .flatMap(category -> category.findings().stream())
                .forEach(finding -> byFile.computeIfAbsent(finding.file(), file -> new ArrayList<>())
                        .add(finding));
        highlights.refreshOpenFiles();
    }

    public List<Finding> findingsIn(VirtualFile file) {
        return relativePath(file).map(path -> byFile.getOrDefault(path, List.of())).orElse(List.of());
    }

    /** The finding with a patch whose lines contain {@code line} (1-based), if any. */
    public Optional<Finding> fixableAt(VirtualFile file, int line) {
        return findingsIn(file).stream()
                .filter(finding -> finding.patch().isPresent())
                .filter(finding -> finding.startLine() <= line && line <= finding.endLine())
                .findFirst();
    }

    /** Forgets a finding once its fix is applied, and redraws its file. Call on the UI thread. */
    public void resolved(Finding finding, VirtualFile file) {
        byFile.computeIfPresent(finding.file(), (path, findings) -> {
            List<Finding> remaining = new ArrayList<>(findings);
            remaining.remove(finding);
            return remaining.isEmpty() ? null : remaining;
        });
        highlights.refresh(file);
    }

    EditorHighlights highlights() {
        return highlights;
    }

    /** The file's path relative to the project root with {@code /}, as the report names files. */
    public Optional<String> relativePath(VirtualFile file) {
        String basePath = project.getBasePath();
        if (basePath == null || !file.isInLocalFileSystem()) {
            return Optional.empty();
        }
        Path root = Path.of(basePath).toAbsolutePath().normalize();
        Path path = Path.of(file.getPath()).toAbsolutePath().normalize();
        return path.startsWith(root)
                ? Optional.of(root.relativize(path).toString().replace('\\', '/'))
                : Optional.empty();
    }
}
