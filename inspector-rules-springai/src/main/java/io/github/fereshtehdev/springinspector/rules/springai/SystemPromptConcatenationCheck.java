package io.github.fereshtehdev.springinspector.rules.springai;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
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
import io.github.fereshtehdev.springinspector.javamodel.StringExpressions;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * System prompts built from runtime values: the model cannot tell the developer's instructions from data mixed into
 * them. Request parameters flowing in directly raise the confidence.
 */
public final class SystemPromptConcatenationCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("springai.system-prompt-concatenation");

    private static final String SPRING_AI = "org.springframework.ai.";
    private static final String SYSTEM_MESSAGE = "org.springframework.ai.chat.messages.SystemMessage";
    private static final Set<String> SYSTEM_METHODS = Set.of("system", "defaultSystem");
    private static final List<String> REQUEST_BINDINGS =
            List.of("RequestParam", "RequestBody", "PathVariable", "RequestHeader", "RequestPart", "ModelAttribute");
    private static final String WEB_BIND = "org.springframework.web.bind.annotation.";

    private static final WhyItMatters WHY = new WhyItMatters(
            "The system prompt carries your instructions with the highest authority. Text spliced into it, such as a"
                    + " user's input or a document, can override those instructions (prompt injection): reveal the"
                    + " prompt, call tools it should not, or change answers for other users.",
            List.of(
                    new Reference(
                            "OWASP Top 10 for LLM Applications: LLM01 Prompt Injection",
                            URI.create("https://genai.owasp.org/llmrisk/llm01-prompt-injection/")),
                    new Reference(
                            "Spring AI: ChatClient prompts",
                            URI.create("https://docs.spring.io/spring-ai/reference/api/chatclient.html"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Keep the system prompt constant. Pass runtime data in the user message, clearly delimited, for example"
                    + " .user(u -> u.text(\"Question: <question>{q}</question>\").param(\"q\", question)), and tell"
                    + " the model in the system prompt to treat delimited text as data, never as instructions.");

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
        boolean usesSpringAi = file.unit().getImports().stream()
                .anyMatch(declaration -> declaration.getNameAsString().startsWith(SPRING_AI));
        if (!usesSpringAi) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            if (isSystemPromptCall(call) && call.getArguments().size() == 1) {
                check(file, call.getArgument(0), call.getArgument(0)).ifPresent(findings::add);
            }
        }
        for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
            if (file.refersTo(creation.getType().getNameWithScope(), SYSTEM_MESSAGE)
                    && creation.getArguments().size() == 1) {
                check(file, creation, creation.getArgument(0)).ifPresent(findings::add);
            }
        }
        return findings;
    }

    /** {@code system(text)}, {@code defaultSystem(text)}, or {@code text(...)} inside {@code system(s -> ...)}. */
    private static boolean isSystemPromptCall(MethodCallExpr call) {
        if (SYSTEM_METHODS.contains(call.getNameAsString())) {
            return true;
        }
        return call.getNameAsString().equals("text")
                && Nodes.ancestor(call, LambdaExpr.class)
                        .flatMap(Node::getParentNode)
                        .filter(parent -> parent instanceof MethodCallExpr outer
                                && SYSTEM_METHODS.contains(outer.getNameAsString()))
                        .isPresent();
    }

    private Optional<Finding> check(JavaFile file, Node prompt, Expression text) {
        List<Expression> values = StringExpressions.runtimeValues(text);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        boolean fromRequest = values.stream().anyMatch(value -> isRequestParameter(file, value));
        return Optional.of(finding(
                file,
                prompt,
                Severity.MAJOR,
                fromRequest ? Confidence.HIGH : Confidence.MEDIUM,
                fromRequest
                        ? "Request input is concatenated into a system prompt"
                        : "The system prompt is built from runtime values",
                WHY,
                FIX));
    }

    private static boolean isRequestParameter(JavaFile file, Expression value) {
        if (!(value instanceof NameExpr name)) {
            return false;
        }
        return Nodes.ancestor(value, MethodDeclaration.class)
                .flatMap(method -> parameterNamed(method, name.getNameAsString()))
                .filter(parameter -> REQUEST_BINDINGS.stream()
                        .anyMatch(binding -> file.hasAnnotation(parameter, WEB_BIND + binding)))
                .isPresent();
    }

    private static Optional<Parameter> parameterNamed(MethodDeclaration method, String name) {
        return method.getParameters().stream()
                .filter(parameter -> parameter.getNameAsString().equals(name))
                .findFirst();
    }
}
