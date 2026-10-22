package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * In a project organised by feature, a class that uses another feature's inner classes rather than the classes in
 * that feature's own package, its public API. This is the boundary Spring Modulith verifies.
 */
public final class FeatureInternalsCheck implements Check {

    static final RuleId ID = RuleId.of("architecture.feature-internals");
    private static final WhyItMatters WHY = new WhyItMatters(
            "When features use each other's inner classes, no feature can change its insides without breaking"
                    + " others, and the boundaries that make a large code base manageable fade away.",
            List.of(new Reference(
                    "Spring Modulith: application modules",
                    URI.create("https://docs.spring.io/spring-modulith/reference/fundamentals.html"))));

    private final Function<ProjectContext, ImportGraph> graphs;

    public FeatureInternalsCheck() {
        this(project -> ImportGraph.empty());
    }

    public FeatureInternalsCheck(Function<ProjectContext, ImportGraph> graphs) {
        this.graphs = graphs;
    }

    /**
     * Applies to one application organised by feature, where a feature's own package is its API. In a multi-module
     * build, sub-packages are ordinary module API, so the rule waits for Spring Modulith to say otherwise.
     */
    static boolean appliesTo(List<ArchitectureReport.StyleMatch> styles) {
        boolean byFeature = styles.stream().anyMatch(match -> match.style() == ArchitectureStyle.PACKAGE_BY_FEATURE);
        boolean modulith = styles.stream().anyMatch(match -> match.style() == ArchitectureStyle.SPRING_MODULITH);
        boolean multiModule = styles.stream().anyMatch(match -> match.style() == ArchitectureStyle.MULTI_MODULE);
        return byFeature && (modulith || !multiModule);
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
        if (!appliesTo(
                StyleDetector.detect(architecture, input.project().modules().size()))) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (JavaUnit unit : architecture.units()) {
            Optional<String> feature = architecture.featureOf(unit);
            for (Architecture.Use use : architecture.uses(unit)) {
                Optional<String> target = architecture.featureOf(use.to());
                if (feature.isEmpty()
                        || target.isEmpty()
                        || feature.equals(target)
                        || use.to().packageName().equals(architecture.featurePackage(target.get()))) {
                    continue;
                }
                String inner =
                        ArchitectureReport.relative(architecture, use.to().packageName());
                findings.add(ArchitectureFindings.onImport(
                        ID,
                        Severity.MINOR,
                        Confidence.MEDIUM,
                        unit,
                        use.statement(),
                        unit.typeName() + " (" + feature.get() + ") uses "
                                + use.to().typeName() + " from " + inner + ", inside the " + target.get() + " feature",
                        WHY,
                        "Use a class in "
                                + ArchitectureReport.relative(architecture, architecture.featurePackage(target.get()))
                                + " instead, and add one there if " + target.get() + " offers no such API yet."));
            }
        }
        return findings;
    }
}
