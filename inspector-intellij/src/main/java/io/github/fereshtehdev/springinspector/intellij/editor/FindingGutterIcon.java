package io.github.fereshtehdev.springinspector.intellij.editor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAwareAction;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import io.github.fereshtehdev.springinspector.intellij.ui.InspectorPanel;
import java.util.Objects;
import javax.swing.Icon;
import org.jspecify.annotations.Nullable;

/** The gutter icon of one finding: its severity, its explanation on hover, and its details on click. */
final class FindingGutterIcon extends GutterIconRenderer {

    private final Finding finding;

    FindingGutterIcon(Finding finding) {
        this.finding = finding;
    }

    static String tooltip(Finding finding) {
        String fix = finding.patch().isPresent() ? "\nAlt+Enter applies the fix." : "";
        return "Spring Inspector (" + finding.ruleId() + "): " + finding.whatsWrong() + "\n" + finding.howToFix() + fix;
    }

    @Override
    public Icon getIcon() {
        return switch (finding.severity()) {
            case BLOCKER -> AllIcons.General.Error;
            case MAJOR -> AllIcons.General.Warning;
            case MINOR -> AllIcons.General.Information;
            case INFO -> AllIcons.General.Note;
        };
    }

    @Override
    public String getTooltipText() {
        return tooltip(finding);
    }

    @Override
    public AnAction getClickAction() {
        return new DumbAwareAction() {
            @Override
            public void actionPerformed(AnActionEvent event) {
                if (event.getProject() != null) {
                    InspectorPanel.reveal(event.getProject(), finding);
                }
            }
        };
    }

    @Override
    public boolean isNavigateAction() {
        return true;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof FindingGutterIcon icon && icon.finding.equals(finding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(finding);
    }
}
