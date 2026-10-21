package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.util.List;

/**
 * Methods with more than {@code max-parameters} (default {@value #DEFAULT_MAX_PARAMETERS}) parameters. Constructors,
 * where dependency injection legitimately lists dependencies, and overriding methods, whose signature is not theirs to
 * choose, are left out.
 */
public final class LongParameterListCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.long-parameter-list");
    static final int DEFAULT_MAX_PARAMETERS = 7;

    private static final WhyItMatters WHY = new WhyItMatters(
            "Long parameter lists are easy to call with arguments in the wrong order, especially several of the same"
                    + " type, and usually mean the method does more than one thing or the values belong together.",
            List.of());

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Group values that travel together into a record, or split the method so each part needs fewer inputs.");

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
        int maxParameters = file.options().positiveInteger("max-parameters").orElse(DEFAULT_MAX_PARAMETERS);
        return file
                .unit()
                .findAll(
                        MethodDeclaration.class,
                        method -> method.getParameters().size() > maxParameters
                                && !file.hasAnnotation(method, "java.lang.Override"))
                .stream()
                .map(method -> finding(
                        file,
                        method.getName(),
                        Severity.MINOR,
                        Confidence.HIGH,
                        method.getNameAsString() + " takes "
                                + method.getParameters().size() + " parameters; the limit is " + maxParameters,
                        WHY,
                        FIX))
                .toList();
    }
}
