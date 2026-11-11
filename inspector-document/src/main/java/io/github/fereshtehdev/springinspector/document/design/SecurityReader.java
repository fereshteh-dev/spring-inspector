package io.github.fereshtehdev.springinspector.document.design;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AccessRule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads Spring Security's configuration: who may call what, and which sign-in mechanisms are on. */
final class SecurityReader {

    private static final Set<String> CHAINS = Set.of("SecurityFilterChain", "SecurityWebFilterChain");
    private static final Set<String> MATCHERS =
            Set.of("requestMatchers", "pathMatchers", "antMatchers", "mvcMatchers", "anyRequest", "anyExchange");
    private static final Set<String> METHOD_RULES = Set.of("PreAuthorize", "PostAuthorize", "Secured", "RolesAllowed");
    private static final Map<String, String> MECHANISMS = mechanisms();

    private SecurityReader() {}

    static List<AccessRule> rules(TypeDeclaration<?> type) {
        List<AccessRule> rules = new ArrayList<>();
        for (MethodDeclaration chain : chains(type)) {
            String definedIn = type.getNameAsString() + "." + chain.getNameAsString();
            chain.findAll(MethodCallExpr.class, call -> MATCHERS.contains(call.getNameAsString())).stream()
                    .sorted(Comparator.comparing(SecurityReader::position))
                    .forEach(matcher -> decision(matcher)
                            .ifPresent(access -> rules.add(new AccessRule(requests(matcher), access, definedIn))));
        }
        return rules;
    }

    static List<String> features(TypeDeclaration<?> type) {
        Set<String> features = new LinkedHashSet<>();
        if (Annotations.has(type, "EnableMethodSecurity", "EnableReactiveMethodSecurity")) {
            features.add("Method security is enabled (@PreAuthorize and similar annotations are checked)");
        }
        for (MethodDeclaration chain : chains(type)) {
            for (MethodCallExpr call : chain.findAll(MethodCallExpr.class)) {
                String name = call.getNameAsString();
                String mechanism = MECHANISMS.get(name);
                if (mechanism != null && !isDisabled(call)) {
                    features.add(mechanism);
                } else if (name.equals("csrf") && isDisabled(call)) {
                    features.add("CSRF protection is turned off");
                } else if (name.equals("sessionCreationPolicy")
                        && call.getArguments().toString().contains("STATELESS")) {
                    features.add("No server-side session (stateless)");
                }
            }
        }
        for (ObjectCreationExpr created : type.findAll(ObjectCreationExpr.class)) {
            String created0 = created.getType().getNameAsString();
            if (created0.endsWith("PasswordEncoder")) {
                features.add("Passwords are hashed with " + created0);
            }
        }
        type
                .findAll(MethodCallExpr.class, call -> call.getNameAsString().equals("createDelegatingPasswordEncoder"))
                .stream()
                .findFirst()
                .ifPresent(call -> features.add("Passwords are hashed with the delegating encoder (bcrypt)"));
        type.findAll(MethodCallExpr.class, call -> call.toString().startsWith("NoOpPasswordEncoder")).stream()
                .findFirst()
                .ifPresent(call -> features.add("Passwords are stored without hashing (NoOpPasswordEncoder)"));
        return List.copyOf(features);
    }

    /** How many classes and methods of the type carry a method security annotation. */
    static int methodRules(TypeDeclaration<?> type) {
        return type.findAll(
                        AnnotationExpr.class,
                        annotation -> METHOD_RULES.contains(annotation.getName().getIdentifier()))
                .size();
    }

    private static List<MethodDeclaration> chains(TypeDeclaration<?> type) {
        return type.getMethods().stream()
                .filter(method -> CHAINS.contains(method.getType().asString().replaceAll(".*\\.", "")))
                .toList();
    }

    /** The call made on the matcher, such as {@code permitAll()} in {@code requestMatchers("/x").permitAll()}. */
    private static Optional<String> decision(MethodCallExpr matcher) {
        Optional<Node> parent = matcher.getParentNode();
        if (parent.isPresent()
                && parent.get() instanceof MethodCallExpr decision
                && decision.getScope().filter(scope -> scope.equals(matcher)).isPresent()) {
            return Optional.of(access(decision));
        }
        return Optional.empty();
    }

    private static String access(MethodCallExpr decision) {
        String arguments = String.join(
                " or ", decision.getArguments().stream().map(Annotations::text).toList());
        return switch (decision.getNameAsString()) {
            case "permitAll" -> "anyone";
            case "authenticated" -> "signed-in users";
            case "fullyAuthenticated" -> "users who signed in during this session";
            case "anonymous" -> "only users who are not signed in";
            case "denyAll" -> "nobody";
            case "hasRole", "hasAnyRole" -> "role " + arguments;
            case "hasAuthority", "hasAnyAuthority" -> "authority " + arguments;
            case "hasScope", "hasAnyScope" -> "OAuth2 scope " + arguments;
            case "access" -> "a custom rule";
            default -> decision.getNameAsString() + "()";
        };
    }

    private static String requests(MethodCallExpr matcher) {
        if (matcher.getArguments().isEmpty()) {
            return "every other request";
        }
        return String.join(
                ", ",
                matcher.getArguments().stream()
                        .map(Annotations::text)
                        .map(text -> text.matches("\\{[A-Z]+}") ? text.substring(1, text.length() - 1) : text)
                        .toList());
    }

    private static boolean isDisabled(MethodCallExpr call) {
        boolean chained = call.getParentNode()
                .filter(parent -> parent instanceof MethodCallExpr next
                        && next.getNameAsString().equals("disable"))
                .isPresent();
        return chained || call.getArguments().toString().contains("disable");
    }

    private static Position position(MethodCallExpr call) {
        return call.getName().getBegin().orElse(Position.HOME);
    }

    private static Map<String, String> mechanisms() {
        Map<String, String> mechanisms = new LinkedHashMap<>();
        mechanisms.put("oauth2ResourceServer", "OAuth2 resource server: requests carry a bearer token (JWT or opaque)");
        mechanisms.put("oauth2Login", "Sign-in with OAuth2 / OpenID Connect");
        mechanisms.put("saml2Login", "Sign-in with SAML 2");
        mechanisms.put("formLogin", "Sign-in with a login form");
        mechanisms.put("httpBasic", "HTTP Basic authentication");
        mechanisms.put("webAuthn", "Sign-in with passkeys (WebAuthn)");
        mechanisms.put("oneTimeTokenLogin", "Sign-in with a one-time token");
        mechanisms.put("x509", "Client certificates (X.509)");
        mechanisms.put("rememberMe", "Remember-me cookies");
        mechanisms.put("cors", "CORS is configured");
        return mechanisms;
    }
}
