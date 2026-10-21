package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code Optional} as the type of a public or protected field or method parameter, where it becomes part of an API.
 * Private helpers passing an Optional along are a matter of local style and are not reported.
 */
public final class OptionalMisuseCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.optional-misuse");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Optional is meant for return values. As a field it is not serializable and adds an allocation per object;"
                    + " as a parameter it forces callers to wrap values and still allows null for the Optional itself.",
            List.of());

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.CLEAN_CODE;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Finding> findings = new ArrayList<>();
        for (FieldDeclaration field : file.unit().findAll(FieldDeclaration.class)) {
            if (isOptional(file, field.getElementType()) && (field.isPublic() || field.isProtected())) {
                findings.add(finding(
                        file,
                        field,
                        Severity.MINOR,
                        Confidence.MEDIUM,
                        "Field " + field.getVariable(0).getNameAsString() + " is an Optional",
                        WHY,
                        HowToFix.withoutPatch(
                                "Keep a nullable field and return Optional.ofNullable(field) from its" + " getter.")));
            }
        }
        for (MethodDeclaration method : file.unit().findAll(MethodDeclaration.class, OptionalMisuseCheck::isApi)) {
            for (Parameter parameter : method.getParameters()) {
                if (isOptional(file, parameter.getType())) {
                    findings.add(finding(
                            file,
                            parameter,
                            Severity.MINOR,
                            Confidence.MEDIUM,
                            "Parameter " + parameter.getNameAsString() + " of " + method.getNameAsString()
                                    + " is an Optional",
                            WHY,
                            HowToFix.withoutPatch("Overload the method, or accept a nullable value and handle the"
                                    + " absent case inside.")));
                }
            }
        }
        return findings;
    }

    private static boolean isApi(MethodDeclaration method) {
        return method.isPublic() || method.isProtected();
    }

    private static boolean isOptional(JavaFile file, Type type) {
        return type.isClassOrInterfaceType()
                && file.refersTo(type.asClassOrInterfaceType().getNameWithScope(), "java.util.Optional");
    }
}
