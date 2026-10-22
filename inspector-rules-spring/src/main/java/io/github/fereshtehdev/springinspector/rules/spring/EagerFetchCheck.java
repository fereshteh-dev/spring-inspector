package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A collection mapping, {@code @OneToMany}, {@code @ManyToMany} or {@code @ElementCollection}, loaded eagerly. Single
 * associations are eager by default in JPA and are not reported.
 */
public final class EagerFetchCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.eager-fetch");

    private static final Set<String> COLLECTION_MAPPINGS = Set.of("OneToMany", "ManyToMany", "ElementCollection");

    private static final List<String> PERSISTENCE_PACKAGES = List.of("jakarta.persistence.", "javax.persistence.");

    private static final WhyItMatters WHY = new WhyItMatters(
            "An eager collection is loaded every time its owner is, by every query and on every screen, whether the"
                    + " code needs it or not. Hibernate often does it with one extra query per owner, and two eager"
                    + " collections on one entity multiply the rows a join returns. It cannot be made lazy for a"
                    + " single query later.",
            List.of(new Reference(
                    "Hibernate ORM User Guide: Fetching",
                    URI.create("https://docs.jboss.org/hibernate/orm/current/userguide/html_single/"
                            + "Hibernate_User_Guide.html#fetching"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Leave the collection lazy (the default for collections) and load it where a use case needs it: with a"
                    + " JOIN FETCH query or @EntityGraph on the repository method. Code that read the collection"
                    + " outside a transaction must then fetch it explicitly.");

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
        return file
                .unit()
                .findAll(NormalAnnotationExpr.class, annotation -> isCollectionMapping(file, annotation))
                .stream()
                .flatMap(annotation -> eagerFetch(annotation).stream()
                        .map(fetch -> finding(
                                file,
                                fetch,
                                Severity.MAJOR,
                                Confidence.HIGH,
                                "@" + annotation.getNameAsString() + " collection is loaded eagerly",
                                WHY,
                                FIX)))
                .toList();
    }

    private static boolean isCollectionMapping(JavaFile file, AnnotationExpr annotation) {
        String name = annotation.getName().getIdentifier();
        return COLLECTION_MAPPINGS.contains(name)
                && PERSISTENCE_PACKAGES.stream()
                        .anyMatch(prefix -> file.refersTo(annotation.getNameAsString(), prefix + name));
    }

    private static Optional<MemberValuePair> eagerFetch(NormalAnnotationExpr annotation) {
        return annotation.getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("fetch"))
                .filter(pair -> pair.getValue().toString().endsWith("EAGER"))
                .findFirst();
    }
}
