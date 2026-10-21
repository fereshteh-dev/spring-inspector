package io.github.fereshtehdev.springinspector.intellij.settings;

import com.intellij.openapi.options.Configurable;
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
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        return !javaPath.getText().strip().equals(InspectorSettings.getInstance().javaPath());
    }

    @Override
    public void apply() {
        InspectorSettings.getInstance().setJavaPath(javaPath.getText());
    }

    @Override
    public void reset() {
        javaPath.setText(InspectorSettings.getInstance().javaPath());
    }
}
