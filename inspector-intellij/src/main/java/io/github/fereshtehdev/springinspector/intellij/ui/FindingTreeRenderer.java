package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import io.github.fereshtehdev.springinspector.intellij.report.Report.CategoryReport;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Severity;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

/** Categories with their counts, and findings with a severity icon and their location. */
final class FindingTreeRenderer extends ColoredTreeCellRenderer {

    @Override
    public void customizeCellRenderer(
            JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Object item = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : value;
        switch (item) {
            case CategoryReport category -> {
                setIcon(statusIcon(category.status()));
                append(category.title(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                append("  " + Summaries.counts(category), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            case Finding finding -> {
                setIcon(severityIcon(finding.severity()));
                append(finding.whatsWrong());
                append("  " + finding.location(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            case null -> {}
            default -> append(item.toString(), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
        }
    }

    static Icon severityIcon(Severity severity) {
        return switch (severity) {
            case BLOCKER -> AllIcons.General.Error;
            case MAJOR -> AllIcons.General.Warning;
            case MINOR -> AllIcons.General.Information;
            case INFO -> AllIcons.General.Note;
        };
    }

    private static Icon statusIcon(String status) {
        return switch (status) {
            case "FAILED" -> AllIcons.General.Error;
            case "HINTS" -> AllIcons.General.Information;
            default -> AllIcons.General.InspectionsOK;
        };
    }
}
