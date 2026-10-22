package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Packages that depend on each other in a circle. Each import that closes the circle is reported, so the one a change
 * adds shows up in review.
 */
public final class PackageCycleCheck implements Check {

    static final RuleId ID = RuleId.of("architecture.package-cycle");
    private static final int LISTED_PACKAGES = 5;
    private static final WhyItMatters WHY = new WhyItMatters(
            "Packages in a cycle can only be understood, tested, reused or split off together: a change in one"
                    + " ripples around the whole circle. Cycles also stop a codebase from being split into modules"
                    + " (or Spring Modulith application modules) later.",
            List.of(new Reference(
                    "Spring Modulith: application modules",
                    URI.create("https://docs.spring.io/spring-modulith/reference/fundamentals.html"))));

    private final Function<ProjectContext, ImportGraph> graphs;

    public PackageCycleCheck() {
        this(project -> ImportGraph.empty());
    }

    public PackageCycleCheck(Function<ProjectContext, ImportGraph> graphs) {
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
        Map<String, Map<String, Architecture.Use>> dependencies = architecture.packageDependencies();
        List<Finding> findings = new ArrayList<>();
        for (Set<String> cycle : architecture.cycles()) {
            for (String from : cycle) {
                dependencies.getOrDefault(from, Map.of()).forEach((to, use) -> {
                    if (cycle.contains(to)) {
                        findings.add(ArchitectureFindings.onImport(
                                ID,
                                Severity.MINOR,
                                Confidence.HIGH,
                                use.from(),
                                use.statement(),
                                describe(from, to, cycle),
                                WHY,
                                "Break the cycle: move the types both packages need into one of them (or a new,"
                                        + " lower package), let one side depend on an interface the other"
                                        + " implements, or replace the call with an application event."));
                    }
                });
            }
        }
        return findings;
    }

    private static String describe(String from, String to, Set<String> cycle) {
        if (cycle.size() == 2) {
            return "Packages " + from + " and " + to + " depend on each other";
        }
        List<String> listed = cycle.stream().limit(LISTED_PACKAGES).toList();
        String more = cycle.size() > LISTED_PACKAGES ? " and " + (cycle.size() - LISTED_PACKAGES) + " more" : "";
        return "Package " + from + " is part of a dependency cycle of " + cycle.size() + " packages: "
                + String.join(", ", listed) + more;
    }
}
