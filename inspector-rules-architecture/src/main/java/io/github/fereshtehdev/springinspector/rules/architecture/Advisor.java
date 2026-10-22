package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.Hotspot;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.StyleMatch;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.Suggestion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns what the analysis found into suggestions for a cleaner structure. Every suggestion names the project's own
 * classes and packages and lists concrete steps; none of them is applied automatically.
 */
final class Advisor {

    private static final int LISTED = 5;
    private static final int MIN_FEATURES = 3;
    private static final int MIN_CLASSES_FOR_STRUCTURE = 20;
    private static final Set<Layer> BUSINESS_LAYERS =
            EnumSet.of(Layer.WEB, Layer.SERVICE, Layer.REPOSITORY, Layer.DOMAIN);

    private Advisor() {}

    static List<Suggestion> suggest(
            Architecture architecture, List<StyleMatch> styles, List<List<String>> cycles, List<Hotspot> hotspots) {
        Set<ArchitectureStyle> found = EnumSet.noneOf(ArchitectureStyle.class);
        styles.forEach(match -> found.add(match.style()));
        List<Suggestion> suggestions = new ArrayList<>();
        byFeature(architecture, found).ifPresent(suggestions::add);
        mixed(architecture, found).ifPresent(suggestions::add);
        suggestions.addAll(cycles(architecture));
        layers(architecture).forEach(suggestions::add);
        domain(architecture, found).ifPresent(suggestions::add);
        boundaries(architecture, styles, found).ifPresent(suggestions::add);
        split(hotspots).ifPresent(suggestions::add);
        if (styles.isEmpty() && architecture.units().size() >= MIN_CLASSES_FOR_STRUCTURE) {
            suggestions.add(new Suggestion(
                    "Choose a structure for the code",
                    "No common structure was recognised. A consistent structure tells every developer where code"
                            + " belongs and keeps related code together.",
                    List.of(
                            "For most Spring applications, package by feature works best: one package per business"
                                    + " area (orders, customers, payments), with its controller, service and"
                                    + " repository inside.",
                            "Keep technical code shared by all features in its own package, such as config or"
                                    + " security.")));
        }
        return suggestions;
    }

    /** A layered project whose class names reveal business features: suggest one package per feature. */
    private static Optional<Suggestion> byFeature(Architecture architecture, Set<ArchitectureStyle> found) {
        if (!found.contains(ArchitectureStyle.LAYERED) || found.contains(ArchitectureStyle.PACKAGE_BY_FEATURE)) {
            return Optional.empty();
        }
        Map<String, Set<Layer>> layersByName = new TreeMap<>();
        Map<String, Set<String>> classesByName = new TreeMap<>();
        for (JavaUnit unit : architecture.units()) {
            Layer layer = architecture.layer(unit);
            if (BUSINESS_LAYERS.contains(layer)) {
                String base = ArchitectureReport.baseName(unit.typeName());
                layersByName
                        .computeIfAbsent(base, key -> EnumSet.noneOf(Layer.class))
                        .add(layer);
                classesByName.computeIfAbsent(base, key -> new TreeSet<>()).add(unit.typeName());
            }
        }
        List<String> features = layersByName.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .toList();
        if (features.size() < MIN_FEATURES) {
            return Optional.empty();
        }
        List<String> steps = new ArrayList<>();
        steps.add("Create one package per business feature below " + rootOrProject(architecture) + ":");
        features.stream()
                .limit(LISTED)
                .forEach(feature -> steps.add("  " + architecture.featurePackage(feature.toLowerCase(Locale.ROOT))
                        + ": " + String.join(", ", classesByName.getOrDefault(feature, Set.of()))));
        if (features.size() > LISTED) {
            steps.add("  and " + (features.size() - LISTED) + " more features: "
                    + String.join(", ", features.subList(LISTED, Math.min(features.size(), LISTED * 3))));
        }
        steps.add(
                "Keep technical code shared by all features, such as configuration and security, in its own package.");
        steps.add("Make classes package-private where you can, so other features only use a feature's public classes.");
        steps.add(
                "Optionally add Spring Modulith and a test that runs ApplicationModules.of(Application.class).verify(),"
                        + " so the boundaries stay clean.");
        return Optional.of(new Suggestion(
                "Organise the code by feature",
                "The code is split by technical layer, but " + features.size() + " business features ("
                        + String.join(", ", features.subList(0, Math.min(LISTED, features.size())))
                        + (features.size() > LISTED ? ", ..." : "")
                        + ") each have classes in several layers. Grouping them by feature keeps a change in one"
                        + " place, makes the structure tell what the application does, and allows features to be"
                        + " split off later.",
                steps));
    }

    /** Layer packages next to feature packages at the top level: two structures at once. */
    private static Optional<Suggestion> mixed(Architecture architecture, Set<ArchitectureStyle> found) {
        if (!found.contains(ArchitectureStyle.LAYERED) || !found.contains(ArchitectureStyle.PACKAGE_BY_FEATURE)) {
            return Optional.empty();
        }
        Set<String> layerPackages = new TreeSet<>();
        Set<String> featurePackages = new TreeSet<>();
        for (JavaUnit unit : architecture.units()) {
            architecture.topSegment(unit).ifPresent(segment -> {
                if (Layer.ofPackage(segment) != Layer.OTHER && Layer.ofPackage(segment) != Layer.CONFIG) {
                    layerPackages.add(segment);
                } else if (!Architecture.TECHNICAL_SEGMENTS.contains(segment)) {
                    featurePackages.add(segment);
                }
            });
        }
        return Optional.of(new Suggestion(
                "Use one structure at the top level",
                "Top-level layer packages (" + String.join(", ", layerPackages) + ") sit next to feature packages ("
                        + String.join(", ", featurePackages) + "). Developers then cannot tell where new code"
                        + " belongs, and features leak into shared layers.",
                List.of(
                        "Move the classes of " + String.join(", ", layerPackages) + " into the feature they belong to.",
                        "Keep only technical code used by every feature, such as configuration, outside the"
                                + " features.")));
    }

    /** For each cycle, the link that is cheapest to cut: the one the fewest classes depend on. */
    private static List<Suggestion> cycles(Architecture architecture) {
        List<Suggestion> suggestions = new ArrayList<>();
        for (Set<String> cycle : architecture.cycles().stream().limit(3).toList()) {
            Map<String, Map<String, Set<String>>> links = new HashMap<>();
            for (JavaUnit unit : architecture.units()) {
                if (!cycle.contains(unit.packageName())) {
                    continue;
                }
                for (Architecture.Use use : architecture.uses(unit)) {
                    String to = use.to().packageName();
                    if (cycle.contains(to) && !to.equals(unit.packageName())) {
                        links.computeIfAbsent(unit.packageName(), key -> new TreeMap<>())
                                .computeIfAbsent(to, key -> new TreeSet<>())
                                .add(unit.typeName());
                    }
                }
            }
            String weakestFrom = "";
            String weakestTo = "";
            Set<String> weakestUsers = Set.of();
            int fewest = Integer.MAX_VALUE;
            for (Map.Entry<String, Map<String, Set<String>>> from : new TreeMap<>(links).entrySet()) {
                for (Map.Entry<String, Set<String>> to : from.getValue().entrySet()) {
                    if (to.getValue().size() < fewest) {
                        fewest = to.getValue().size();
                        weakestFrom = from.getKey();
                        weakestTo = to.getKey();
                        weakestUsers = to.getValue();
                    }
                }
            }
            List<String> names = cycle.stream()
                    .map(name -> ArchitectureReport.relative(architecture, name))
                    .toList();
            suggestions.add(new Suggestion(
                    "Break the cycle between " + String.join(", ", names),
                    "These packages depend on each other in a circle, so none of them can be understood, tested or"
                            + " moved on its own.",
                    List.of(
                            "The cheapest link to cut is " + ArchitectureReport.relative(architecture, weakestFrom)
                                    + " → " + ArchitectureReport.relative(architecture, weakestTo) + ", used only by "
                                    + String.join(", ", weakestUsers) + ".",
                            "Move the types those classes need into "
                                    + ArchitectureReport.relative(architecture, weakestFrom)
                                    + ", or let them depend on an interface that "
                                    + ArchitectureReport.relative(architecture, weakestTo) + " implements.",
                            "If the dependency only reacts to something that happened, publish an application"
                                    + " event instead of calling across.")));
        }
        return suggestions;
    }

    private static List<Suggestion> layers(Architecture architecture) {
        Set<String> controllers = new LinkedHashSet<>();
        Set<String> upward = new LinkedHashSet<>();
        for (JavaUnit unit : architecture.units()) {
            for (Architecture.Use use : architecture.uses(unit)) {
                Layer from = architecture.layer(unit);
                Layer to = architecture.layer(use.to());
                if (LayerViolationCheck.violation(from, to, unit, use.to()).isEmpty()) {
                    continue;
                }
                if (from == Layer.WEB && to == Layer.REPOSITORY) {
                    controllers.add(unit.typeName() + " → " + use.to().typeName());
                } else {
                    upward.add(unit.typeName() + " (" + from.label() + ") → "
                            + use.to().typeName() + " (" + to.label() + ")");
                }
            }
        }
        List<Suggestion> suggestions = new ArrayList<>();
        if (!controllers.isEmpty()) {
            suggestions.add(new Suggestion(
                    "Route data access through services",
                    controllers.size() + " controller dependencies skip the service layer, so business rules and"
                            + " transactions are bypassed or duplicated.",
                    listed(controllers, "Add service methods for: ")));
        }
        if (!upward.isEmpty()) {
            suggestions.add(new Suggestion(
                    "Keep dependencies pointing down the layers",
                    upward.size() + " dependencies point from a lower layer to a higher one, which ties the"
                            + " lower layers to details they should not know.",
                    listed(upward, "Turn these around, or move the shared types down: ")));
        }
        return suggestions;
    }

    private static Optional<Suggestion> domain(Architecture architecture, Set<ArchitectureStyle> found) {
        if (!found.contains(ArchitectureStyle.HEXAGONAL) && !found.contains(ArchitectureStyle.CLEAN)) {
            return Optional.empty();
        }
        Set<String> leaks = new LinkedHashSet<>();
        for (JavaUnit unit : architecture.units()) {
            if (DomainFrameworkCheck.isDomain(unit.packageName())) {
                unit.imports()
                        .forEach(statement -> DomainFrameworkCheck.framework(statement.name())
                                .ifPresent(framework -> leaks.add(unit.typeName() + " uses " + framework)));
            }
        }
        if (leaks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Suggestion(
                "Keep the domain free of frameworks",
                leaks.size() + " domain classes import a framework, which ties the core of the application to it.",
                listed(leaks, "Move framework concerns into adapters for: ")));
    }

    /** Feature packages that use each other's inner classes: make the boundaries explicit. */
    private static Optional<Suggestion> boundaries(
            Architecture architecture, List<StyleMatch> styles, Set<ArchitectureStyle> found) {
        if (!FeatureInternalsCheck.appliesTo(styles) || found.contains(ArchitectureStyle.SPRING_MODULITH)) {
            return Optional.empty();
        }
        Set<String> crossings = new LinkedHashSet<>();
        for (JavaUnit unit : architecture.units()) {
            Optional<String> feature = architecture.featureOf(unit);
            for (Architecture.Use use : architecture.uses(unit)) {
                Optional<String> target = architecture.featureOf(use.to());
                if (feature.isPresent()
                        && target.isPresent()
                        && !feature.equals(target)
                        && !use.to().packageName().equals(architecture.featurePackage(target.get()))) {
                    crossings.add(unit.typeName() + " (" + feature.get() + ") → "
                            + use.to().typeName() + " ("
                            + ArchitectureReport.relative(architecture, use.to().packageName()) + ")");
                }
            }
        }
        if (crossings.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Suggestion(
                "Make feature boundaries explicit with Spring Modulith",
                crossings.size() + " dependencies reach into another feature's inner packages instead of its"
                        + " public API, so features cannot change their insides independently.",
                List.of(
                        "Give each feature a small public API in its top package and move the rest into"
                                + " sub-packages such as internal.",
                        "Add spring-modulith-starter-core and a test that runs"
                                + " ApplicationModules.of(Application.class).verify().",
                        "Start with: "
                                + String.join(
                                        "; ", crossings.stream().limit(LISTED).toList()))));
    }

    private static Optional<Suggestion> split(List<Hotspot> hotspots) {
        if (hotspots.isEmpty()) {
            return Optional.empty();
        }
        List<String> steps = new ArrayList<>();
        hotspots.stream()
                .sorted(Comparator.comparingInt((Hotspot hotspot) -> hotspot.usedBy() + hotspot.uses())
                        .reversed())
                .forEach(hotspot -> steps.add(
                        hotspot.type() + ": used by " + hotspot.usedBy() + " classes, uses " + hotspot.uses()));
        steps.add("Find the groups of methods that change together and give each group its own class.");
        return Optional.of(new Suggestion(
                "Split classes that do too much",
                "These classes are used by many others and use many themselves. Every change to them risks"
                        + " breaking something far away.",
                steps));
    }

    private static List<String> listed(Set<String> items, String lead) {
        List<String> steps = new ArrayList<>();
        steps.add(lead + String.join("; ", items.stream().limit(LISTED).toList())
                + (items.size() > LISTED ? "; and " + (items.size() - LISTED) + " more" : "") + ".");
        return steps;
    }

    private static String rootOrProject(Architecture architecture) {
        return architecture.rootPackage().isEmpty() ? "the source root" : architecture.rootPackage();
    }
}
