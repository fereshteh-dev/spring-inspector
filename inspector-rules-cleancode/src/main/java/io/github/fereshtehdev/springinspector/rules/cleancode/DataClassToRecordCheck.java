package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.project.VersionRange;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * A class that only holds data: private final fields, one constructor, getters and at most equals, hashCode and
 * toString. Annotated classes are skipped, since frameworks such as JPA need classes. Java 16 and later.
 */
public final class DataClassToRecordCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.data-class-to-record");

    private static final Set<String> OBJECT_METHODS = Set.of("equals", "hashCode", "toString");

    private static final WhyItMatters WHY = new WhyItMatters(
            "A record states that the class is plain data and generates the constructor, accessors, equals, hashCode"
                    + " and toString, so they cannot drift out of sync with the fields.",
            List.of(new Reference("JEP 395: Records", URI.create("https://openjdk.org/jeps/395"))));

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.CLEAN_CODE;
    }

    @Override
    public Applicability applicability() {
        return Applicability.always().requiringJava(VersionRange.atLeast(Version.of(16, 0, 0)));
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        return file.unit().findAll(ClassOrInterfaceDeclaration.class, DataClassToRecordCheck::isDataClass).stream()
                .map(type -> finding(
                        file,
                        type.getName(),
                        Severity.INFO,
                        Confidence.HIGH,
                        type.getNameAsString() + " only holds data and can be a record",
                        WHY,
                        HowToFix.withoutPatch("Declare it as record " + type.getNameAsString() + "(" + components(type)
                                + ") and replace getX() calls with x().")))
                .toList();
    }

    private static boolean isDataClass(ClassOrInterfaceDeclaration type) {
        List<FieldDeclaration> fields =
                type.getFields().stream().filter(field -> !field.isStatic()).toList();
        boolean plainClass = !type.isInterface()
                && !type.isAbstract()
                && type.getExtendedTypes().isEmpty()
                && type.getAnnotations().isEmpty()
                && type.getMembers().stream().noneMatch(member -> member.isTypeDeclaration());
        boolean dataFields = !fields.isEmpty()
                && fields.stream()
                        .allMatch(field -> field.isPrivate()
                                && field.isFinal()
                                && field.getAnnotations().isEmpty());
        boolean onlyAccessors = type.getMethods().stream().allMatch(DataClassToRecordCheck::isAccessorOrObjectMethod);
        return plainClass
                && dataFields
                && onlyAccessors
                && type.getConstructors().size() == 1;
    }

    private static boolean isAccessorOrObjectMethod(MethodDeclaration method) {
        if (OBJECT_METHODS.contains(method.getNameAsString())) {
            return true;
        }
        boolean getter = method.getParameters().isEmpty()
                && method.getBody()
                        .map(body -> body.getStatements().size() == 1 && body.getStatement(0) instanceof ReturnStmt)
                        .orElse(false);
        return getter && !method.isStatic();
    }

    private static String components(ClassOrInterfaceDeclaration type) {
        return String.join(
                ", ",
                type.getFields().stream()
                        .filter(field -> !field.isStatic())
                        .flatMap(field -> field.getVariables().stream())
                        .map(variable -> variable.getType() + " " + variable.getNameAsString())
                        .toList());
    }
}
