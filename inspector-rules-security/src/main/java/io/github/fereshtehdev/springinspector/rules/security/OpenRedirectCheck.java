package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
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
import io.github.fereshtehdev.springinspector.javamodel.StringExpressions;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Redirects whose target comes from request input. A target that starts with a fixed local path or a fixed host stays
 * on that site, so it is not reported.
 */
public final class OpenRedirectCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.open-redirect");

    private static final String REDIRECT_PREFIX = "redirect:";
    private static final Pattern FIXED_TARGET =
            Pattern.compile("(?i)^(?:/[^/\\\\].*|[a-z][a-z0-9+.-]*://[^/?#{}]+[/?#].*)$", Pattern.DOTALL);

    private static final WhyItMatters WHY = new WhyItMatters(
            "A link to your site can then send people anywhere. Attackers use it in phishing, because the link looks"
                    + " trustworthy, and to steal login tokens that are handed over during a redirect.",
            List.of(
                    SecurityReferences.cwe(601, "Open redirect"),
                    SecurityReferences.owaspCheatSheet(
                            "Unvalidated Redirects and Forwards", "Unvalidated_Redirects_and_Forwards_Cheat_Sheet")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Redirect to a fixed path, or map the input to an allow-list of known targets. A value that only has to"
                    + " stay on this site must start with one '/' and must not start with '//' or '/\\'.");

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
            if (call.getNameAsString().equals("sendRedirect")
                    && call.getArguments().size() == 1) {
                report(file, call, call.getArgument(0), findings);
            }
        }
        for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
            if (file.refersTo(
                            creation.getType().getNameWithScope(), "org.springframework.web.servlet.view.RedirectView")
                    && !creation.getArguments().isEmpty()) {
                report(file, creation, creation.getArgument(0), findings);
            }
        }
        for (BinaryExpr concatenation : file.unit().findAll(BinaryExpr.class, OpenRedirectCheck::isRedirectView)) {
            report(file, concatenation, concatenation, findings);
        }
        return findings;
    }

    /** {@code "redirect:" + target}, taken as a whole and not for each of its parts. */
    private static boolean isRedirectView(BinaryExpr concatenation) {
        boolean outermost = concatenation
                .getParentNode()
                .filter(parent -> parent instanceof BinaryExpr)
                .isEmpty();
        return outermost && leadingLiteral(concatenation).startsWith(REDIRECT_PREFIX);
    }

    private void report(JavaFile file, Node use, Expression target, List<Finding> findings) {
        if (RequestInputs.isRequestInput(file, target) && !hasFixedTarget(target)) {
            findings.add(finding(
                    file,
                    use,
                    Severity.MAJOR,
                    Confidence.HIGH,
                    "The redirect target comes from the request",
                    WHY,
                    FIX));
        }
    }

    private static boolean hasFixedTarget(Expression target) {
        String literal = leadingLiteral(target);
        String path = literal.startsWith(REDIRECT_PREFIX) ? literal.substring(REDIRECT_PREFIX.length()) : literal;
        return FIXED_TARGET.matcher(path).matches();
    }

    private static String leadingLiteral(Expression expression) {
        Expression value = StringExpressions.followLocal(expression);
        while (value instanceof BinaryExpr concatenation) {
            value = StringExpressions.followLocal(concatenation.getLeft());
        }
        return value instanceof StringLiteralExpr literal ? literal.getValue() : "";
    }
}
