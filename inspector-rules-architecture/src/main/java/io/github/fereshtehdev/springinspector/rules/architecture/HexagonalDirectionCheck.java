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
import java.util.Set;
import java.util.function.Function;

/**
 * In a hexagonal project, dependencies that point the wrong way: the core (domain, application, ports, use cases) using
 * an adapter, or one adapter using another. Imports that {@code architecture.layer-violation} already reports are left
 * to that rule, so one import does not get two findings.
 */
public final class HexagonalDirectionCheck implements Check {

    static final RuleId ID = RuleId.of("architecture.hexagonal-direction");

    private static final Set<String> CORE_SEGMENTS =
            Set.of("domain", "application", "port", "ports", "usecase", "usecases");
    private static final Set<String> ADAPTER_SEGMENTS = Set.of("adapter", "adapters");
    private static final Set<String> OUTER_SEGMENTS = Set.of("adapter", "adapters", "infrastructure");
    private static final Set<String> DIRECTION_SEGMENTS =
            Set.of("in", "out", "inbound", "outbound", "driving", "driven", "primary", "secondary");
    private static final Set<String> SHARED_SEGMENTS = Set.of("common", "shared", "support", "util", "utils", "config");

    private static final WhyItMatters CORE_WHY = new WhyItMatters(
            "In a hexagonal architecture, dependencies point inwards: adapters know the core, and the core knows only"
                    + " its ports. When the core uses an adapter, it can no longer be tested alone, and replacing the"
                    + " technology behind that adapter means changing business code.",
            List.of(new Reference(
                    "Hexagonal architecture (Alistair Cockburn)",
                    URI.create("https://alistair.cockburn.us/hexagonal-architecture/"))));

    private static final WhyItMatters ADAPTER_WHY = new WhyItMatters(
            "Adapters connect the core to one technology each. When a web adapter uses a persistence adapter directly,"
                    + " the request skips the application core and its rules, and the two technologies can no longer"
                    + " change independently.",
            CORE_WHY.references());

    private final Function<ProjectContext, ImportGraph> graphs;

    public HexagonalDirectionCheck() {
        this(project -> ImportGraph.empty());
    }

    public HexagonalDirectionCheck(Function<ProjectContext, ImportGraph> graphs) {
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
            for (Architecture.Use use : architecture.uses(unit)) {
                boolean reportedAsLayerViolation = DefaultLayerRules.violation(
                                architecture.layer(unit), architecture.layer(use.to()), unit, use.to())
                        .isPresent();
                if (!reportedAsLayerViolation) {
                    problem(unit, use).ifPresent(findings::add);
                }
            }
        }
        return findings;
    }

    private static Optional<Finding> problem(JavaUnit unit, Architecture.Use use) {
        if (isCore(unit.packageName()) && isOuter(use.to().packageName())) {
            return Optional.of(ArchitectureFindings.onImport(
                    ID,
                    Severity.MAJOR,
                    Confidence.HIGH,
                    unit,
                    use.statement(),
                    "The core class " + unit.typeName() + " depends on the adapter "
                            + use.to().typeName(),
                    CORE_WHY,
                    "Define what " + unit.typeName() + " needs as a port (an interface) in the core, and let "
                            + use.to().typeName() + " implement it."));
        }
        Optional<String> from = adapterOf(unit.packageName());
        Optional<String> to = adapterOf(use.to().packageName());
        if (from.isPresent() && to.isPresent() && !from.equals(to)) {
            return Optional.of(ArchitectureFindings.onImport(
                    ID,
                    Severity.MINOR,
                    Confidence.MEDIUM,
                    unit,
                    use.statement(),
                    "The " + from.get() + " adapter class " + unit.typeName() + " uses "
                            + use.to().typeName() + " from the " + to.get() + " adapter",
                    ADAPTER_WHY,
                    "Go through a use case or port in the core. Move types that both adapters really need into the"
                            + " core."));
        }
        return Optional.empty();
    }

    private static boolean isCore(String packageName) {
        List<String> segments = Packages.segments(packageName);
        return segments.stream().anyMatch(CORE_SEGMENTS::contains)
                && segments.stream().noneMatch(OUTER_SEGMENTS::contains);
    }

    private static boolean isOuter(String packageName) {
        return Packages.segments(packageName).stream().anyMatch(OUTER_SEGMENTS::contains);
    }

    /**
     * The adapter a package belongs to, such as {@code in.web} in {@code com.acme.adapter.in.web.dto}; empty outside
     * adapters and for packages that adapters share.
     */
    private static Optional<String> adapterOf(String packageName) {
        List<String> segments = Packages.segments(packageName);
        for (int i = 0; i < segments.size(); i++) {
            if (ADAPTER_SEGMENTS.contains(segments.get(i))) {
                List<String> below = segments.subList(i + 1, segments.size());
                if (below.isEmpty() || SHARED_SEGMENTS.contains(below.getFirst())) {
                    return Optional.empty();
                }
                boolean directional = DIRECTION_SEGMENTS.contains(below.getFirst()) && below.size() > 1;
                return Optional.of(directional ? below.get(0) + "." + below.get(1) : below.getFirst());
            }
        }
        return Optional.empty();
    }
}
