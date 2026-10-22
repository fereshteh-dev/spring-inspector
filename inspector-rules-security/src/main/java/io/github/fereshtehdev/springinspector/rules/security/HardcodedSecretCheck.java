package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Secrets written into source code: well-known token formats anywhere, and secret-looking literals kept in variables,
 * fields or assignments named like passwords, tokens or keys. Test sources are skipped; reported snippets are masked.
 */
public final class HardcodedSecretCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.hardcoded-secret");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Anything in the source code is in every clone, build, artifact and backup, readable by everyone with access"
                    + " to the repository, forever: git history keeps it after the line is deleted. A leaked key has to"
                    + " be treated as compromised and rotated.",
            List.of(
                    SecurityReferences.cwe(798, "Use of hard-coded credentials"),
                    SecurityReferences.owaspCheatSheet("Secrets Management", "Secrets_Management_Cheat_Sheet")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Rotate the secret now, then read it from configuration: @Value(\"${...}\") or a @ConfigurationProperties"
                    + " record, backed by an environment variable or a secret store. Never commit the value.");

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
        if (JavaCheck.isTestSource(file.path())) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        file.unit()
                .walk(node -> literalValue(node)
                        .ifPresent(value -> check(file, node, value).ifPresent(findings::add)));
        return findings;
    }

    private Optional<Finding> check(JavaFile file, Node literal, String value) {
        Optional<String> token = Secrets.knownTokenKind(value);
        if (token.isPresent()) {
            return Optional.of(
                    report(file, literal, value, Severity.BLOCKER, "A " + token.get() + " is written in the code"));
        }
        Optional<String> holder = holderName(literal);
        if (holder.isPresent() && Secrets.isSensitiveName(holder.get()) && Secrets.couldBeSecret(value)) {
            return Optional.of(report(
                    file, literal, value, Severity.MAJOR, "A secret is assigned to " + holder.get() + " in the code"));
        }
        return Optional.empty();
    }

    private Finding report(JavaFile file, Node literal, String value, Severity severity, String what) {
        return finding(
                file,
                literal,
                Secrets.mask(file.snippet(literal), value),
                severity,
                severity == Severity.BLOCKER ? Confidence.HIGH : Confidence.MEDIUM,
                what,
                WHY,
                FIX);
    }

    private static Optional<String> literalValue(Node node) {
        if (node instanceof StringLiteralExpr literal) {
            return Optional.of(literal.getValue());
        }
        if (node instanceof TextBlockLiteralExpr block) {
            return Optional.of(block.getValue());
        }
        return Optional.empty();
    }

    /** The variable, field or assignment target a literal is stored in directly. */
    private static Optional<String> holderName(Node literal) {
        return literal.getParentNode().flatMap(parent -> switch (parent) {
            case VariableDeclarator variable -> Optional.of(variable.getNameAsString());
            case AssignExpr assign -> Optional.of(assign.getTarget().toString());
            default -> Optional.empty();
        });
    }
}
