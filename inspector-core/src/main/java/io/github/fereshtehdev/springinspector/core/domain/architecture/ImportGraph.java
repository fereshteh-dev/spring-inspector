package io.github.fereshtehdev.springinspector.core.domain.architecture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every Java source file of the project with what it imports: the raw material of the architecture rules. */
public record ImportGraph(List<JavaUnit> units) {

    public ImportGraph {
        units = List.copyOf(units);
    }

    public static ImportGraph empty() {
        return new ImportGraph(List.of());
    }

    /** The project's own types by qualified name; imports of anything else are libraries. */
    public Map<String, JavaUnit> byQualifiedName() {
        Map<String, JavaUnit> index = new HashMap<>();
        units.forEach(unit -> index.putIfAbsent(unit.qualifiedName(), unit));
        return index;
    }

    public Optional<JavaUnit> unit(String qualifiedName) {
        return units.stream()
                .filter(unit -> unit.qualifiedName().equals(qualifiedName))
                .findFirst();
    }
}
