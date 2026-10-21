package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.type.Type;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.StringExpressions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A loop over entities loaded from a repository that reads a lazily loaded association of each one: one query for the
 * list, then one more per element. The association is checked on the entity's own source.
 */
public final class NPlusOneCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.n-plus-one");

    private static final List<String> TO_MANY = List.of("OneToMany", "ManyToMany");
    private static final List<String> TO_ONE = List.of("ManyToOne", "OneToOne");
    private static final List<String> PERSISTENCE = List.of("jakarta.persistence.", "javax.persistence.");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Each iteration triggers its own query to load the association. With a few rows in development this is"
                    + " invisible; with thousands in production one request runs thousands of queries.",
            List.of(SpringCode.springDocs(
                    "Spring Data JPA: Fetching with entity graphs",
                    "spring-data/jpa/reference/jpa/query-methods.html#jpa.entity-graph")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Load the association together with the list: a query with JOIN FETCH, an @EntityGraph on the repository"
                    + " method, or a projection that selects only the needed columns.");

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
        for (ForEachStmt loop : file.unit().findAll(ForEachStmt.class)) {
            if (!loadsFromRepository(loop.getIterable())
                    || loop.getVariable().getVariables().size() != 1) {
                continue;
            }
            String element = loop.getVariable().getVariables().get(0).getNameAsString();
            Type elementType = loop.getVariable().getVariables().get(0).getType();
            Optional<TypeDeclaration<?>> entity =
                    file.declarationOf(elementType).filter(declaration -> SpringCode.isEntity(file, elementType));
            if (entity.isEmpty()) {
                continue;
            }
            for (MethodCallExpr getter :
                    loop.getBody().findAll(MethodCallExpr.class, call -> readsFrom(call, element))) {
                String property = propertyOf(getter.getNameAsString());
                if (isLazyAssociation(file, entity.get(), property)) {
                    findings.add(finding(
                            file,
                            getter,
                            Severity.MAJOR,
                            Confidence.MEDIUM,
                            getter + " loads " + property + " once per loop iteration (N+1 queries)",
                            WHY,
                            FIX));
                }
            }
        }
        return findings;
    }

    private static boolean loadsFromRepository(Expression iterable) {
        Expression source = StringExpressions.followLocal(iterable);
        return source instanceof MethodCallExpr call
                && call.getScope()
                        .map(scope -> scope.toString().toLowerCase(Locale.ROOT).endsWith("repository"))
                        .orElse(false);
    }

    private static boolean readsFrom(MethodCallExpr call, String element) {
        return call.getArguments().isEmpty()
                && call.getNameAsString().startsWith("get")
                && call.getNameAsString().length() > 3
                && call.getScope()
                        .map(scope -> scope instanceof NameExpr name
                                && name.getNameAsString().equals(element))
                        .orElse(false);
    }

    private static String propertyOf(String getter) {
        String name = getter.substring(3);
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** To-many associations are lazy unless marked EAGER; to-one associations only when marked LAZY. */
    private static boolean isLazyAssociation(JavaFile file, TypeDeclaration<?> entity, String property) {
        for (FieldDeclaration field : entity.getFields()) {
            if (field.getVariables().stream()
                    .noneMatch(variable -> variable.getNameAsString().equals(property))) {
                continue;
            }
            String annotations = field.getAnnotations().toString();
            if (isRelation(file, field, TO_MANY)) {
                return !annotations.contains("FetchType.EAGER");
            }
            return isRelation(file, field, TO_ONE) && annotations.contains("FetchType.LAZY");
        }
        return false;
    }

    private static boolean isRelation(JavaFile file, FieldDeclaration field, List<String> relations) {
        return relations.stream()
                .anyMatch(relation -> PERSISTENCE.stream()
                        .anyMatch(persistence -> file.hasAnnotation(field, persistence + relation)));
    }
}
