package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.fereshtehdev.springinspector.intellij.editor.InspectionResults;
import io.github.fereshtehdev.springinspector.intellij.run.InspectionScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Inspect with Spring Inspector, from the right-click menu of files and folders in the project view and of the
 * editor: reviews the selected files, and every project file below the selected folders.
 */
public final class InspectSelectionAction extends DumbAwareAction {

    static final int MAX_FILES = 2_000;

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile[] selection = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || selection == null) {
            return;
        }
        List<Path> files = files(project, selection);
        if (!files.isEmpty()) {
            InspectorPanel.withPanel(project, panel -> panel.run(InspectionScope.FILES, files));
        }
    }

    @Override
    public void update(AnActionEvent event) {
        VirtualFile[] selection = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        event.getPresentation()
                .setEnabledAndVisible(event.getProject() != null && selection != null && selection.length > 0);
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /** The selected files and the project files inside the selected folders, relative to the project root. */
    private static List<Path> files(Project project, VirtualFile[] selection) {
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        InspectionResults results = InspectionResults.getInstance(project);
        Set<String> paths = new LinkedHashSet<>();
        for (VirtualFile selected : selection) {
            VfsUtilCore.iterateChildrenRecursively(
                    selected,
                    file -> index.isInContent(file) && !index.isExcluded(file),
                    file -> {
                        if (!file.isDirectory()) {
                            results.relativePath(file).ifPresent(paths::add);
                        }
                        return paths.size() < MAX_FILES;
                    });
        }
        List<Path> files = new ArrayList<>();
        paths.forEach(path -> files.add(Path.of(path)));
        return files;
    }
}
