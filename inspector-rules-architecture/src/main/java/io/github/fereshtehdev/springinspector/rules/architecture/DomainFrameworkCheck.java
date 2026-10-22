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
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit.Import;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * In a hexagonal (ports and adapters, clean, onion) project, a domain class that imports a framework: Spring, JPA,
 * Jackson or the Servlet API. Projects organised in plain layers are not checked, as entities there are expected to
 * carry JPA annotations.
 */
public final class DomainFrameworkCheck implements Check {

    static final RuleId ID = RuleId.of("architecture.domain-depends-on-framework");

    /** Framework packages by the name the finding uses for them. */
    private static final Map<String, String> FRAMEWORKS = Map.of(
            "org.springframework.", "Spring",
            "jakarta.persistence.", "JPA",
            "javax.persistence.", "JPA",
            "org.hibernate.", "Hibernate",
            "tools.jackson.", "Jackson",
            "com.fasterxml.jackson.", "Jackson",
            "jakarta.servlet.", "the Servlet API",
            "jakarta.ws.rs.", "JAX-RS");

    private static final Set<String> OUTER_SEGMENTS = Set.of("adapter", "adapters", "infrastructure");
    private static final WhyItMatters WHY = new WhyItMatters(
            "In a hexagonal architecture the domain is plain Java, so business rules can be tested without starting"
                    + " Spring or a database, and frameworks can be upgraded or replaced in the adapters alone."
                    + " A framework import in the domain ties the core of the application to that framework.",
            List.of(new Reference(
                    "Hexagonal architecture (Alistair Cockburn)",
                    URI.create("https://alistair.cockburn.us/hexagonal-architecture/"))));

    private final Function<ProjectContext, ImportGraph> graphs;

    public DomainFrameworkCheck() {
        this(project -> ImportGraph.empty());
    }

    public DomainFrameworkCheck(Function<ProjectContext, ImportGraph> graphs) {
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
        if (!architecture.isHexagonal()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (JavaUnit unit : architecture.units()) {
            if (!isDomain(unit.packageName())) {
                continue;
            }
            for (Import statement : unit.imports()) {
                framework(statement.name())
                        .map(framework -> ArchitectureFindings.onImport(
                                ID,
                                Severity.MAJOR,
                                Confidence.HIGH,
                                unit,
                                statement,
                                "The domain class " + unit.typeName() + " depends on " + framework,
                                WHY,
                                "Keep " + framework + " in the adapters: map between plain domain objects and"
                                        + " framework types there, and define what the domain needs as an"
                                        + " interface (a port) in the domain."))
                        .ifPresent(findings::add);
            }
        }
        return findings;
    }

    private static boolean isDomain(String packageName) {
        List<String> segments = Packages.segments(packageName);
        return segments.contains("domain") && segments.stream().noneMatch(OUTER_SEGMENTS::contains);
    }

    private static Optional<String> framework(String imported) {
        return FRAMEWORKS.entrySet().stream()
                .filter(entry -> (imported + ".").startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
