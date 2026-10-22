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
                Optional<Violation> violation = declared.containsKey(from)
                        ? DeclaredLayers.violation(declared, from, to, unit, use.to())
                        : violation(from, to, unit, use.to());
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

    static Optional<Violation> violation(Layer from, Layer to, JavaUnit unit, JavaUnit target) {
        String user = unit.typeName();
        String used = target.typeName();
        return switch (from) {
            case WEB ->
                to == Layer.REPOSITORY
                        ? Optional.of(new Violation(
                                Severity.MINOR,
                                user + " uses the repository " + used + " directly",
                                "Controllers that call repositories skip the service layer, where business rules,"
                                        + " validation and transactions live. The same logic then gets copied"
                                        + " into several controllers, and it cannot be tested without HTTP.",
                                "Call a service method instead, and let the service use " + used + "."))
                        : Optional.empty();
            case SERVICE ->
                to == Layer.WEB
                        ? Optional.of(new Violation(
                                Severity.MAJOR,
                                user + " depends on the web class " + used,
                                "A service that knows about controllers or web request types is tied to HTTP: it"
                                        + " cannot be used from a scheduled job, a message listener or a test"
                                        + " without the web layer.",
                                "Move " + used + " into the service layer if it is a plain data type, or map"
                                        + " between web types and service types in the controller."))
                        : Optional.empty();
            case REPOSITORY ->
                to == Layer.WEB || to == Layer.SERVICE
                        ? Optional.of(new Violation(
                                Severity.MAJOR,
                                user + " depends on the " + to.label() + " class " + used,
                                "Repositories sit below services and controllers. Depending upwards creates a"
                                        + " cycle between layers, so neither can change or be tested alone.",
                                "Keep " + user + " to data access; move what it needs from " + used
                                        + " down into the domain, or let the service pass it in."))
                        : Optional.empty();
            case DOMAIN ->
                to == Layer.WEB || to == Layer.SERVICE || to == Layer.REPOSITORY
                        ? Optional.of(new Violation(
                                Severity.MAJOR,
                                "The domain class " + user + " depends on the " + to.label() + " class " + used,
                                "Domain classes are the heart of the application and should depend on nothing"
                                        + " above them. When they do, every change in the outer layers can break"
                                        + " the business model.",
                                "Turn the dependency around: let the " + to.label() + " layer use " + user
                                        + ", or define an interface in the domain that " + used + " implements."))
                        : Optional.empty();
            case CONFIG, OTHER -> Optional.empty();
        };
    }

    record Violation(Severity severity, String description, String why, String howToFix) {}
}
