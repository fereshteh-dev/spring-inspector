package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Passwords kept without a real hash: {@code NoOpPasswordEncoder}, {@code {noop}} values and
 * {@code User.withDefaultPasswordEncoder()}. Tests often use these on purpose, so test code is not inspected.
 */
public final class PlaintextPasswordCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.plaintext-password");

    private static final String NO_OP_ENCODER = "org.springframework.security.crypto.password.NoOpPasswordEncoder";
    private static final String USER = "org.springframework.security.core.userdetails.User";

    private static final WhyItMatters WHY = new WhyItMatters(
            "Whoever reads the database, a backup or this source file gets every affected password in a usable form,"
                    + " and people reuse their passwords on other sites.",
            List.of(
                    SecurityReferences.cwe(256, "Plaintext storage of a password"),
                    SecurityReferences.springDocs(
                            "Spring Security: Password storage",
                            "spring-security/reference/features/authentication/password-storage.html")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Use PasswordEncoderFactories.createDelegatingPasswordEncoder() or another adaptive hash such as BCrypt or"
                    + " Argon2, and keep real passwords out of source code.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SECURITY;
    }

    @Override
    protected boolean inspectsTestSources() {
        return false;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Finding> findings = new ArrayList<>();
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            if (isCallOn(file, call, "getInstance", NO_OP_ENCODER)) {
                findings.add(report(file, call, "NoOpPasswordEncoder keeps passwords as plain text"));
            } else if (isCallOn(file, call, "withDefaultPasswordEncoder", USER)) {
                findings.add(report(file, call, "User.withDefaultPasswordEncoder() is meant only for demos"));
            }
        }
        for (StringLiteralExpr literal : file.unit().findAll(StringLiteralExpr.class)) {
            if (literal.getValue().startsWith("{noop}")) {
                findings.add(report(file, literal, "A password is stored as plain text with {noop}"));
            }
        }
        return findings;
    }

    private static boolean isCallOn(JavaFile file, MethodCallExpr call, String method, String type) {
        return call.getNameAsString().equals(method)
                && call.getScope()
                        .filter(scope -> file.refersTo(scope.toString(), type))
                        .isPresent();
    }

    private Finding report(JavaFile file, Node use, String whatsWrong) {
        return finding(file, use, Severity.MAJOR, Confidence.HIGH, whatsWrong, WHY, FIX);
    }
}
