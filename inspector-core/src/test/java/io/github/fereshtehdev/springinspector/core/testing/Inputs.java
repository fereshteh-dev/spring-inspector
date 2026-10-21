package io.github.fereshtehdev.springinspector.core.testing;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedVersions;
import io.github.fereshtehdev.springinspector.core.domain.project.GatePolicy;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.project.PathPattern;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class Inputs {

    public static final String APP_FILE = "src/main/java/App.java";

    private final Set<Category> disabledCategories = EnumSet.noneOf(Category.class);
    private final Set<RuleId> disabledRules = new HashSet<>();
    private final Map<RuleId, Severity> severityOverrides = new HashMap<>();
    private Duration checkTimeout = InspectorConfig.DEFAULT_CHECK_TIMEOUT;
    private int maxFindingsPerCategory = InspectorConfig.DEFAULT_MAX_FINDINGS_PER_CATEGORY;
    private boolean changedLinesOnly = true;
    private GatePolicy gatePolicy = GatePolicy.defaults();
    private DetectedVersions versions = DetectedVersions.none();
    private ChangeSet changeSet = ChangeSet.of(FileChange.entireFile(APP_FILE));
    private final Map<Path, String> sources = new HashMap<>();
    private final List<PathPattern> ignoredPaths = new ArrayList<>();

    private Inputs() {}

    public static Inputs anInput() {
        return new Inputs();
    }

    public Inputs disablingRule(String ruleId) {
        disabledRules.add(RuleId.of(ruleId));
        return this;
    }

    public Inputs disablingCategory(Category category) {
        disabledCategories.add(category);
        return this;
    }

    public Inputs overridingSeverity(String ruleId, Severity severity) {
        severityOverrides.put(RuleId.of(ruleId), severity);
        return this;
    }

    public Inputs withCheckTimeout(Duration timeout) {
        this.checkTimeout = timeout;
        return this;
    }

    public Inputs showingAtMost(int findingsPerCategory) {
        this.maxFindingsPerCategory = findingsPerCategory;
        return this;
    }

    public Inputs reportingAllLines() {
        this.changedLinesOnly = false;
        return this;
    }

    public Inputs blockingOn(Severity severity) {
        this.gatePolicy = new GatePolicy(severity, gatePolicy.minimumScore());
        return this;
    }

    public Inputs requiringMinimumScore(int score) {
        this.gatePolicy = new GatePolicy(gatePolicy.blockOn(), OptionalInt.of(score));
        return this;
    }

    public Inputs withVersions(DetectedVersions versions) {
        this.versions = versions;
        return this;
    }

    public Inputs withChanges(ChangeSet changeSet) {
        this.changeSet = changeSet;
        return this;
    }

    public Inputs withSource(String path, String content) {
        sources.put(Path.of(path).normalize(), content);
        return this;
    }

    public Inputs ignoring(String glob) {
        ignoredPaths.add(PathPattern.of(glob));
        return this;
    }

    public InspectionInput build() {
        InspectorConfig config = new InspectorConfig(
                disabledCategories,
                disabledRules,
                severityOverrides,
                checkTimeout,
                maxFindingsPerCategory,
                changedLinesOnly,
                gatePolicy,
                Map.of(),
                ignoredPaths);
        ProjectContext project = new ProjectContext(Path.of("project"), versions, BuildTool.MAVEN, List.of(), config);
        Map<Path, String> files = Map.copyOf(sources);
        return new InspectionInput(
                project,
                changeSet,
                path -> Optional.ofNullable(files.get(path.normalize())).map(content -> new SourceFile(path, content)));
    }
}
