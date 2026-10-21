package io.github.fereshtehdev.springinspector.rules.springai;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
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
import java.util.regex.Pattern;

/**
 * {@code @Tool} methods that delete, change or send things, with neither an authorization check nor a confirmation
 * parameter: the model alone decides when they run.
 */
public final class DestructiveToolCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("springai.unconfirmed-destructive-tool");

    private static final String TOOL = "org.springframework.ai.tool.annotation.Tool";
    private static final Pattern DESTRUCTIVE = Pattern.compile(
            "(?:delete|remove|drop|purge|cancel|refund|transfer|pay|charge|send|update|reset|revoke|disable|close"
                    + "|wipe|erase|terminate)(?:[A-Z_].*)?");
    private static final List<String> AUTHORIZATION = List.of(
            "org.springframework.security.access.prepost.PreAuthorize",
            "org.springframework.security.access.annotation.Secured",
            "jakarta.annotation.security.RolesAllowed");
    private static final Pattern CONFIRMATION = Pattern.compile("(?:confirm|approv)\\w*");

    private static final WhyItMatters WHY = new WhyItMatters(
            "The model chooses when to call tools, and its input can be steered by users or by documents it reads"
                    + " (prompt injection). A tool that changes data without its own checks lets that steering delete"
                    + " records, move money or send messages in the user's name.",
            List.of(
                    new Reference(
                            "OWASP Top 10 for LLM Applications: LLM06 Excessive Agency",
                            URI.create("https://genai.owasp.org/llmrisk/llm062025-excessive-agency/")),
                    new Reference(
                            "Spring AI: Tool calling",
                            URI.create("https://docs.spring.io/spring-ai/reference/api/tools.html"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Check the caller's permission in the tool (for example @PreAuthorize) and ask a human to confirm the"
                    + " action, for instance by returning a pending action the user approves, before changing anything.");

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
                .filter(method -> DESTRUCTIVE.matcher(method.getNameAsString()).matches())
                .filter(method -> !isAuthorized(file, method) && !asksForConfirmation(method))
                .map(method -> finding(
                        file,
                        method.getName(),
                        Severity.MAJOR,
                        Confidence.MEDIUM,
                        "Tool " + method.getNameAsString() + " changes data without authorization or confirmation",
                        WHY,
                        FIX))
                .toList();
    }

    private static boolean isAuthorized(JavaFile file, MethodDeclaration method) {
        return hasAuthorization(file, method)
                || Nodes.ancestor(method, ClassOrInterfaceDeclaration.class)
                        .map(type -> hasAuthorization(file, type))
                        .orElse(false);
    }

    private static boolean hasAuthorization(JavaFile file, NodeWithAnnotations<?> node) {
        return AUTHORIZATION.stream().anyMatch(annotation -> file.hasAnnotation(node, annotation));
    }

    private static boolean asksForConfirmation(MethodDeclaration method) {
        return method.getParameters().stream()
                .anyMatch(parameter -> CONFIRMATION
                        .matcher(parameter.getNameAsString().toLowerCase(Locale.ROOT))
                        .matches());
    }
}
