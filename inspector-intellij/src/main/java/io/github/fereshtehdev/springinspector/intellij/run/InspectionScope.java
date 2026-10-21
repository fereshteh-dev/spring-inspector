package io.github.fereshtehdev.springinspector.intellij.run;

/** What one run inspects, as the command line's options name it. */
public enum InspectionScope {
    PROJECT("--all", "Whole project"),
    STAGED("--staged", "Staged changes");

    private final String option;
    private final String title;

    InspectionScope(String option, String title) {
        this.option = option;
        this.title = title;
    }

    public String option() {
        return option;
    }

    public String title() {
        return title;
    }
}
