package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
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

/** {@code @Autowired} on the only constructor of a class, where Spring uses that constructor anyway. */
public final class AutowiredConstructorCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.autowired-constructor");

    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

    private static final WhyItMatters WHY = new WhyItMatters(
            "Since Spring 4.3 a class with a single constructor is autowired through it without the annotation. The"
                    + " annotation is noise, and it couples a plain class to Spring for no benefit.",
            List.of(SpringCode.springDocs(
                    "Spring Framework: Using @Autowired",
                    "spring-framework/reference/core/beans/annotation-config/autowired.html")));

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
        for (ClassOrInterfaceDeclaration type : file.unit().findAll(ClassOrInterfaceDeclaration.class)) {
            List<ConstructorDeclaration> constructors = type.getConstructors();
            if (constructors.size() != 1) {
                continue;
            }
            Optional<AnnotationExpr> autowired = file.annotation(constructors.getFirst(), AUTOWIRED);
            if (autowired.isPresent() && autowired.get() instanceof MarkerAnnotationExpr) {
                findings.add(finding(
                        file,
                        autowired.get(),
                        Severity.MINOR,
                        Confidence.HIGH,
                        "@Autowired is not needed on the only constructor of " + type.getNameAsString(),
                        WHY,
                        HowToFix.withPatch("Remove the annotation.", file.patch(edits(file, autowired.get())))));
            }
        }
        return findings;
    }

    private static List<TextEdit> edits(JavaFile file, AnnotationExpr autowired) {
        List<TextEdit> edits = new ArrayList<>();
        edits.add(file.delete(autowired));
        long uses = file.unit()
                .findAll(AnnotationExpr.class, annotation -> file.refersTo(annotation.getNameAsString(), AUTOWIRED))
                .size();
        if (uses == 1) {
            file.unit().getImports().stream()
                    .filter(declaration -> !declaration.isAsterisk()
                            && declaration.getNameAsString().equals(AUTOWIRED))
                    .findFirst()
                    .map(ImportDeclaration.class::cast)
                    .ifPresent(declaration -> edits.add(file.delete(declaration)));
        }
        return edits;
    }
}
