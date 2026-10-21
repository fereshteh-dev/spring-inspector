package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
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
import io.github.fereshtehdev.springinspector.javamodel.StringExpressions;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL or JPQL built by concatenating or formatting runtime values and handed to a query API. The query text must look
 * like SQL, which keeps unrelated methods called {@code update} or {@code execute} out.
 */
public final class SqlStringConcatenationCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.sql-concatenation");

    private static final Set<String> QUERY_METHODS = Set.of(
            "query",
            "queryForObject",
            "queryForList",
            "queryForMap",
            "queryForRowSet",
            "queryForStream",
            "update",
            "batchUpdate",
            "execute",
            "sql",
            "createQuery",
            "createNativeQuery",
            "prepareStatement",
            "prepareCall",
            "executeQuery",
            "executeUpdate",
            "executeLargeUpdate",
            "addBatch");

    private static final List<String> QUERY_TYPE_PACKAGES = List.of(
            "org.springframework.jdbc.",
            "org.springframework.r2dbc.",
            "jakarta.persistence.",
            "javax.persistence.",
            "java.sql.",
            "org.hibernate.");

    private static final Pattern SQL = Pattern.compile(
            "\\b(select\\b.*\\bfrom|insert\\s+into|update\\s+\\S+\\s+set|delete\\s+from|merge\\s+into|where\\b|order\\s+by)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final WhyItMatters WHY = new WhyItMatters(
            "Values concatenated into a query become part of the SQL itself. A value that contains a quote or SQL"
                    + " keywords changes what the query does: it can read other users' data, bypass checks or delete"
                    + " tables.",
            List.of(
                    new Reference(
                            "CWE-89: SQL injection", URI.create("https://cwe.mitre.org/data/definitions/89.html")),
                    new Reference(
                            "OWASP SQL Injection Prevention Cheat Sheet",
                            URI.create("https://cheatsheetseries.owasp.org/cheatsheets/"
                                    + "SQL_Injection_Prevention_Cheat_Sheet.html"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Keep the query text constant and pass the values as bind parameters: `?` or `:name` placeholders with"
                    + " JdbcTemplate/JdbcClient, `setParameter` with JPA. Identifiers such as column names cannot be"
                    + " bound; pick them from a fixed allow-list instead.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SECURITY;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Finding> findings = new ArrayList<>();
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            if (!QUERY_METHODS.contains(call.getNameAsString())
                    || call.getArguments().isEmpty()) {
                continue;
            }
            Expression query = call.getArgument(0);
            if (!StringExpressions.isBuiltFromValues(query)
                    || !SQL.matcher(StringExpressions.literalText(query)).find()) {
                continue;
            }
            confidence(file, call)
                    .ifPresent(confidence -> findings.add(finding(
                            file,
                            call,
                            confidence == Confidence.HIGH ? Severity.BLOCKER : Severity.MAJOR,
                            confidence,
                            "SQL built from runtime values is passed to " + call.getNameAsString() + "()",
                            WHY,
                            FIX)));
        }
        return findings;
    }

    /** HIGH when the call resolves to a database API, MEDIUM when it cannot be resolved, empty for other APIs. */
    private static Optional<Confidence> confidence(JavaFile file, MethodCallExpr call) {
        Optional<String> declaringType = file.declaringTypeOf(call);
        if (declaringType.isEmpty()) {
            return Optional.of(Confidence.MEDIUM);
        }
        boolean databaseApi = QUERY_TYPE_PACKAGES.stream().anyMatch(declaringType.get()::startsWith);
        return databaseApi ? Optional.of(Confidence.HIGH) : Optional.empty();
    }
}
