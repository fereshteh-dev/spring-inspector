package io.github.fereshtehdev.springinspector.intellij.editor;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;

/** Draws the latest findings into a file when it is opened after the run. */
public final class HighlightOpenedFiles implements FileEditorManagerListener {

    @Override
    public void fileOpened(FileEditorManager source, VirtualFile file) {
        InspectionResults.getInstance(source.getProject()).highlights().refresh(file);
    }
}
