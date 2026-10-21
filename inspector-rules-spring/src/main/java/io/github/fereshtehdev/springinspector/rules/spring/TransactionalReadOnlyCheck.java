package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.body.MethodDeclaration;
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
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** A plain {@code @Transactional} on a method whose name says it only reads, such as {@code findByEmail}. */
public final class TransactionalReadOnlyCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.transactional-read-only");

    private static final Pattern QUERY_METHOD =
            Pattern.compile("(?:find|get|load|read|count|exists|list|search|fetch|query)(?:[A-Z].*)?");

    /** {@code getOrCreate} or {@code findAndLock} read first but then write. */
    private static final Pattern WRITES =
            Pattern.compile("Create|Save|Update|Insert|Delete|Remove|Register|Store|Persist|Lock");

    private static final WhyItMatters WHY = new WhyItMatters(
            "A read-only transaction lets Hibernate skip dirty checking and flushing, and lets the driver and database"
                    + " route the query to a replica or optimise locking. Without it every read pays for a write"
                    + " transaction.",
            List.of(SpringCode.springDocs(
                    "Spring Framework: @Transactional settings",
                    "spring-framework/reference/data-access/transaction/declarative/annotations.html"
                            + "#transaction-declarative-attransactional-settings")));

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
        return file.unit().findAll(MethodDeclaration.class).stream()
                .filter(method -> QUERY_METHOD.matcher(method.getNameAsString()).matches()
                        && !WRITES.matcher(method.getNameAsString()).find())
                .flatMap(method -> plainTransactional(file, method).stream()
                        .map(annotation -> finding(
                                file,
                                annotation,
                                Severity.MINOR,
                                Confidence.HIGH,
                                method.getNameAsString() + " only reads but its transaction is not read-only",
                                WHY,
                                HowToFix.withPatch(
                                        "Mark the transaction read-only.",
                                        file.patch(List.of(file.replace(
                                                annotation,
                                                "@" + annotation.getNameAsString() + "(readOnly = true)")))))))
                .toList();
    }

    private static Optional<AnnotationExpr> plainTransactional(JavaFile file, MethodDeclaration method) {
        return file.annotation(method, SpringCode.SPRING_TRANSACTIONAL)
                .filter(annotation -> annotation instanceof MarkerAnnotationExpr);
    }
}
