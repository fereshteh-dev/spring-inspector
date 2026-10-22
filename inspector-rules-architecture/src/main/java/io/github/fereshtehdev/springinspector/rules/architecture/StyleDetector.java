package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.StyleMatch;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Recognises the architecture styles a project uses from its package names, class names, imports and annotations.
 * Each match lists its evidence, so a user can see why it was recognised, and how sure the detector is.
 */
final class StyleDetector {

    private static final Set<String> LAYER_NAMES = Set.of(
            "controller",
            "controllers",
            "web",
            "rest",
            "service",
            "services",
            "repository",
            "repositories",
            "dao",
            "persistence",
            "domain",
            "model",
            "entity",
            "entities");
    private static final Set<String> EVENT_IMPORTS = Set.of(
            "org.springframework.context.ApplicationEventPublisher",
            "org.springframework.context.event.EventListener",
            "org.springframework.transaction.event.TransactionalEventListener",
            "org.springframework.kafka.annotation.KafkaListener",
            "org.springframework.amqp.rabbit.annotation.RabbitListener",
            "org.springframework.jms.annotation.JmsListener",
            "org.springframework.modulith.events.ApplicationModuleListener",
            "org.springframework.cloud.stream.function.StreamBridge");

    private StyleDetector() {}

    static List<StyleMatch> detect(Architecture architecture, int buildModules) {
        List<StyleMatch> matches = new ArrayList<>();
        Map<String, Long> top = architecture.units().stream()
                .flatMap(unit -> architecture.topSegment(unit).stream())
                .collect(Collectors.groupingBy(segment -> segment, TreeMap::new, Collectors.counting()));
        Set<String> allSegments = new TreeSet<>();
        architecture.units().forEach(unit -> allSegments.addAll(Packages.segments(unit.packageName())));

        Set<String> topLayers =
                top.keySet().stream().filter(LAYER_NAMES::contains).collect(Collectors.toCollection(TreeSet::new));
        if (topLayers.size() >= 2) {
            matches.add(new StyleMatch(
                    ArchitectureStyle.LAYERED,
                    topLayers.size() >= 3 ? "high" : "medium",
                    List.of("top-level packages " + String.join(", ", topLayers))));
        }

        List<String> features = top.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2 && !Architecture.TECHNICAL_SEGMENTS.contains(entry.getKey()))
                .map(Map.Entry::getKey)
                .toList();
        if (features.size() >= 2) {
            matches.add(new StyleMatch(
                    ArchitectureStyle.PACKAGE_BY_FEATURE,
                    features.size() >= 3 ? "high" : "medium",
                    List.of("feature packages " + String.join(", ", features))));
        }

        boolean ports = allSegments.contains("port") || allSegments.contains("ports");
        boolean adapters = allSegments.contains("adapter") || allSegments.contains("adapters");
        if (allSegments.contains("domain") && (ports || adapters)) {
            matches.add(new StyleMatch(
                    ArchitectureStyle.HEXAGONAL,
                    ports && adapters ? "high" : "medium",
                    List.of("packages "
                            + present(allSegments, "domain", "port", "ports", "adapter", "adapters", "application"))));
        } else if (hasAny(allSegments, "usecase", "usecases", "application")
                && hasAny(allSegments, "domain", "entities", "core")
                && hasAny(
                        allSegments,
                        "infrastructure",
                        "presentation",
                        "framework",
                        "frameworks",
                        "gateway",
                        "gateways",
                        "interfaceadapters")) {
            matches.add(new StyleMatch(
                    ArchitectureStyle.CLEAN,
                    "medium",
                    List.of("packages "
                            + present(
                                    allSegments,
                                    "domain",
                                    "entities",
                                    "core",
                                    "usecase",
                                    "usecases",
                                    "application",
                                    "infrastructure",
                                    "presentation",
                                    "gateway",
                                    "gateways"))));
        }

        long modulith = java.util.stream.Stream.concat(
                        architecture.units().stream(), architecture.packageInfos().stream())
                .filter(unit -> unit.imports().stream()
                        .anyMatch(statement -> statement.name().startsWith("org.springframework.modulith")))
                .count();
        if (modulith > 0) {
            matches.add(new StyleMatch(
                    ArchitectureStyle.SPRING_MODULITH,
                    "high",
                    List.of(modulith + " " + plural(modulith, "file uses", "files use") + " Spring Modulith")));
        }

        long commands = countTypes(architecture, "Command", "CommandHandler");
        long queries = countTypes(architecture, "Query", "QueryHandler");
        if ((commands >= 2 && queries >= 2)
                || (hasAny(allSegments, "command", "commands") && hasAny(allSegments, "query", "queries"))) {
            matches.add(new StyleMatch(
                    ArchitectureStyle.CQRS,
                    commands >= 2 && queries >= 2 ? "high" : "medium",
                    List.of(commands + " command and " + queries + " query types")));
        }

        Set<String> events = new LinkedHashSet<>();
        for (JavaUnit unit : architecture.units()) {
            unit.imports().stream()
                    .map(statement -> statement.name())
                    .filter(EVENT_IMPORTS::contains)
                    .forEach(name -> events.add(name.substring(name.lastIndexOf('.') + 1)));
        }
        if (!events.isEmpty()) {
            matches.add(new StyleMatch(
                    ArchitectureStyle.EVENT_DRIVEN,
                    events.size() >= 2 ? "high" : "medium",
                    List.of("uses " + String.join(", ", events))));
        }

        if (buildModules > 0) {
            matches.add(new StyleMatch(
                    ArchitectureStyle.MULTI_MODULE,
                    "high",
                    List.of(buildModules + " build " + plural(buildModules, "module", "modules"))));
        }

        long applications = architecture.units().stream()
                .filter(unit -> unit.stereotypes().contains("SpringBootApplication"))
                .count();
        if (applications >= 2) {
            matches.add(new StyleMatch(
                    ArchitectureStyle.MULTIPLE_APPLICATIONS,
                    "high",
                    List.of(applications + " classes with @SpringBootApplication")));
        }
        return matches;
    }

    private static long countTypes(Architecture architecture, String... suffixes) {
        return architecture.units().stream()
                .filter(unit -> List.of(suffixes).stream().anyMatch(unit.typeName()::endsWith))
                .count();
    }

    private static boolean hasAny(Set<String> segments, String... names) {
        return List.of(names).stream().anyMatch(segments::contains);
    }

    private static String present(Set<String> segments, String... names) {
        return List.of(names).stream().filter(segments::contains).collect(Collectors.joining(", "));
    }

    private static String plural(long count, String one, String many) {
        return count == 1 ? one : many;
    }
}
