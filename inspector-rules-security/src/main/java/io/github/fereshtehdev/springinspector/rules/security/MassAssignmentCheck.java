package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.Nodes;
import java.util.List;

/**
 * Request bodies and form data bound straight into a JPA entity in a controller. The entity type is resolved from
 * the project's sources, so only real entities are reported.
 */
public final class MassAssignmentCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.mass-assignment");

    private static final String WEB_BIND = "org.springframework.web.bind.annotation.";
    private static final List<String> CONTROLLERS =
            List.of(WEB_BIND + "RestController", "org.springframework.stereotype.Controller");
    private static final List<String> BINDINGS = List.of(WEB_BIND + "RequestBody", WEB_BIND + "ModelAttribute");
    private static final List<String> ENTITIES = List.of("jakarta.persistence.Entity", "javax.persistence.Entity");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Binding a request onto an entity lets the caller set every field it has, not just the ones the form shows:"
                    + " ids, roles, prices, owners or audit fields, which are then saved as they arrive.",
            List.of(
                    SecurityReferences.cwe(
                            915, "Improperly controlled modification of dynamically-determined object attributes"),
                    SecurityReferences.owaspCheatSheet("Mass Assignment", "Mass_Assignment_Cheat_Sheet")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Accept a request record that has only the fields the caller may set, validate it, and copy those fields"
                    + " onto the entity in the service.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SECURITY;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        return file.unit().findAll(Parameter.class, parameter -> isBoundInController(file, parameter)).stream()
                .filter(parameter -> ENTITIES.stream()
                        .anyMatch(entity -> file.typeHasAnnotation(parameter.getType(), entity)
                                .orElse(false)))
                .map(parameter -> finding(
                        file,
                        parameter,
                        Severity.MAJOR,
                        Confidence.HIGH,
                        "The request is bound directly to the entity " + parameter.getType(),
                        WHY,
                        FIX))
                .toList();
    }

    private static boolean isBoundInController(JavaFile file, Parameter parameter) {
        boolean bound = BINDINGS.stream().anyMatch(binding -> file.hasAnnotation(parameter, binding));
        boolean inController = Nodes.ancestor(parameter, MethodDeclaration.class)
                .flatMap(method -> Nodes.ancestor(method, ClassOrInterfaceDeclaration.class))
                .map(type -> CONTROLLERS.stream().anyMatch(controller -> file.hasAnnotation(type, controller)))
                .orElse(false);
        return bound && inController;
    }
}
