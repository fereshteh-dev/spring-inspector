package io.github.fereshtehdev.springinspector.intellij.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import io.github.fereshtehdev.springinspector.intellij.run.JavaRuntimes;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Settings | Tools | Spring Inspector. */
public final class InspectorConfigurable implements Configurable {

    private final JBTextField javaPath = new JBTextField();
    private final JBCheckBox checkBeforeCommit = new JBCheckBox("Check commits made from the IDE");

    @Override
    public String getDisplayName() {
        return "Spring Inspector";
    }

    @Override
    public JComponent createComponent() {
        JBLabel hint = new JBLabel(
                "Leave empty to use the IDE's own Java when it is version " + JavaRuntimes.REQUIRED_FEATURE
                        + " or newer.",
                UIUtil.ComponentStyle.SMALL,
                UIUtil.FontColor.BRIGHTER);
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Java " + JavaRuntimes.REQUIRED_FEATURE + "+ home or executable:", javaPath)
                .addComponentToRightColumn(hint)
                .addComponent(checkBeforeCommit)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        InspectorSettings settings = InspectorSettings.getInstance();
        return !javaPath.getText().strip().equals(settings.javaPath())
                || checkBeforeCommit.isSelected() != settings.checkBeforeCommit();
    }

    @Override
    public void apply() {
        InspectorSettings.getInstance().setJavaPath(javaPath.getText());
        InspectorSettings.getInstance().setCheckBeforeCommit(checkBeforeCommit.isSelected());
    }

    @Override
    public void reset() {
        javaPath.setText(InspectorSettings.getInstance().javaPath());
        checkBeforeCommit.setSelected(InspectorSettings.getInstance().checkBeforeCommit());
    }
}
