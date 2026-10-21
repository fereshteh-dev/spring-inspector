package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.expr.MethodCallExpr;
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
import java.util.Locale;

/** {@code repository.findAll()} without paging in a controller, where the table's size grows with production data. */
public final class FindAllWithoutPagingCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.find-all-unpaged");

    private static final WhyItMatters WHY = new WhyItMatters(
            "findAll() loads every row into memory and serialises it in one response. It is fast with test data and"
                    + " slows down, or runs out of memory, as the table grows in production.",
            List.of(
                    SpringCode.springDocs(
                            "Spring Data: Paging and sorting",
                            "spring-data/commons/reference/repositories/query-methods-details.html#repositories.special-parameters")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Accept a Pageable in the handler and call findAll(pageable), or query only the rows the screen needs.");

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
                .findAll(
                        MethodCallExpr.class,
                        call -> call.getNameAsString().equals("findAll")
                                && call.getArguments().isEmpty())
                .stream()
                .filter(call -> call.getScope()
                        .map(scope -> scope.toString().toLowerCase(Locale.ROOT).endsWith("repository"))
                        .orElse(false))
                .filter(call -> SpringCode.enclosingType(call)
                        .map(type -> SpringCode.isController(file, type))
                        .orElse(false))
                .map(call -> finding(
                        file,
                        call,
                        Severity.MINOR,
                        Confidence.MEDIUM,
                        call + " loads the whole table for one request",
                        WHY,
                        FIX))
                .toList();
    }
}
