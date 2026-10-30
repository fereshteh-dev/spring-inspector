package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions;
import io.github.fereshtehdev.springinspector.rules.architecture.DefaultLayerRules.Violation;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Layer rules a team writes in {@code spring-inspector.yml}, which replace the built-in ones for the layers they name:
 *
 * <pre>
 * rules:
 *   options:
 *     architecture.layer-violation:
 *       web: service, domain
 *       service: repository, domain
 *       domain: ""
 * </pre>
 *
 * A layer may always use itself and classes outside any layer (utilities).
 */
final class DeclaredLayers {

    private static final Pattern SEPARATOR = Pattern.compile("[,\\s]+");

    private DeclaredLayers() {}

    static Map<Layer, Set<Layer>> read(RuleOptions options) {
        Map<Layer, Set<Layer>> declared = new EnumMap<>(Layer.class);
        options.values().forEach((key, value) -> {
            Layer from = layer(key, key);
            Set<Layer> allowed = EnumSet.of(from, Layer.OTHER);
            SEPARATOR
                    .splitAsStream(value.strip())
                    .filter(name -> !name.isEmpty())
                    .forEach(name -> allowed.add(layer(name, key)));
            declared.put(from, allowed);
        });
        return declared;
    }

    static Optional<Violation> violation(
            Map<Layer, Set<Layer>> declared, Layer from, Layer to, JavaUnit unit, JavaUnit target) {
        Set<Layer> allowed = declared.getOrDefault(from, Set.of());
        if (allowed.contains(to)) {
            return Optional.empty();
        }
        String permitted = allowed.stream()
                .filter(layer -> layer != from && layer != Layer.OTHER)
                .map(Layer::label)
                .collect(Collectors.joining(", "));
        return Optional.of(new Violation(
                Severity.MAJOR,
                unit.typeName() + " (" + from.label() + ") uses " + target.typeName() + " (" + to.label()
                        + "), which the project's layer rules do not allow",
                "The team's spring-inspector.yml lets the " + from.label() + " layer use "
                        + (permitted.isEmpty() ? "no other layer" : "only: " + permitted)
                        + ". Keeping to the declared layers is what keeps them replaceable and testable.",
                "Go through a layer " + from.label() + " may use, or change the rule in spring-inspector.yml if the"
                        + " architecture has changed on purpose."));
    }

    private static Layer layer(String name, String key) {
        String normalized = name.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(Layer.values())
                .filter(layer -> layer.label().equals(normalized)
                        || layer.name().toLowerCase(Locale.ROOT).equals(normalized)
                        || (layer == Layer.CONFIG && normalized.equals("config")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("rules.options.architecture.layer-violation." + key
                        + ": '" + name + "' is not a layer; use web, service, repository, domain, config or other"));
    }
}
