package io.github.fereshtehdev.springinspector.intellij.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import java.time.Instant;

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

    /** Whether commits from the IDE are inspected first; set from the commit dialog. */
    public boolean checkBeforeCommit() {
        return values.checkBeforeCommit;
    }

    public void setCheckBeforeCommit(boolean check) {
        values.checkBeforeCommit = check;
    }

    /** Whether the IDE keeps the vulnerability advisories up to date in the background. */
    public boolean updateAdvisories() {
        return values.updateAdvisories;
    }

    public void setUpdateAdvisories(boolean update) {
        values.updateAdvisories = update;
    }

    /** Whether the IDE says when a newer Spring Inspector is on GitHub. */
    public boolean notifyAboutUpdates() {
        return values.notifyAboutUpdates;
    }

    public void setNotifyAboutUpdates(boolean notify) {
        values.notifyAboutUpdates = notify;
    }

    public Instant lastUpdateCheck() {
        return Instant.ofEpochMilli(values.lastUpdateCheck);
    }

    public void setLastUpdateCheck(Instant checked) {
        values.lastUpdateCheck = checked.toEpochMilli();
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
        public boolean checkBeforeCommit = true;
        public boolean updateAdvisories = true;
        public boolean notifyAboutUpdates = true;
        public long lastUpdateCheck;
    }
}
