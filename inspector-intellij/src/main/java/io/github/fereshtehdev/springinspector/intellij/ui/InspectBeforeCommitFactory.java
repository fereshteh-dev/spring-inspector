package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import io.github.fereshtehdev.springinspector.intellij.editor.InspectionResults;
import io.github.fereshtehdev.springinspector.intellij.run.InspectionScope;
import io.github.fereshtehdev.springinspector.intellij.run.InspectorRunner;
import io.github.fereshtehdev.springinspector.intellij.run.RunOutcome;
import io.github.fereshtehdev.springinspector.intellij.settings.InspectorSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;

/**
 * Inspects the files of a commit made from the IDE before it is made, and asks before committing when the gate
 * blocks, like the git hook does on the command line. It can be switched off in the commit dialog's options.
 */
public final class InspectBeforeCommitFactory extends CheckinHandlerFactory {

    @Override
    public CheckinHandler createHandler(CheckinProjectPanel panel, CommitContext commitContext) {
        return new InspectBeforeCommit(panel);
    }

    private static final class InspectBeforeCommit extends CheckinHandler {

        private final CheckinProjectPanel panel;

        InspectBeforeCommit(CheckinProjectPanel panel) {
            this.panel = panel;
        }

        @Override
        public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
            JBCheckBox box = new JBCheckBox("Check with Spring Inspector");
            return new RefreshableOnComponent() {
                @Override
                public JComponent getComponent() {
                    return box;
                }

                @Override
                public void saveState() {
                    InspectorSettings.getInstance().setCheckBeforeCommit(box.isSelected());
                }

                @Override
                public void restoreState() {
                    box.setSelected(InspectorSettings.getInstance().checkBeforeCommit());
                }
            };
        }

        @Override
        public ReturnResult beforeCheckin() {
            Project project = panel.getProject();
            String basePath = project.getBasePath();
            if (!InspectorSettings.getInstance().checkBeforeCommit() || basePath == null) {
                return ReturnResult.COMMIT;
            }
            InspectionResults results = InspectionResults.getInstance(project);
            List<Path> files = panel.getVirtualFiles().stream()
                    .filter(VirtualFile::isValid)
                    .flatMap(file -> results.relativePath(file).stream())
                    .map(Path::of)
                    .toList();
            if (files.isEmpty()) {
                return ReturnResult.COMMIT;
            }
            RunOutcome outcome = ProgressManager.getInstance()
                    .runProcessWithProgressSynchronously(
                            () -> InspectorRunner.run(Path.of(basePath), InspectionScope.FILES, files, indicator()),
                            "Spring Inspector: checking the commit",
                            true,
                            project);
            return decide(project, outcome);
        }

        private static ReturnResult decide(Project project, RunOutcome outcome) {
            return switch (outcome) {
                case RunOutcome.Completed completed when !completed.report().blocks() -> ReturnResult.COMMIT;
                case RunOutcome.Completed completed -> {
                    int answer = Messages.showYesNoCancelDialog(
                            project,
                            "Spring Inspector found problems that should be fixed before committing:\n\n"
                                    + String.join("\n", completed.report().gateReasons()),
                            "Spring Inspector",
                            "Review",
                            "Commit Anyway",
                            "Cancel",
                            Messages.getWarningIcon());
                    if (answer == Messages.YES) {
                        InspectorPanel.withPanel(project, inspector -> inspector.show(completed));
                    }
                    yield answer == Messages.NO ? ReturnResult.COMMIT : ReturnResult.CANCEL;
                }
                case RunOutcome.Failed failed -> {
                    int answer = Messages.showYesNoDialog(
                            project,
                            failed.message() + "\n\nCommit without the check?",
                            "Spring Inspector",
                            "Commit Anyway",
                            "Cancel",
                            Messages.getWarningIcon());
                    yield answer == Messages.YES ? ReturnResult.COMMIT : ReturnResult.CANCEL;
                }
            };
        }

        private static ProgressIndicator indicator() {
            return Objects.requireNonNullElseGet(
                    ProgressManager.getInstance().getProgressIndicator(), EmptyProgressIndicator::new);
        }
    }
}
