package io.github.fereshtehdev.springinspector.intellij.editor;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Alt+Enter on a finding that has a patch: shows the change as a preview and applies it. The patch is applied only if
 * the lines it changes are still as they were when the inspection ran.
 */
public final class ApplyFixIntention implements IntentionAction {

    @Override
    public String getText() {
        return "Apply Spring Inspector fix";
    }

    @Override
    public String getFamilyName() {
        return "Spring Inspector";
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, @Nullable PsiFile file) {
        return finding(project, editor, file).isPresent();
    }

    @Override
    public void invoke(Project project, Editor editor, @Nullable PsiFile file) {
        Optional<Finding> finding = finding(project, editor, file);
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        if (finding.isEmpty() || virtualFile == null) {
            return;
        }
        Document document = editor.getDocument();
        String original = document.getText();
        Optional<String> patched = patched(original, finding.get());
        if (patched.isEmpty()) {
            HintManager.getInstance()
                    .showErrorHint(editor, "The code changed since Spring Inspector ran; run it again to get a fresh fix.");
            return;
        }
        replaceChangedRegion(document, original, patched.get());
        InspectionResults.getInstance(project).resolved(finding.get(), virtualFile);
    }

    @Override
    public IntentionPreviewInfo generatePreview(Project project, Editor editor, PsiFile file) {
        String original = editor.getDocument().getText();
        return finding(project, editor, file)
                .flatMap(finding -> patched(original, finding))
                .<IntentionPreviewInfo>map(patched ->
                        new IntentionPreviewInfo.CustomDiff(file.getFileType(), original, patched))
                .orElse(IntentionPreviewInfo.EMPTY);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    private static Optional<Finding> finding(Project project, Editor editor, @Nullable PsiFile file) {
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        if (virtualFile == null) {
            return Optional.empty();
        }
        int line = editor.getDocument().getLineNumber(editor.getCaretModel().getOffset()) + 1;
        return InspectionResults.getInstance(project).fixableAt(virtualFile, line);
    }

    private static Optional<String> patched(String text, Finding finding) {
        return finding.patch().flatMap(patch -> PatchText.apply(text, patch, finding.file()));
    }

    /** Replaces only what differs, so the caret, folding and undo stay as close as possible to where they were. */
    private static void replaceChangedRegion(Document document, String original, String patched) {
        int prefix = 0;
        int limit = Math.min(original.length(), patched.length());
        while (prefix < limit && original.charAt(prefix) == patched.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < limit - prefix
                && original.charAt(original.length() - 1 - suffix) == patched.charAt(patched.length() - 1 - suffix)) {
            suffix++;
        }
        document.replaceString(prefix, original.length() - suffix, patched.substring(prefix, patched.length() - suffix));
    }
}
