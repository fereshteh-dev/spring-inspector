package io.github.fereshtehdev.springinspector.rules.springai;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
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
import java.net.URI;
import java.util.List;

/** {@code @Tool} methods without a description, which the model needs to decide when to call them. */
public final class ToolWithoutDescriptionCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("springai.tool-without-description");

    private static final String TOOL = "org.springframework.ai.tool.annotation.Tool";

    private static final WhyItMatters WHY = new WhyItMatters(
            "The description is how the model learns what a tool does and when to use it. Without one, it has to guess"
                    + " from the method name, so it may skip the tool or call it at the wrong moment.",
            List.of(new Reference(
                    "Spring AI: Tool calling",
                    URI.create("https://docs.spring.io/spring-ai/reference/api/tools.html"))));

    private static final HowToFix FIX =
            HowToFix.withoutPatch("Add @Tool(description = \"...\") that says what the tool does and when to use it.");

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
        return file.unit().findAll(MethodDeclaration.class).stream()
                .filter(method -> file.annotation(method, TOOL)
                        .filter(ToolWithoutDescriptionCheck::lacksDescription)
                        .isPresent())
                .map(method -> finding(
                        file,
                        method.getName(),
                        Severity.MINOR,
                        Confidence.HIGH,
                        "Tool " + method.getNameAsString() + " has no description",
                        WHY,
                        FIX))
                .toList();
    }

    private static boolean lacksDescription(AnnotationExpr annotation) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return annotation.isMarkerAnnotationExpr();
        }
        return normal.getPairs().stream()
                .noneMatch(pair -> pair.getNameAsString().equals("description")
                        && !pair.getValue().toString().equals("\"\""));
    }
}
