package io.github.fereshtehdev.springinspector.intellij.editor;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
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
 * scroll bar, and get a gutter icon that explains the finding. Highlights move with edits until the next run.
 */
final class EditorHighlights {

    private final Project project;
    private final InspectionResults results;
    private final Map<Document, List<RangeHighlighter>> drawn = new WeakHashMap<>();

    EditorHighlights(Project project, InspectionResults results) {
        this.project = project;
        this.results = results;
    }

    void refreshOpenFiles() {
        for (Document document : List.copyOf(drawn.keySet())) {
            clear(document);
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
        clear(document);
        MarkupModel markup = DocumentMarkupModel.forDocument(document, project, true);
        List<RangeHighlighter> added = new ArrayList<>();
        for (Finding finding : results.findingsIn(file)) {
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
            drawn.put(document, added);
        }
    }

    private void clear(Document document) {
        List<RangeHighlighter> highlighters = drawn.remove(document);
        if (highlighters != null) {
            highlighters.forEach(RangeHighlighter::dispose);
        }
    }

    private static TextAttributesKey attributes(Severity severity) {
        return switch (severity) {
            case BLOCKER -> CodeInsightColors.ERRORS_ATTRIBUTES;
            case MAJOR -> CodeInsightColors.WARNINGS_ATTRIBUTES;
            case MINOR -> CodeInsightColors.WEAK_WARNING_ATTRIBUTES;
            case INFO -> HighlightInfoType.INFORMATION.getAttributesKey();
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
