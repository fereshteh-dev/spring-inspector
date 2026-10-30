package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.Nodes;
import io.github.fereshtehdev.springinspector.javamodel.StringExpressions;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Recognises values that come straight from an HTTP request, which an attacker controls. */
final class RequestInputs {

    private static final String WEB_BIND = "org.springframework.web.bind.annotation.";
    private static final List<String> BINDINGS = List.of(
            "RequestParam",
            "PathVariable",
            "RequestBody",
            "RequestHeader",
            "ModelAttribute",
            "CookieValue",
            "RequestPart",
            "MatrixVariable");
    private static final Set<String> REQUEST_ACCESSORS = Set.of(
            "getParameter", "getHeader", "getQueryString", "getRequestURI", "getPathInfo", "getOriginalFilename");
    /** Spring has already converted these, so they cannot carry text an attacker chose. */
    private static final Set<String> UNINJECTABLE_TYPES = Set.of(
            "boolean",
            "byte",
            "short",
            "int",
            "long",
            "float",
            "double",
            "Boolean",
            "Byte",
            "Short",
            "Integer",
            "Long",
            "Float",
            "Double",
            "BigDecimal",
            "BigInteger",
            "UUID");

    private static final int MAX_DEPTH = 6;

    private RequestInputs() {}

    /** A request-bound parameter, a servlet request accessor, or a string built from either. */
    static boolean isRequestInput(JavaFile file, Expression expression) {
        return isRequestInput(file, expression, 0);
    }

    private static boolean isRequestInput(JavaFile file, Expression expression, int depth) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        Expression value = StringExpressions.followLocal(expression);
        if (value instanceof NameExpr name && isBoundParameter(file, name)) {
            return true;
        }
        if (value instanceof MethodCallExpr call && REQUEST_ACCESSORS.contains(call.getNameAsString())) {
            return true;
        }
        return StringExpressions.runtimeValues(value).stream().anyMatch(part -> isRequestInput(file, part, depth + 1));
    }

    private static boolean isBoundParameter(JavaFile file, NameExpr name) {
        Optional<Parameter> parameter = Nodes.ancestor(name, MethodDeclaration.class)
                .flatMap(method -> method.getParameters().stream()
                        .filter(candidate -> candidate.getNameAsString().equals(name.getNameAsString()))
                        .findFirst());
        return parameter
                .filter(found -> !UNINJECTABLE_TYPES.contains(found.getType().asString()))
                .map(found -> BINDINGS.stream().anyMatch(binding -> file.hasAnnotation(found, WEB_BIND + binding)))
                .orElse(false);
    }
}
