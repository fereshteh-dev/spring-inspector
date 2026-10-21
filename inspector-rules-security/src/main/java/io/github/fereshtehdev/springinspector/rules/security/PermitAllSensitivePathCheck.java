package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.expr.Expression;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code permitAll()} on administrative paths such as {@code /admin/**}, {@code /actuator/**} or
 * {@code /h2-console/**}. Public health and info endpoints are fine and not reported.
 */
public final class PermitAllSensitivePathCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.permit-all-sensitive");

    private static final Set<String> MATCHERS = Set.of("requestMatchers", "antMatchers", "mvcMatchers", "pathMatchers");
    private static final Pattern SENSITIVE = Pattern.compile(
            "/(?:api/)?(?:admin|internal|management|manage|actuator|h2-console|jolokia|console)(?:/.*)?");
    private static final Pattern PUBLIC_ACTUATOR = Pattern.compile("/actuator/(?:health|info)(?:/.*)?");

    private static final WhyItMatters WHY = new WhyItMatters(
            "permitAll() lets anyone reach these endpoints without logging in. Administrative and actuator endpoints"
                    + " expose configuration, environment variables, heap dumps or data-changing operations.",
            List.of(
                    SecurityReferences.cwe(306, "Missing authentication for critical function"),
                    SecurityReferences.springDocs(
                            "Spring Security: Authorize HttpServletRequests",
                            "spring-security/reference/servlet/authorization/authorize-http-requests.html")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Require authentication and a role for these paths, for example .hasRole(\"ADMIN\"), and keep permitAll()"
                    + " for genuinely public paths. Expose only /actuator/health and /actuator/info publicly.");

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
        return file
                .unit()
                .findAll(MethodCallExpr.class, call -> call.getNameAsString().equals("permitAll"))
                .stream()
                .flatMap(permitAll -> sensitivePath(file, permitAll).stream())
                .map(path -> finding(
                        file,
                        path.argument(),
                        Severity.MAJOR,
                        Confidence.HIGH,
                        "permitAll() opens " + path.description() + " to anyone",
                        WHY,
                        FIX))
                .toList();
    }

    /** The matcher argument a sensitive path is written in, where the finding is reported. */
    private record SensitivePath(Expression argument, String description) {}

    private static Optional<SensitivePath> sensitivePath(JavaFile file, MethodCallExpr permitAll) {
        Optional<MethodCallExpr> matcher = permitAll
                .getScope()
                .filter(Expression::isMethodCallExpr)
                .map(Expression::asMethodCallExpr)
                .filter(scope -> MATCHERS.contains(scope.getNameAsString()));
        if (matcher.isEmpty()) {
            return Optional.empty();
        }
        Optional<SensitivePath> everyEndpoint = matcher.get().getArguments().stream()
                .filter(argument -> isEveryActuatorEndpoint(file, argument))
                .map(argument -> new SensitivePath(argument, "every actuator endpoint"))
                .findFirst();
        if (everyEndpoint.isPresent()) {
            return everyEndpoint;
        }
        return matcher.get().getArguments().stream()
                .filter(StringLiteralExpr.class::isInstance)
                .map(argument -> new SensitivePath(argument, ((StringLiteralExpr) argument).getValue()))
                .filter(path -> SENSITIVE
                                .matcher(path.description().toLowerCase(Locale.ROOT))
                                .matches()
                        && !PUBLIC_ACTUATOR.matcher(path.description()).matches())
                .findFirst();
    }

    private static boolean isEveryActuatorEndpoint(JavaFile file, Expression argument) {
        return argument instanceof MethodCallExpr call
                && call.getNameAsString().equals("toAnyEndpoint")
                && call.getScope()
                        .map(scope -> file.refersTo(
                                scope.toString(),
                                "org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest"))
                        .orElse(false);
    }
}
