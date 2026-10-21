package io.github.fereshtehdev.springinspector.rules.spring;

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

/** Request handlers that return JPA entities, directly or inside {@code List}, {@code Page} or {@code ResponseEntity}. */
public final class EntityInControllerCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.entity-in-controller");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Serialising an entity exposes every field, including ones added later such as password hashes or internal"
                    + " flags, ties the API to the database schema, and triggers lazy loading or serialisation loops"
                    + " outside the transaction.",
            List.of(SpringCode.springDocs(
                    "Spring Boot: Open Session in View",
                    "spring-boot/reference/data/sql.html#data.sql.jpa-and-spring-data.open-entity-manager-in-view")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Return a response record with exactly the fields the client needs, built from the entity in the service"
                    + " or controller.");

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
        return file.unit().findAll(MethodDeclaration.class, method -> SpringCode.isHandler(file, method)).stream()
                .flatMap(method -> SpringCode.entityIn(file, method.getType()).stream()
                        .map(entity -> finding(
                                file,
                                method.getType(),
                                Severity.MAJOR,
                                Confidence.HIGH,
                                method.getNameAsString() + " returns the entity " + entity + " to the client",
                                WHY,
                                FIX)))
                .toList();
    }
}
