package io.github.fereshtehdev.springinspector.document.design;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Flow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Follows the injected dependencies from each entry point: a controller uses services, which use repositories and
 * clients. This is what happens, in order, when a request arrives.
 */
final class Flows {

    /**
     * The project types a type is given through its constructor, record components or fields.
     *
     * @param trigger why the type is an entry point, or empty when it is not one
     * @param data whether the type only holds values, such as a record or an enum, and so is not a step of a flow
     * @param candidates qualified names the injected types could have, most likely first
     */
    record Wiring(String qualifiedName, String simpleName, String trigger, boolean data, List<String> candidates) {

        Wiring {
            candidates = List.copyOf(candidates);
        }
    }

    private Flows() {}

    static Wiring wiring(CompilationUnit unit, TypeDeclaration<?> type, String trigger) {
        String packageName = unit.getPackageDeclaration()
                .map(declared -> declared.getNameAsString())
                .orElse("");
        List<String> candidates = new ArrayList<>();
        for (String injected : injected(type)) {
            unit.getImports().stream()
                    .filter(statement -> !statement.isAsterisk() && !statement.isStatic())
                    .map(statement -> statement.getNameAsString())
                    .filter(name -> name.endsWith("." + injected))
                    .forEach(candidates::add);
            candidates.add(packageName.isEmpty() ? injected : packageName + "." + injected);
            unit.getImports().stream()
                    .filter(statement -> statement.isAsterisk() && !statement.isStatic())
                    .forEach(statement -> candidates.add(statement.getNameAsString() + "." + injected));
        }
        String simpleName = type.getNameAsString();
        boolean data = type instanceof RecordDeclaration
                || type instanceof EnumDeclaration
                || type instanceof AnnotationDeclaration;
        return new Wiring(
                packageName.isEmpty() ? simpleName : packageName + "." + simpleName,
                simpleName,
                trigger,
                data,
                candidates);
    }

    /** @param stored simple names of entities, which are data and not a step of a flow */
    static List<Flow> of(List<Wiring> wirings, Set<String> stored) {
        Map<String, Wiring> byName = new HashMap<>();
        wirings.forEach(wiring -> byName.putIfAbsent(wiring.qualifiedName(), wiring));
        List<Flow> flows = new ArrayList<>();
        for (Wiring entry : wirings) {
            if (entry.trigger().isEmpty()) {
                continue;
            }
            Set<Wiring> direct = uses(entry, byName, stored);
            Set<String> indirect = new TreeSet<>();
            for (Wiring used : direct) {
                uses(used, byName, stored).forEach(next -> indirect.add(next.simpleName()));
            }
            Set<String> directNames = new TreeSet<>();
            direct.forEach(used -> directNames.add(used.simpleName()));
            indirect.removeAll(directNames);
            indirect.remove(entry.simpleName());
            if (direct.isEmpty()) {
                continue;
            }
            flows.add(new Flow(entry.simpleName(), entry.trigger(), List.copyOf(directNames), List.copyOf(indirect)));
        }
        return flows;
    }

    private static Set<Wiring> uses(Wiring from, Map<String, Wiring> byName, Set<String> stored) {
        Set<Wiring> used = new LinkedHashSet<>();
        for (String candidate : from.candidates()) {
            Optional.ofNullable(byName.get(candidate))
                    .filter(target -> !target.equals(from) && !target.data() && !stored.contains(target.simpleName()))
                    .ifPresent(used::add);
        }
        return used;
    }

    private static Set<String> injected(TypeDeclaration<?> type) {
        Set<String> names = new LinkedHashSet<>();
        if (type instanceof RecordDeclaration data) {
            data.getParameters().forEach(component -> addNames(component.getType(), names));
        }
        for (ConstructorDeclaration constructor : type.getConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                addNames(parameter.getType(), names);
            }
        }
        for (FieldDeclaration field : type.getFields()) {
            if (!field.isStatic()) {
                addNames(field.getElementType(), names);
            }
        }
        return names;
    }

    private static void addNames(Type type, Set<String> names) {
        if (type instanceof ClassOrInterfaceType declared) {
            names.add(declared.getNameAsString());
        }
        type.findAll(ClassOrInterfaceType.class).forEach(nested -> names.add(nested.getNameAsString()));
    }
}
