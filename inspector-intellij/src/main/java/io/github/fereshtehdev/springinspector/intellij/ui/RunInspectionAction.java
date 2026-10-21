package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import io.github.fereshtehdev.springinspector.intellij.run.InspectionScope;

/** Tools | Inspect Project with Spring Inspector: opens the tool window and starts a run on the whole project. */
public final class RunInspectionAction extends DumbAwareAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(InspectorToolWindowFactory.ID);
        if (toolWindow == null) {
            return;
        }
        toolWindow.activate(() -> {
            Content content = toolWindow.getContentManager().getContent(0);
            if (content != null && content.getComponent() instanceof InspectorPanel panel) {
                panel.run(InspectionScope.PROJECT);
            }
        });
    }

    @Override
    public void update(AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null);
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
