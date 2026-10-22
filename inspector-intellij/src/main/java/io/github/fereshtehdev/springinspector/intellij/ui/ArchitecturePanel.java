package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.fereshtehdev.springinspector.intellij.report.ArchitectureView;
import io.github.fereshtehdev.springinspector.intellij.run.InspectorRunner;
import io.github.fereshtehdev.springinspector.intellij.run.InspectorRunner.ArchitectureOutcome;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JEditorPane;
import org.jspecify.annotations.Nullable;

/** The Architecture tab: the project's styles, parts, cycles and suggestions, and its diagram as Markdown. */
public final class ArchitecturePanel extends SimpleToolWindowPanel {

    private final Project project;
    private final JEditorPane content = new JEditorPane("text/html", "");
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<@Nullable ArchitectureView> latest = new AtomicReference<>();

    public ArchitecturePanel(Project project) {
        super(true, true);
        this.project = project;
        content.setEditable(false);
        content.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        content.setFont(UIUtil.getLabelFont());
        content.setBorder(JBUI.Borders.empty(4, 8));
        content.setText(FindingHtml.message("Analyse the architecture to see the styles this project follows, how its"
                + " parts depend on each other, and suggestions for a cleaner structure."));
        setContent(new JBScrollPane(content));
        DefaultActionGroup actions = new DefaultActionGroup(new AnalyseAction(), new DiagramAction());
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("SpringInspectorArchitecture", actions, true);
        toolbar.setTargetComponent(this);
        setToolbar(toolbar.getComponent());
    }

    public void analyse() {
        String basePath = project.getBasePath();
        if (basePath == null || !running.compareAndSet(false, true)) {
            return;
        }
        content.setText(FindingHtml.message("Analysing the architecture…"));
        new Task.Backgroundable(project, "Spring Inspector: analysing the architecture", true) {
            private ArchitectureOutcome outcome = new ArchitectureOutcome.Failed("The analysis did not run.");

            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                outcome = InspectorRunner.architecture(Path.of(basePath), indicator);
            }

            @Override
            public void onFinished() {
                running.set(false);
                switch (outcome) {
                    case ArchitectureOutcome.Described described -> {
                        latest.set(described.view());
                        content.setText(ArchitectureHtml.of(described.view()));
                    }
                    case ArchitectureOutcome.Failed failed -> content.setText(FindingHtml.message(failed.message()));
                }
                content.setCaretPosition(0);
            }
        }.queue();
    }

    /** Opens the report as Markdown; with IntelliJ's Markdown plugin, its preview draws the Mermaid diagram. */
    private void openDiagram() {
        ArchitectureView view = latest.get();
        if (view == null) {
            return;
        }
        LightVirtualFile file = new LightVirtualFile(
                "architecture.md", FileTypeManager.getInstance().getFileTypeByExtension("md"), view.markdown());
        file.setWritable(false);
        FileEditorManager.getInstance(project).openFile(file, true);
    }

    private final class AnalyseAction extends DumbAwareAction {

        AnalyseAction() {
            super("Analyse Architecture", "Describe the project's architecture and suggest improvements", AllIcons.Actions.Execute);
        }

        @Override
        public void actionPerformed(AnActionEvent event) {
            analyse();
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

    private final class DiagramAction extends DumbAwareAction {

        DiagramAction() {
            super(
                    "Open Diagram",
                    "Open the architecture report with its diagram as Markdown (the Markdown preview draws it)",
                    AllIcons.Actions.Diff);
        }

        @Override
        public void actionPerformed(AnActionEvent event) {
            openDiagram();
        }

        @Override
        public void update(AnActionEvent event) {
            event.getPresentation().setEnabled(latest.get() != null);
        }

        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }
}
