package io.github.fereshtehdev.springinspector.intellij.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

/** The plugin's settings for this machine, kept with the IDE's own settings. */
@State(name = "SpringInspectorSettings", storages = @Storage("spring-inspector.xml"))
public final class InspectorSettings implements PersistentStateComponent<InspectorSettings.Values> {

    private Values values = new Values();

    public static InspectorSettings getInstance() {
        return ApplicationManager.getApplication().getService(InspectorSettings.class);
    }

    /** A Java 25+ home or executable; blank to use the IDE's own Java when it is new enough. */
    public String javaPath() {
        return values.javaPath;
    }

    public void setJavaPath(String javaPath) {
        values.javaPath = javaPath.strip();
    }

    @Override
    public Values getState() {
        return values;
    }

    @Override
    public void loadState(Values state) {
        values = state;
    }

    /** The stored form; the IDE writes its public fields to XML. */
    public static final class Values {
        public String javaPath = "";
    }
}
