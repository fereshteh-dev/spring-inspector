package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
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
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dependencies injected into fields. Test classes are left alone: field injection is the normal style there. When the
 * class has no constructor yet, the fix replaces all of its injected fields with one constructor.
 */
public final class FieldInjectionCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.field-injection");

    private static final List<String> INJECTION_ANNOTATIONS = List.of(
            "org.springframework.beans.factory.annotation.Autowired", "jakarta.inject.Inject", "javax.inject.Inject");

    private static final Set<String> GENERATED_CONSTRUCTOR_ANNOTATIONS =
            Set.of("RequiredArgsConstructor", "AllArgsConstructor", "NoArgsConstructor", "Data", "Value", "Builder");

    private static final WhyItMatters WHY = new WhyItMatters(
            "An injected field hides a dependency: the class can be created without it, so tests need reflection or a"
                    + " Spring context, the field cannot be final, and a growing list of dependencies goes unnoticed."
                    + " Constructor injection makes dependencies explicit, immutable and checked at startup.",
            List.of(new Reference(
                    "Spring Framework: Dependency Injection",
                    URI.create("https://docs.spring.io/spring-framework/reference/core/beans/dependencies/"
                            + "factory-collaborators.html"))));

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
        if (isTestSource(file.path())) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : file.unit().findAll(ClassOrInterfaceDeclaration.class)) {
            if (type.isInterface() || isTestClass(type)) {
                continue;
            }
            List<FieldDeclaration> injected = type.getFields().stream()
                    .filter(field -> injectionAnnotation(file, field).isPresent())
                    .toList();
            if (injected.isEmpty()) {
                continue;
            }
            HowToFix fix = fix(file, type, injected);
            for (FieldDeclaration field : injected) {
                findings.add(finding(
                        file,
                        field,
                        Severity.MINOR,
                        Confidence.HIGH,
                        "Field " + names(field) + " of " + type.getNameAsString() + " is injected by field injection",
                        WHY,
                        fix));
            }
        }
        return findings;
    }

    private static HowToFix fix(JavaFile file, ClassOrInterfaceDeclaration type, List<FieldDeclaration> injected) {
        String explanation = "Declare the dependencies as final fields and inject them through a constructor; Spring"
                + " uses a class's only constructor without @Autowired.";
        if (!canConvert(type, injected)) {
            return HowToFix.withoutPatch(explanation);
        }
        String separator = file.lineSeparator();
        String memberIndent = file.indentation(injected.getFirst());
        String classIndent = file.indentation(type);
        String unit = memberIndent.startsWith(classIndent) && memberIndent.length() > classIndent.length()
                ? memberIndent.substring(classIndent.length())
                : "    ";
        List<TextEdit> edits = new ArrayList<>();
        List<String> parameters = new ArrayList<>();
        List<String> assignments = new ArrayList<>();
        for (FieldDeclaration field : injected) {
            VariableDeclarator variable = field.getVariable(0);
            edits.add(file.delete(injectionAnnotation(file, field).orElseThrow()));
            edits.add(file.insertBefore(variable.getType(), "final "));
            parameters.add(file.text(variable.getType()) + " " + variable.getNameAsString());
            assignments.add(memberIndent + unit + "this." + variable.getNameAsString() + " = "
                    + variable.getNameAsString() + ";" + separator);
        }
        String visibility = type.isPublic() ? "public " : "";
        String constructor = separator
                + memberIndent + visibility + type.getNameAsString() + "(" + String.join(", ", parameters) + ") {"
                + separator
                + String.join("", assignments)
                + memberIndent + "}" + separator;
        edits.add(file.insertAfterLine(type.getFields().getLast(), constructor));
        edits.addAll(unusedImportRemovals(file, injected));
        return HowToFix.withPatch(
                explanation + " The patch converts every injected field of " + type.getNameAsString() + ".",
                file.patch(edits));
    }

    /**
     * Only plain cases are rewritten: no constructor to merge with, no generated constructors, and fields whose only
     * annotation is a bare injection annotation, so no qualifier or optional injection is lost.
     */
    private static boolean canConvert(ClassOrInterfaceDeclaration type, List<FieldDeclaration> injected) {
        boolean nestedInner = type.isNestedType() && !type.isStatic();
        boolean generatesConstructors = type.getAnnotations().stream()
                .anyMatch(annotation -> GENERATED_CONSTRUCTOR_ANNOTATIONS.contains(simpleName(annotation)));
        if (!type.getConstructors().isEmpty() || nestedInner || generatesConstructors) {
            return false;
        }
        return injected.stream()
                .allMatch(field -> field.getVariables().size() == 1
                        && field.getVariable(0).getInitializer().isEmpty()
                        && !field.hasModifier(Modifier.Keyword.STATIC)
                        && field.getAnnotations().size() == 1
                        && field.getAnnotation(0) instanceof MarkerAnnotationExpr);
    }

    /** Imports of injection annotations that the patch removes the last use of. */
    private static List<TextEdit> unusedImportRemovals(JavaFile file, List<FieldDeclaration> injected) {
        List<TextEdit> removals = new ArrayList<>();
        for (String annotation : INJECTION_ANNOTATIONS) {
            long uses = file.unit()
                    .findAll(AnnotationExpr.class, use -> file.refersTo(use.getNameAsString(), annotation))
                    .size();
            long removed = injected.stream()
                    .filter(field -> file.hasAnnotation(field, annotation))
                    .count();
            if (uses > 0 && uses == removed) {
                file.unit().getImports().stream()
                        .filter(declaration -> !declaration.isStatic()
                                && !declaration.isAsterisk()
                                && declaration.getNameAsString().equals(annotation))
                        .findFirst()
                        .ifPresent(declaration -> removals.add(file.delete(declaration)));
            }
        }
        return removals;
    }

    private static Optional<AnnotationExpr> injectionAnnotation(JavaFile file, FieldDeclaration field) {
        return INJECTION_ANNOTATIONS.stream()
                .map(annotation -> file.annotation(field, annotation))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static boolean isTestSource(Path path) {
        for (int i = 0; i + 1 < path.getNameCount(); i++) {
            if (path.getName(i).toString().equals("src")
                    && path.getName(i + 1).toString().equals("test")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTestClass(ClassOrInterfaceDeclaration type) {
        return type.getAnnotations().stream()
                .anyMatch(annotation -> simpleName(annotation).endsWith("Test"));
    }

    private static String simpleName(AnnotationExpr annotation) {
        return annotation.getName().getIdentifier();
    }

    private static String names(FieldDeclaration field) {
        return field.getVariables().stream()
                .map(VariableDeclarator::getNameAsString)
                .collect(Collectors.joining(", "));
    }
}
