package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Classes with more than {@code max-lines} (default {@value #DEFAULT_MAX_LINES}) lines of code, blank lines and
 * comments excluded.
 */
public final class LargeClassCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.large-class");
    static final int DEFAULT_MAX_LINES = 500;

    private static final WhyItMatters WHY = new WhyItMatters(
            "A very large class almost always has several responsibilities. Every change risks the others, tests need"
                    + " a lot of setup, and the class becomes the place every new feature lands.",
            List.of());

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Find the groups of fields and methods that change together and move each group into its own class.");

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
        int maxLines = file.options().positiveInteger("max-lines").orElse(DEFAULT_MAX_LINES);
        List<Finding> findings = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : file.unit().findAll(ClassOrInterfaceDeclaration.class)) {
            int lines = CodeLines.count(file, type);
            if (lines > maxLines) {
                findings.add(finding(
                        file,
                        type.getName(),
                        file.source()
                                .snippet(LineRange.line(
                                        file.lines(type.getName()).start())),
                        Severity.MINOR,
                        Confidence.HIGH,
                        type.getNameAsString() + " has " + lines + " lines of code; the limit is " + maxLines,
                        WHY,
                        FIX));
            }
        }
        return findings;
    }
}
