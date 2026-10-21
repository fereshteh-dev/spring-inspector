package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@code @RequestBody} whose type declares validation constraints, received without {@code @Valid}, so the
 * constraints are never checked. Types without constraints are not reported: {@code @Valid} would do nothing there.
 */
public final class MissingValidCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.missing-valid");

    private static final List<String> VALIDATION_PACKAGES = List.of("jakarta.validation", "javax.validation");
    private static final String VALIDATED = "org.springframework.validation.annotation.Validated";

    private static final WhyItMatters WHY = new WhyItMatters(
            "Constraints such as @NotBlank or @Size on the request type are only checked when the parameter is"
                    + " marked @Valid. Without it, invalid or malicious input reaches the service unchecked.",
            List.of(SpringCode.springDocs(
                    "Spring Framework: Validation of controller method parameters",
                    "spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html")));

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SPRING_BOOT;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Finding> findings = new ArrayList<>();
        for (MethodDeclaration handler :
                file.unit().findAll(MethodDeclaration.class, method -> SpringCode.isHandler(file, method))) {
            for (Parameter parameter : handler.getParameters()) {
                Optional<AnnotationExpr> body = file.annotation(parameter, SpringCode.WEB + "RequestBody");
                if (body.isEmpty() || isValidated(file, parameter)) {
                    continue;
                }
                constraintPackage(file, parameter)
                        .ifPresent(validation -> findings.add(finding(
                                file,
                                parameter,
                                Severity.MINOR,
                                Confidence.HIGH,
                                "The constraints on " + parameter.getType() + " are not checked: @Valid is missing",
                                WHY,
                                HowToFix.withPatch(
                                        "Mark the request body @Valid; Spring then answers invalid requests with 400.",
                                        file.patch(edits(file, body.get(), validation + ".Valid"))))));
            }
        }
        return findings;
    }

    private static boolean isValidated(JavaFile file, Parameter parameter) {
        return file.hasAnnotation(parameter, VALIDATED)
                || VALIDATION_PACKAGES.stream()
                        .anyMatch(validation -> file.hasAnnotation(parameter, validation + ".Valid"));
    }

    /** The validation package the request type's constraints come from, if it has any. */
    private static Optional<String> constraintPackage(JavaFile file, Parameter parameter) {
        Optional<TypeDeclaration<?>> declaration = file.declarationOf(parameter.getType());
        return declaration
                .flatMap(type -> type.findCompilationUnit())
                .flatMap(unit -> unit.getImports().stream()
                        .map(ImportDeclaration::getNameAsString)
                        .flatMap(imported -> VALIDATION_PACKAGES.stream()
                                .filter(validation -> imported.startsWith(validation + ".constraints")))
                        .findFirst());
    }

    private static List<TextEdit> edits(JavaFile file, AnnotationExpr requestBody, String valid) {
        List<TextEdit> edits = new ArrayList<>();
        edits.add(file.insertBefore(requestBody, "@Valid "));
        file.addImport(valid).ifPresent(edits::add);
        return edits;
    }
}
