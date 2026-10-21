package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.fereshtehdev.springinspector.intellij.report.Report;
import io.github.fereshtehdev.springinspector.intellij.report.Report.CategoryReport;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import io.github.fereshtehdev.springinspector.intellij.run.InspectionScope;
import io.github.fereshtehdev.springinspector.intellij.run.InspectorRunner;
import io.github.fereshtehdev.springinspector.intellij.run.RunOutcome;
import io.github.fereshtehdev.springinspector.intellij.settings.InspectorConfigurable;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.jspecify.annotations.Nullable;

/** The Spring Inspector tool window: run buttons, the report card as a tree, and the selected finding's details. */
public final class InspectorPanel extends SimpleToolWindowPanel {

    private final Project project;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final Tree tree = new Tree(model);
    private final JEditorPane details = new JEditorPane("text/html", "");
    private final JBLabel headline = new JBLabel();
    private final AtomicBoolean running = new AtomicBoolean();

    public InspectorPanel(Project project) {
        super(true, true);
        this.project = project;
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new FindingTreeRenderer());
        tree.addTreeSelectionListener(event -> showSelection());
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(MouseEvent event) {
                return openSelection();
            }
        }.installOn(tree);
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER && openSelection()) {
                    event.consume();
                }
            }
        });

        details.setEditable(false);
        details.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        details.setFont(UIUtil.getLabelFont());
        details.setBorder(JBUI.Borders.empty(4, 8));
        details.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getURL() != null) {
                String protocol = event.getURL().getProtocol();
                if (protocol.equals("https") || protocol.equals("http")) {
                    BrowserUtil.browse(event.getURL().toString());
                }
            }
        });

        JBSplitter splitter = new JBSplitter(false, 0.5f);
        splitter.setFirstComponent(new JBScrollPane(tree));
        splitter.setSecondComponent(new JBScrollPane(details));
        headline.setBorder(JBUI.Borders.empty(4, 8));
        JPanel content = new JPanel(new BorderLayout());
        content.add(headline, BorderLayout.NORTH);
        content.add(splitter, BorderLayout.CENTER);
        setContent(content);

        DefaultActionGroup actions = new DefaultActionGroup(
                new RunAction(InspectionScope.PROJECT, AllIcons.Actions.Execute),
                new RunAction(InspectionScope.STAGED, AllIcons.Actions.Commit),
                new SettingsAction());
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("SpringInspector", actions, true);
        toolbar.setTargetComponent(this);
        setToolbar(toolbar.getComponent());

        headline.setText("Run Spring Inspector on the whole project or on your staged changes.");
        details.setText(FindingHtml.message("Select a finding to see what is wrong, why it matters and how to fix it."
                + "\nDouble-click or press Enter to open it in the editor."));
    }

    /** Starts a run in the background, unless one is already running. */
    public void run(InspectionScope scope) {
        String basePath = project.getBasePath();
        if (basePath == null || !running.compareAndSet(false, true)) {
            return;
        }
        FileDocumentManager.getInstance().saveAllDocuments();
        headline.setText("Inspecting: " + scope.title() + "…");
        new Task.Backgroundable(project, "Spring Inspector: " + scope.title(), true) {
            private RunOutcome outcome = new RunOutcome.Failed("The inspection did not run.");

            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                outcome = InspectorRunner.run(Path.of(basePath), scope, indicator);
            }

            @Override
            public void onFinished() {
                running.set(false);
                show(outcome);
            }
        }.queue();
    }

    private void show(RunOutcome outcome) {
        root.removeAllChildren();
        switch (outcome) {
            case RunOutcome.Completed completed -> {
                Report report = completed.report();
                for (CategoryReport category : report.categories()) {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(category);
                    category.findings().forEach(finding -> node.add(new DefaultMutableTreeNode(finding, false)));
                    root.add(node);
                }
                List<String> notes = Summaries.notes(report, completed.warnings());
                notes.forEach(note -> root.add(new DefaultMutableTreeNode(note, false)));
                headline.setText(Summaries.headline(report));
                headline.setIcon(report.blocks() ? AllIcons.General.Error : AllIcons.General.InspectionsOK);
                details.setText(FindingHtml.message(
                        notes.isEmpty() ? "Select a finding to see its details." : String.join("\n", notes)));
            }
            case RunOutcome.Failed failed -> {
                headline.setText("Spring Inspector did not finish");
                headline.setIcon(AllIcons.General.Warning);
                details.setText(FindingHtml.message(failed.message()));
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("Spring Inspector")
                        .createNotification(failed.message(), NotificationType.WARNING)
                        .notify(project);
            }
        }
        model.reload();
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    private void showSelection() {
        selectedFinding().ifPresent(finding -> {
            details.setText(FindingHtml.of(finding));
            details.setCaretPosition(0);
        });
    }

    private boolean openSelection() {
        Optional<Finding> finding = selectedFinding();
        String basePath = project.getBasePath();
        if (finding.isEmpty() || basePath == null) {
            return false;
        }
        Path projectRoot = Path.of(basePath).toAbsolutePath().normalize();
        Path file = projectRoot.resolve(finding.get().file()).normalize();
        if (!file.startsWith(projectRoot)) {
            return false;
        }
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
        if (virtualFile == null) {
            return false;
        }
        new OpenFileDescriptor(project, virtualFile, finding.get().startLine() - 1, 0).navigate(true);
        return true;
    }

    private Optional<Finding> selectedFinding() {
        TreePath path = tree.getSelectionPath();
        @Nullable Object selected = path == null ? null : path.getLastPathComponent();
        if (selected instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Finding finding) {
            return Optional.of(finding);
        }
        return Optional.empty();
    }

    private final class RunAction extends DumbAwareAction {

        private final InspectionScope scope;

        RunAction(InspectionScope scope, Icon icon) {
            super("Inspect " + scope.title(), "Run Spring Inspector on the " + scope.title().toLowerCase(Locale.ROOT), icon);
            this.scope = scope;
        }

        @Override
        public void actionPerformed(AnActionEvent event) {
            run(scope);
        }

        @Override
        public void update(AnActionEvent event) {
            event.getPresentation().setEnabled(!running.get());
        }

        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    private final class SettingsAction extends DumbAwareAction {

        SettingsAction() {
            super("Settings", "Open Spring Inspector settings", AllIcons.General.Settings);
        }

        @Override
        public void actionPerformed(AnActionEvent event) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, InspectorConfigurable.class);
        }

        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }
}
