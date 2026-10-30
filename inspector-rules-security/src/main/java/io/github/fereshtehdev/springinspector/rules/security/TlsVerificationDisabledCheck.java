package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
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
import java.util.Set;

/**
 * TLS clients that accept any certificate or host name: empty trust managers, host name verifiers that always say yes,
 * and the libraries' ready-made insecure classes. Tests often do this on purpose, so test code is not inspected.
 */
public final class TlsVerificationDisabledCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.tls-verification-disabled");

    private static final Set<String> INSECURE_CLASSES = Set.of(
            "org.apache.hc.client5.http.ssl.NoopHostnameVerifier",
            "org.apache.http.conn.ssl.NoopHostnameVerifier",
            "io.netty.handler.ssl.util.InsecureTrustManagerFactory");
    private static final Set<String> VERIFIER_SETTERS =
            Set.of("setHostnameVerifier", "setDefaultHostnameVerifier", "hostnameVerifier");

    private static final WhyItMatters WHY = new WhyItMatters(
            "The connection is still encrypted, but to anyone. Someone on the network path can pose as the server and"
                    + " read or change everything sent, including passwords and tokens.",
            List.of(
                    SecurityReferences.cwe(295, "Improper certificate validation"),
                    SecurityReferences.owaspCheatSheet(
                            "Transport Layer Security", "Transport_Layer_Security_Cheat_Sheet")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Remove this and use the default certificate checks. For a private certificate authority, add it to a"
                    + " trust store and configure it with a Spring Boot SSL bundle.");

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
        file.unit().getImports().stream()
                .filter(declaration -> INSECURE_CLASSES.contains(declaration.getNameAsString()))
                .forEach(declaration -> findings.add(report(file, declaration, "An insecure TLS helper is used")));
        for (MethodDeclaration method : file.unit().findAll(MethodDeclaration.class)) {
            if (isEmptyTrustCheck(method)) {
                findings.add(report(file, method.getName(), "This trust manager accepts every server certificate"));
            } else if (isAlwaysTrueVerifier(method)) {
                findings.add(report(file, method.getName(), "This host name verifier accepts every host"));
            }
        }
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            if (VERIFIER_SETTERS.contains(call.getNameAsString())
                    && call.getArguments().size() == 1
                    && call.getArgument(0) instanceof LambdaExpr lambda
                    && lambda.getParameters().size() == 2
                    && alwaysReturnsTrue(lambda)) {
                findings.add(report(file, lambda, "This host name verifier accepts every host"));
            }
        }
        return findings;
    }

    private static boolean isEmptyTrustCheck(MethodDeclaration method) {
        return method.getNameAsString().equals("checkServerTrusted")
                && method.getBody()
                        .filter(body -> body.getStatements().isEmpty())
                        .isPresent();
    }

    private static boolean isAlwaysTrueVerifier(MethodDeclaration method) {
        return method.getNameAsString().equals("verify")
                && method.getParameters().size() == 2
                && method.getParameter(1).getType().asString().endsWith("SSLSession")
                && method.getBody()
                        .filter(TlsVerificationDisabledCheck::returnsOnlyTrue)
                        .isPresent();
    }

    private static boolean alwaysReturnsTrue(LambdaExpr lambda) {
        return switch (lambda.getBody()) {
            case ExpressionStmt statement -> isTrue(statement.getExpression());
            case BlockStmt block -> returnsOnlyTrue(block);
            default -> false;
        };
    }

    private static boolean returnsOnlyTrue(BlockStmt block) {
        return block.getStatements().size() == 1
                && block.getStatement(0) instanceof ReturnStmt statement
                && statement
                        .getExpression()
                        .filter(TlsVerificationDisabledCheck::isTrue)
                        .isPresent();
    }

    private static boolean isTrue(Node expression) {
        return expression instanceof BooleanLiteralExpr literal && literal.getValue();
    }

    private Finding report(JavaFile file, Node use, String whatsWrong) {
        return finding(file, use, Severity.MAJOR, Confidence.HIGH, whatsWrong, WHY, FIX);
    }
}
