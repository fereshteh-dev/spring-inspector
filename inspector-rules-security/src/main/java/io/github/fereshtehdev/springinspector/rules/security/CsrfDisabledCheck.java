package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
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
import java.util.List;
import java.util.regex.Pattern;

/**
 * CSRF protection switched off in a security configuration that keeps login sessions. Stateless APIs, configured
 * with {@code SessionCreationPolicy.STATELESS} or as an OAuth2 resource server, do not need CSRF tokens and are not
 * reported.
 */
public final class CsrfDisabledCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.csrf-disabled");

    private static final WhyItMatters WHY = new WhyItMatters(
            "With sessions or cookies, a browser sends the user's credentials with every request, including ones a"
                    + " malicious page triggers. CSRF tokens are what stops another site from changing data in the"
                    + " user's name.",
            List.of(
                    SecurityReferences.cwe(352, "Cross-Site Request Forgery"),
                    SecurityReferences.springDocs(
                            "Spring Security: CSRF", "spring-security/reference/servlet/exploits/csrf.html")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Remove the csrf disable call and keep Spring Security's default CSRF protection. If this is a token-based"
                    + " API without sessions, say so with sessionCreationPolicy(SessionCreationPolicy.STATELESS).");

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
        return file.unit().findAll(MethodCallExpr.class, CsrfDisabledCheck::disablesCsrf).stream()
                .filter(call -> !isStateless(scopeOf(call)))
                .filter(call -> !hasNothingToForge(scopeOf(call)))
                .map(call -> finding(
                        file,
                        call.getArguments().isEmpty() ? call : call.getArgument(0),
                        Severity.MAJOR,
                        usesFormLogin(scopeOf(call)) ? Confidence.HIGH : Confidence.MEDIUM,
                        "CSRF protection is disabled for a configuration that uses sessions",
                        WHY,
                        FIX))
                .toList();
    }

    /** {@code csrf(c -> c.disable())}, {@code csrf(AbstractHttpConfigurer::disable)} or {@code csrf().disable()}. */
    private static boolean disablesCsrf(MethodCallExpr call) {
        if (call.getNameAsString().equals("csrf") && call.getArguments().size() == 1) {
            Node argument = call.getArgument(0);
            boolean reference = argument instanceof MethodReferenceExpr method
                    && method.getIdentifier().equals("disable");
            boolean lambda = !argument.findAll(
                            MethodCallExpr.class,
                            inner -> inner.getNameAsString().equals("disable"))
                    .isEmpty();
            return reference || lambda;
        }
        return call.getNameAsString().equals("disable")
                && call.getScope()
                        .filter(scope -> scope instanceof MethodCallExpr inner
                                && inner.getNameAsString().equals("csrf")
                                && inner.getArguments().isEmpty())
                        .isPresent();
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Signs that a security chain authenticates users or restricts what they may do. */
    private static final List<String> AUTHENTICATION = List.of(
            "authenticated()",
            "hasRole(",
            "hasAnyRole(",
            "hasAuthority(",
            "hasAnyAuthority(",
            ".access(",
            "httpBasic",
            "formLogin",
            "oauth2Login",
            "saml2Login",
            "rememberMe",
            "x509",
            "oneTimeTokenLogin",
            "webAuthn");

    private static Node scopeOf(MethodCallExpr call) {
        return Nodes.ancestor(call, MethodDeclaration.class)
                .<Node>map(method -> method)
                .orElse(call.findRootNode());
    }

    private static boolean isStateless(Node scope) {
        String text = scope.toString();
        return text.contains("SessionCreationPolicy.STATELESS") || text.contains("oauth2ResourceServer");
    }

    /**
     * Every request is permitted and nothing logs users in: no request carries credentials an attacker could ride on,
     * so CSRF protection would protect nothing.
     */
    private static boolean hasNothingToForge(Node scope) {
        String text = WHITESPACE.matcher(scope.toString()).replaceAll("");
        return text.contains(".anyRequest().permitAll()")
                && AUTHENTICATION.stream().noneMatch(text::contains);
    }

    private static boolean usesFormLogin(Node scope) {
        String text = scope.toString();
        return text.contains("formLogin") || text.contains("oauth2Login");
    }
}
