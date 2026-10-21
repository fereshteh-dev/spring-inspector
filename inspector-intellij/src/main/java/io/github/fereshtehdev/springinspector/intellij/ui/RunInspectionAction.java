package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import io.github.fereshtehdev.springinspector.intellij.run.InspectionScope;

/** Tools | Inspect Project with Spring Inspector: opens the tool window and starts a run on the whole project. */
public final class RunInspectionAction extends DumbAwareAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project != null) {
            InspectorPanel.withPanel(project, panel -> panel.run(InspectionScope.PROJECT));
        }
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
