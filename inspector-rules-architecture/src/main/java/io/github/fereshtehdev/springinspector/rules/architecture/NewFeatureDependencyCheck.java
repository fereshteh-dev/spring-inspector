package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A change that makes one feature depend on another for the first time: every import creating that dependency is
 * on a changed line. A reviewer should see such decisions, as they shape the architecture. Whole-project runs are
 * skipped, since every line counts as changed there.
 */
public final class NewFeatureDependencyCheck implements Check {

    static final RuleId ID = RuleId.of("architecture.new-feature-dependency");
    private static final double WHOLE_PROJECT = 0.9;
    private static final WhyItMatters WHY = new WhyItMatters(
            "Dependencies between features are architecture decisions: each one makes the features harder to change,"
                    + " test or split apart independently. A new one deserves a conscious yes in review.",
            List.of());

    private final Function<ProjectContext, ImportGraph> graphs;

    public NewFeatureDependencyCheck() {
        this(project -> ImportGraph.empty());
    }

    public NewFeatureDependencyCheck(Function<ProjectContext, ImportGraph> graphs) {
        this.graphs = graphs;
    }

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.ARCHITECTURE;
    }

    @Override
    public List<Finding> inspect(InspectionInput input) {
        if (!ArchitectureFindings.touchesJava(input)) {
            return List.of();
        }
        Architecture architecture = new Architecture(graphs.apply(input.project()));
        if (isWholeProject(architecture, input.changeSet())) {
            return List.of();
        }
        Map<String, List<Architecture.Use>> links = new LinkedHashMap<>();
        for (JavaUnit unit : architecture.units()) {
            Optional<String> feature = architecture.featureOf(unit);
            for (Architecture.Use use : architecture.uses(unit)) {
                Optional<String> target = architecture.featureOf(use.to());
                if (feature.isPresent() && target.isPresent() && !feature.equals(target)) {
                    links.computeIfAbsent(feature.get() + " → " + target.get(), key -> new ArrayList<>())
                            .add(use);
                }
            }
        }
        List<Finding> findings = new ArrayList<>();
        links.forEach((link, uses) -> {
            boolean allNew = uses.stream().allMatch(use -> input.changeSet().touches(location(use)));
            if (allNew) {
                Architecture.Use first = uses.getFirst();
                String from = architecture.featureOf(first.from()).orElse("");
                String to = architecture.featureOf(first.to()).orElse("");
                findings.add(ArchitectureFindings.onImport(
                        ID,
                        Severity.INFO,
                        Confidence.HIGH,
                        first.from(),
                        first.statement(),
                        "This change makes " + from + " depend on " + to + " for the first time",
                        WHY,
                        "If " + from + " really needs " + to + ", keep it and say so in the review; otherwise publish"
                                + " an event or move the shared logic so the features stay independent."));
            }
        });
        return findings;
    }

    private static Location location(Architecture.Use use) {
        return new Location(use.from().file(), LineRange.line(use.statement().line()));
    }

    /** Most Java files of the project are in the change: a whole-project run, not a change to review. */
    private static boolean isWholeProject(Architecture architecture, ChangeSet changes) {
        long javaFiles = architecture.units().size();
        long changed = changes.files().stream()
                .filter(file -> file.path().toString().endsWith(".java"))
                .count();
        return javaFiles > 0 && changed >= Math.max(1, Math.round(javaFiles * WHOLE_PROJECT));
    }
}
