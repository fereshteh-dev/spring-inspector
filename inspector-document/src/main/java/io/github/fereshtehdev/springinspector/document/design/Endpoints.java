package io.github.fereshtehdev.springinspector.document.design;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Endpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The HTTP, GraphQL and messaging endpoints a type declares with Spring's mapping annotations. */
final class Endpoints {

    private static final Map<String, String> HTTP = Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "DeleteMapping", "DELETE",
            "PatchMapping", "PATCH");
    private static final Map<String, String> OTHER = Map.of(
            "QueryMapping", "QUERY",
            "MutationMapping", "MUTATION",
            "SubscriptionMapping", "SUBSCRIPTION",
            "MessageMapping", "MESSAGE");
    private static final String[] ACCESS = {"PreAuthorize", "Secured", "RolesAllowed", "PermitAll", "DenyAll"};
    private static final String[] HTTP_CLIENTS = {"FeignClient", "HttpExchange"};

    private Endpoints() {}

    static List<Endpoint> of(TypeDeclaration<?> type) {
        if (Annotations.has(type, HTTP_CLIENTS)) {
            return List.of();
        }
        String prefix = Annotations.find(type, "RequestMapping")
                .flatMap(mapping -> Annotations.value(mapping, "value", "path"))
                .orElse("");
        List<Endpoint> endpoints = new ArrayList<>();
        for (MethodDeclaration method : type.getMethods()) {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                String name = annotation.getName().getIdentifier();
                String verb = HTTP.get(name);
                String channel = OTHER.get(name);
                if (verb != null || name.equals("RequestMapping")) {
                    for (String path : orRoot(Annotations.values(annotation, "value", "path"))) {
                        endpoints.add(endpoint(
                                type, method, verb != null ? verb : requestMethods(annotation), join(prefix, path)));
                    }
                } else if (channel != null) {
                    String target =
                            Annotations.value(annotation, "value", "name").orElse(method.getNameAsString());
                    endpoints.add(endpoint(type, method, channel, target));
                }
            }
        }
        return endpoints;
    }

    private static Endpoint endpoint(TypeDeclaration<?> type, MethodDeclaration method, String verb, String path) {
        String body = method.getParameters().stream()
                .filter(parameter -> Annotations.has(parameter, "RequestBody", "ModelAttribute", "Payload"))
                .map(parameter -> parameter.getType().asString())
                .findFirst()
                .orElse("");
        return new Endpoint(
                verb,
                path,
                type.getNameAsString() + "." + method.getNameAsString(),
                body,
                response(method.getType()),
                access(type, method));
    }

    private static String requestMethods(AnnotationExpr mapping) {
        List<String> methods = Annotations.values(mapping, "method").stream()
                .map(method -> method.replace("{", "").replace("}", ""))
                .map(method -> method.substring(method.lastIndexOf('.') + 1))
                .toList();
        return methods.isEmpty() ? "ANY" : String.join(", ", methods);
    }

    private static List<String> orRoot(List<String> paths) {
        return paths.isEmpty() ? List.of("") : paths;
    }

    static String join(String prefix, String path) {
        String joined = ("/" + prefix + "/" + path).replaceAll("/{2,}", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    /** The payload type: {@code Order} for {@code ResponseEntity<Order>}. */
    private static String response(Type type) {
        if (type instanceof ClassOrInterfaceType declared
                && declared.getNameAsString().equals("ResponseEntity")
                && declared.getTypeArguments().isPresent()
                && declared.getTypeArguments().get().size() == 1) {
            return declared.getTypeArguments().get().get(0).asString();
        }
        return type.asString();
    }

    private static String access(TypeDeclaration<?> type, MethodDeclaration method) {
        Optional<AnnotationExpr> rule = Annotations.find(method, ACCESS).or(() -> Annotations.find(type, ACCESS));
        return rule.map(annotation -> {
                    List<String> values = Annotations.values(annotation, "value");
                    return values.isEmpty() ? annotation.getName().getIdentifier() : String.join(", ", values);
                })
                .orElse("");
    }
}
