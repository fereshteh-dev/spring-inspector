package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class reading {@code min-values} (default 4) or more settings with {@code @Value} under the same prefix, which a
 * {@code @ConfigurationProperties} record expresses as one typed, validated object.
 */
public final class ValueSprawlCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.value-sprawl");
    static final int DEFAULT_MIN_VALUES = 4;

    private static final String VALUE = "org.springframework.beans.factory.annotation.Value";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)\\.[^}:]+");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Scattered @Value strings are checked only at startup, one by one, cannot be validated together and"
                    + " repeat the prefix everywhere. A @ConfigurationProperties record binds the group once, with"
                    + " types, defaults, validation and IDE completion.",
            List.of(
                    SpringCode.springDocs(
                            "Spring Boot: Type-safe configuration properties",
                            "spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties")));

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
        int minValues = file.options().positiveInteger("min-values").orElse(DEFAULT_MIN_VALUES);
        List<Finding> findings = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : file.unit().findAll(ClassOrInterfaceDeclaration.class)) {
            Map<String, Integer> prefixes = new TreeMap<>();
            for (AnnotationExpr value : type.findAll(
                    AnnotationExpr.class, annotation -> file.refersTo(annotation.getNameAsString(), VALUE))) {
                boolean ownMember = Nodes.ancestor(value, ClassOrInterfaceDeclaration.class)
                        .map(type::equals)
                        .orElse(false);
                prefix(value).filter(prefix -> ownMember).ifPresent(prefix -> prefixes.merge(prefix, 1, Integer::sum));
            }
            prefixes.forEach((prefix, count) -> {
                if (count >= minValues) {
                    findings.add(finding(
                            file,
                            type.getName(),
                            Severity.MINOR,
                            Confidence.HIGH,
                            type.getNameAsString() + " reads " + count + " settings under " + prefix + " with @Value",
                            WHY,
                            HowToFix.withoutPatch("Bind them with a record annotated @ConfigurationProperties(\""
                                    + prefix + "\") and inject that record instead.")));
                }
            });
        }
        return findings;
    }

    private static Optional<String> prefix(AnnotationExpr value) {
        if (value instanceof SingleMemberAnnotationExpr single
                && single.getMemberValue() instanceof StringLiteralExpr text) {
            Matcher matcher = PLACEHOLDER.matcher(text.getValue());
            return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
        }
        return Optional.empty();
    }
}
