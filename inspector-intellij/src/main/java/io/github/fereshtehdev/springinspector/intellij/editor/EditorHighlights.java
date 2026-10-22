package io.github.fereshtehdev.springinspector.intellij.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the latest findings into open editors: the lines are underlined like the IDE's own problems, marked in the
 * scroll bar, and get a gutter icon that explains the finding. Highlights move with edits until the next run. Each
 * editor of a file gets its own, through public editor APIs only.
 */
final class EditorHighlights {

    private final Project project;
    private final InspectionResults results;
    private final Map<Editor, List<RangeHighlighter>> drawn = new WeakHashMap<>();

    EditorHighlights(Project project, InspectionResults results) {
        this.project = project;
        this.results = results;
    }

    void refreshOpenFiles() {
        for (Editor editor : List.copyOf(drawn.keySet())) {
            clear(editor);
        }
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
            refresh(file);
        }
    }

    void refresh(VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) {
            return;
        }
        List<Finding> findings = results.findingsIn(file);
        for (Editor editor : EditorFactory.getInstance().getEditors(document, project)) {
            clear(editor);
            draw(editor, document, findings);
        }
    }

    private void draw(Editor editor, Document document, List<Finding> findings) {
        MarkupModel markup = editor.getMarkupModel();
        List<RangeHighlighter> added = new ArrayList<>();
        for (Finding finding : findings) {
            if (finding.startLine() > document.getLineCount()) {
                continue;
            }
            int startLine = finding.startLine() - 1;
            int endLine = Math.min(finding.endLine(), document.getLineCount()) - 1;
            int start = firstNonBlank(document, startLine);
            int end = Math.max(start, lastNonBlank(document, endLine));
            RangeHighlighter highlighter = markup.addRangeHighlighter(
                    attributes(finding.severity()),
                    start,
                    end,
                    HighlighterLayer.WARNING,
                    HighlighterTargetArea.EXACT_RANGE);
            highlighter.setErrorStripeTooltip(FindingGutterIcon.tooltip(finding));
            highlighter.setGutterIconRenderer(new FindingGutterIcon(finding));
            added.add(highlighter);
        }
        if (!added.isEmpty()) {
            drawn.put(editor, added);
        }
    }

    private void clear(Editor editor) {
        List<RangeHighlighter> highlighters = drawn.remove(editor);
        if (highlighters != null) {
            highlighters.stream().filter(RangeHighlighter::isValid).forEach(RangeHighlighter::dispose);
        }
    }

    private static TextAttributesKey attributes(Severity severity) {
        return switch (severity) {
            case BLOCKER -> CodeInsightColors.ERRORS_ATTRIBUTES;
            case MAJOR -> CodeInsightColors.WARNINGS_ATTRIBUTES;
            case MINOR -> CodeInsightColors.WEAK_WARNING_ATTRIBUTES;
            case INFO -> CodeInsightColors.INFORMATION_ATTRIBUTES;
        };
    }

    private static int firstNonBlank(Document document, int line) {
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        CharSequence text = document.getCharsSequence();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return start;
    }

    private static int lastNonBlank(Document document, int line) {
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        CharSequence text = document.getCharsSequence();
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }
}
