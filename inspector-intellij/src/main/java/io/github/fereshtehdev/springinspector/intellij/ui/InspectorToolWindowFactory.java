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
        Content content = ContentFactory.getInstance().createContent(new InspectorPanel(project), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
