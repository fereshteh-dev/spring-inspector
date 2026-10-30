package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CORS that accepts the origin pattern {@code *} together with credentials. Spring refuses {@code allowedOrigins("*")}
 * with credentials at run time, but origin patterns pass that check and echo back any site's origin.
 */
public final class CorsCredentialsCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.cors-any-origin-with-credentials");

    private static final String CROSS_ORIGIN = "org.springframework.web.bind.annotation.CrossOrigin";
    private static final Set<String> PATTERN_METHODS = Set.of("allowedOriginPatterns", "setAllowedOriginPatterns");
    private static final Set<String> CREDENTIAL_METHODS = Set.of("allowCredentials", "setAllowCredentials");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Any website a logged-in user visits can then call this API with that user's cookies and read the answers,"
                    + " which exposes their data and lets the site act in their name.",
            List.of(
                    SecurityReferences.cwe(942, "Permissive cross-domain policy"),
                    SecurityReferences.springDocs(
                            "Spring Framework: CORS", "spring-framework/reference/web/webmvc-cors.html")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "List the origins that may call the API, such as https://app.example.com, or remove allowCredentials if"
                    + " the API is public and does not use cookies.");

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
        List<Finding> findings = new ArrayList<>();
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class, CorsCredentialsCheck::allowsAnyOrigin)) {
            if (allowsCredentials(scopeOf(call))) {
                findings.add(report(file, call));
            }
        }
        for (AnnotationExpr annotation : file.unit().findAll(AnnotationExpr.class)) {
            if (annotation instanceof NormalAnnotationExpr normal
                    && file.refersTo(annotation.getNameAsString(), CROSS_ORIGIN)
                    && annotationAllowsAnyOriginWithCredentials(normal)) {
                findings.add(report(file, annotation));
            }
        }
        return findings;
    }

    private Finding report(JavaFile file, Node use) {
        return finding(
                file, use, Severity.MAJOR, Confidence.HIGH, "CORS allows every origin to send credentials", WHY, FIX);
    }

    private static boolean allowsAnyOrigin(MethodCallExpr call) {
        return PATTERN_METHODS.contains(call.getNameAsString())
                && !call.findAll(
                                StringLiteralExpr.class,
                                literal -> literal.getValue().equals("*"))
                        .isEmpty();
    }

    private static Node scopeOf(MethodCallExpr call) {
        return Nodes.ancestor(call, MethodDeclaration.class)
                .<Node>map(method -> method)
                .orElse(call.findRootNode());
    }

    private static boolean allowsCredentials(Node scope) {
        return !scope.findAll(
                        MethodCallExpr.class,
                        call -> CREDENTIAL_METHODS.contains(call.getNameAsString())
                                && call.getArguments().size() == 1
                                && call.getArgument(0).toString().equals("true"))
                .isEmpty();
    }

    private static boolean annotationAllowsAnyOriginWithCredentials(NormalAnnotationExpr annotation) {
        boolean anyOrigin = annotation.getPairs().stream()
                .anyMatch(pair -> pair.getNameAsString().equals("originPatterns")
                        && !pair.findAll(
                                        StringLiteralExpr.class,
                                        literal -> literal.getValue().equals("*"))
                                .isEmpty());
        boolean credentials = annotation.getPairs().stream()
                .anyMatch(pair -> pair.getNameAsString().equals("allowCredentials")
                        && pair.getValue().toString().equals("\"true\""));
        return anyOrigin && credentials;
    }
}
