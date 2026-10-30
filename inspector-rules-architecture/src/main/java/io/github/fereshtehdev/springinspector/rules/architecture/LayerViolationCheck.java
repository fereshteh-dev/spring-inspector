package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
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
 * A dependency against the direction of a layered Spring application: web → service → repository, with the domain
 * at the bottom. Controllers that use repositories skip the business rules and transactions of the service layer;
 * services, repositories and domain classes that depend on outer layers can no longer be reused or tested alone.
 */
public final class LayerViolationCheck implements Check {

    static final RuleId ID = RuleId.of("architecture.layer-violation");
    private static final Reference LAYERS = new Reference(
            "Spring Boot reference: structuring your code",
            URI.create("https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html"));

    private final Function<ProjectContext, ImportGraph> graphs;

    public LayerViolationCheck() {
        this(project -> ImportGraph.empty());
    }

    public LayerViolationCheck(Function<ProjectContext, ImportGraph> graphs) {
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
        Map<Layer, Set<Layer>> declared =
                DeclaredLayers.read(input.project().config().options(ID));
        Architecture architecture = new Architecture(graphs.apply(input.project()));
        List<Finding> findings = new ArrayList<>();
        for (JavaUnit unit : architecture.units()) {
            Layer from = architecture.layer(unit);
            for (Architecture.Use use : architecture.uses(unit)) {
                Layer to = architecture.layer(use.to());
                Optional<DefaultLayerRules.Violation> violation = declared.containsKey(from)
                        ? DeclaredLayers.violation(declared, from, to, unit, use.to())
                        : DefaultLayerRules.violation(from, to, unit, use.to());
                violation
                        .map(found -> ArchitectureFindings.onImport(
                                ID,
                                found.severity(),
                                Confidence.MEDIUM,
                                unit,
                                use.statement(),
                                found.description(),
                                new WhyItMatters(found.why(), List.of(LAYERS)),
                                found.howToFix()))
                        .ifPresent(findings::add);
            }
        }
        return findings;
    }
}
