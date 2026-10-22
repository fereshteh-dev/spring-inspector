package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/** Creates the Spring Inspector tool window for a project. */
public final class InspectorToolWindowFactory implements ToolWindowFactory, DumbAware {

    public static final String ID = "Spring Inspector";

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ContentFactory factory = ContentFactory.getInstance();
        Content inspection = factory.createContent(new InspectorPanel(project), "Inspection", false);
        Content architecture = factory.createContent(new ArchitecturePanel(project), "Architecture", false);
        toolWindow.getContentManager().addContent(inspection);
        toolWindow.getContentManager().addContent(architecture);
    }
}
