package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
import java.util.ArrayList;
import java.util.List;

/**
 * A JPA entity with Lombok's {@code @Data}, {@code @Value} or {@code @EqualsAndHashCode}, which compute equality and
 * hash codes from every field. {@code @EqualsAndHashCode(onlyExplicitlyIncluded = true)} is accepted.
 */
public final class LombokDataEntityCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.lombok-data-entity");

    private static final List<String> ENTITIES = List.of("jakarta.persistence.Entity", "javax.persistence.Entity");

    private static final List<String> ALL_FIELD_EQUALITY =
            List.of("lombok.Data", "lombok.Value", "lombok.EqualsAndHashCode");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Equality over every field does not fit an entity. Its hash code changes when the database assigns the id,"
                    + " so a saved entity gets lost in a HashSet. Comparing or printing it reads lazy associations, which"
                    + " triggers extra queries or a LazyInitializationException, and two entities that refer to each"
                    + " other recurse until the stack overflows.",
            List.of(
                    new Reference(
                            "Vlad Mihalcea: How to implement equals and hashCode using the JPA entity identifier",
                            URI.create(
                                    "https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Use @Getter and @Setter instead, and write equals and hashCode yourself: equal when both have the same"
                    + " non-null id, with a hash code that does not depend on the id, such as getClass().hashCode()."
                    + " Exclude associations from @ToString.");

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
            if (ENTITIES.stream().noneMatch(entity -> file.hasAnnotation(type, entity))) {
                continue;
            }
            for (String lombok : ALL_FIELD_EQUALITY) {
                file.annotation(type, lombok)
                        .filter(annotation -> !onlyExplicitlyIncluded(annotation))
                        .ifPresent(annotation -> findings.add(finding(
                                file,
                                annotation,
                                Severity.MAJOR,
                                Confidence.HIGH,
                                "Entity " + type.getNameAsString() + " uses @"
                                        + annotation.getName().getIdentifier()
                                        + ", which bases equals and hashCode on every field",
                                WHY,
                                FIX)));
            }
        }
        return findings;
    }

    private static boolean onlyExplicitlyIncluded(AnnotationExpr annotation) {
        return annotation instanceof NormalAnnotationExpr normal
                && normal.getPairs().stream()
                        .anyMatch(pair -> pair.getNameAsString().equals("onlyExplicitlyIncluded")
                                && pair.getValue().toString().equals("true"));
    }
}
