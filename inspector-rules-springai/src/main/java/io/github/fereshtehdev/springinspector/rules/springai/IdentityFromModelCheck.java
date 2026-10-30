package io.github.fereshtehdev.springinspector.rules.springai;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.Nodes;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code @Tool} parameters that say who the caller is, such as {@code userId} or {@code tenantId}. The model fills in
 * tool parameters, so it decides whose data the tool uses. A tool that also takes a {@code ToolContext} or checks
 * authorization is not reported.
 */
public final class IdentityFromModelCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("springai.identity-from-model");

    private static final String TOOL = "org.springframework.ai.tool.annotation.Tool";
    private static final Set<String> IDENTITY_NAMES =
            Set.of("userid", "username", "tenantid", "callerid", "principal", "principalid", "currentuser");
    private static final List<String> AUTHORIZATION = List.of(
            "org.springframework.security.access.prepost.PreAuthorize",
            "org.springframework.security.access.annotation.Secured",
            "jakarta.annotation.security.RolesAllowed");

    private static final WhyItMatters WHY = new WhyItMatters(
            "The model writes every tool parameter, and prompt injection can steer what it writes. One crafted message"
                    + " or document can make it pass another user's or tenant's id and read or change their data.",
            List.of(
                    new Reference(
                            "OWASP Top 10 for LLM Applications: LLM06 Excessive Agency",
                            URI.create("https://genai.owasp.org/llmrisk/llm062025-excessive-agency/")),
                    new Reference(
                            "Spring AI: Tool context",
                            URI.create("https://docs.spring.io/spring-ai/reference/api/tools.html"))));

    private static final HowToFix FIX =
            HowToFix.withoutPatch("Remove the parameter and take the identity from a trusted source: pass it with"
                    + " .toolContext(Map.of(\"tenantId\", tenantId)) and read it from a ToolContext parameter, which"
                    + " is never sent to the model, or read it from the security context.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SPRING_AI;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        return file.unit().findAll(MethodDeclaration.class, method -> file.hasAnnotation(method, TOOL)).stream()
                .filter(method -> !isProtected(file, method))
                .flatMap(method -> method.getParameters().stream())
                .filter(IdentityFromModelCheck::namesTheCaller)
                .map(parameter -> finding(
                        file,
                        parameter,
                        Severity.MAJOR,
                        Confidence.MEDIUM,
                        "The model chooses the identity " + parameter.getNameAsString() + " this tool acts for",
                        WHY,
                        FIX))
                .toList();
    }

    private static boolean namesTheCaller(Parameter parameter) {
        return IDENTITY_NAMES.contains(parameter.getNameAsString().toLowerCase(Locale.ROOT));
    }

    private static boolean isProtected(JavaFile file, MethodDeclaration method) {
        boolean takesContext = method.getParameters().stream()
                .anyMatch(parameter -> parameter.getType().asString().endsWith("ToolContext"));
        return takesContext
                || hasAuthorization(file, method)
                || Nodes.ancestor(method, ClassOrInterfaceDeclaration.class)
                        .map(type -> hasAuthorization(file, type))
                        .orElse(false);
    }

    private static boolean hasAuthorization(JavaFile file, NodeWithAnnotations<?> node) {
        return AUTHORIZATION.stream().anyMatch(annotation -> file.hasAnnotation(node, annotation));
    }
}
