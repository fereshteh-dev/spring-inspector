package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.Nodes;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/** What the Spring Boot rules need to know about Spring code: controllers, transactions, entities and fields. */
final class SpringCode {

    static final String WEB = "org.springframework.web.bind.annotation.";
    static final List<String> TRANSACTIONAL =
            List.of("org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional");
    static final String SPRING_TRANSACTIONAL = TRANSACTIONAL.getFirst();
    private static final List<String> CONTROLLERS =
            List.of(WEB + "RestController", "org.springframework.stereotype.Controller");
    private static final List<String> MAPPINGS =
            List.of("RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping");
    private static final List<String> ENTITIES = List.of("jakarta.persistence.Entity", "javax.persistence.Entity");

    private SpringCode() {}

    static boolean isController(JavaFile file, TypeDeclaration<?> type) {
        return CONTROLLERS.stream().anyMatch(controller -> file.hasAnnotation(type, controller));
    }

    /** A method that handles requests: it carries a request mapping and sits in a controller. */
    static boolean isHandler(JavaFile file, MethodDeclaration method) {
        boolean mapped = MAPPINGS.stream().anyMatch(mapping -> file.hasAnnotation(method, WEB + mapping));
        return mapped
                && enclosingType(method).map(type -> isController(file, type)).orElse(false);
    }

    static boolean isTransactional(JavaFile file, NodeWithAnnotations<?> node) {
        return TRANSACTIONAL.stream().anyMatch(annotation -> file.hasAnnotation(node, annotation));
    }

    /** The project entity a type names, looking through containers such as {@code List<Order>} or {@code Page<Order>}. */
    static Optional<String> entityIn(JavaFile file, Type type) {
        if (isEntity(file, type)) {
            return Optional.of(type.asString());
        }
        if (type instanceof ClassOrInterfaceType generic
                && generic.getTypeArguments().isPresent()) {
            for (Type argument : generic.getTypeArguments().get()) {
                Optional<String> entity = entityIn(file, argument);
                if (entity.isPresent()) {
                    return entity;
                }
            }
        }
        return Optional.empty();
    }

    static boolean isEntity(JavaFile file, Type type) {
        return type.isClassOrInterfaceType()
                && ENTITIES.stream()
                        .anyMatch(entity -> file.typeHasAnnotation(type, entity).orElse(false));
    }

    static Optional<ClassOrInterfaceDeclaration> enclosingType(Node node) {
        return Nodes.ancestor(node, ClassOrInterfaceDeclaration.class);
    }

    /** The declared type of the field an expression such as {@code jdbc} or {@code this.jdbc} reads. */
    static Optional<Type> fieldType(Expression expression) {
        String name = switch (expression) {
            case NameExpr variable -> variable.getNameAsString();
            case FieldAccessExpr field when field.getScope() instanceof ThisExpr -> field.getNameAsString();
            default -> "";
        };
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return enclosingType(expression)
                .flatMap(type -> type.getFields().stream()
                        .flatMap(field -> field.getVariables().stream())
                        .filter(variable -> variable.getNameAsString().equals(name))
                        .map(variable -> variable.getType())
                        .findFirst());
    }

    static boolean hasFieldOfType(JavaFile file, Expression scope, List<String> qualifiedTypes) {
        return fieldType(scope)
                .map(type -> type.isClassOrInterfaceType()
                        ? type.asClassOrInterfaceType().getNameWithScope()
                        : type.asString())
                .map(name -> qualifiedTypes.stream().anyMatch(qualified -> file.refersTo(name, qualified)))
                .orElse(false);
    }

    static Reference springDocs(String title, String path) {
        return new Reference(title, URI.create("https://docs.spring.io/" + path));
    }

    static boolean declaresField(TypeDeclaration<?> type, String name) {
        return type.getFields().stream()
                .map(FieldDeclaration::getVariables)
                .flatMap(List::stream)
                .anyMatch(variable -> variable.getNameAsString().equals(name));
    }
}
