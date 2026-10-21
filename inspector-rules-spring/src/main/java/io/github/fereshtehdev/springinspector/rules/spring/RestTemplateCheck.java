package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.project.VersionRange;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.Nodes;
import java.util.ArrayList;
import java.util.List;

/** {@code RestTemplate} fields and instances in Spring Boot 3.2 and later, where {@code RestClient} replaces it. */
public final class RestTemplateCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.rest-template");

    private static final String REST_TEMPLATE = "org.springframework.web.client.RestTemplate";

    private static final WhyItMatters WHY = new WhyItMatters(
            "RestTemplate is in maintenance mode and receives no new features. RestClient, available since Spring"
                    + " Framework 6.1, offers the same synchronous model with a fluent API, and shares message"
                    + " converters and interceptors with it.",
            List.of(SpringCode.springDocs(
                    "Spring Framework: REST clients", "spring-framework/reference/integration/rest-clients.html")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Use RestClient for new code. Existing configuration carries over with RestClient.create(restTemplate) or"
                    + " RestClient.builder(restTemplate).");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SPRING_BOOT;
    }

    @Override
    public Applicability applicability() {
        return Applicability.always().requiringSpringBoot(VersionRange.atLeast(Version.of(3, 2, 0)));
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Finding> findings = new ArrayList<>();
        for (FieldDeclaration field : file.unit().findAll(FieldDeclaration.class)) {
            if (field.getElementType().isClassOrInterfaceType()
                    && file.refersTo(
                            field.getElementType().asClassOrInterfaceType().getNameWithScope(), REST_TEMPLATE)) {
                findings.add(report(
                        file,
                        field,
                        "RestTemplate field " + field.getVariable(0).getNameAsString()));
            }
        }
        for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
            boolean inField = Nodes.ancestor(creation, FieldDeclaration.class).isPresent();
            if (!inField && file.refersTo(creation.getType().getNameWithScope(), REST_TEMPLATE)) {
                findings.add(report(file, creation, "A new RestTemplate"));
            }
        }
        return findings;
    }

    private Finding report(JavaFile file, Node node, String what) {
        return finding(
                file,
                node,
                Severity.INFO,
                Confidence.HIGH,
                what + " is used where RestClient is the current API",
                WHY,
                FIX);
    }
}
