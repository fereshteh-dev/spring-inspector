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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Outgoing HTTP requests whose target URL comes from request input (server-side request forgery). A URL that starts
 * with a fixed scheme and host, with request values only in the path or query, is not reported.
 */
public final class SsrfCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.ssrf");

    private static final Set<String> CLIENT_METHODS = Set.of(
            "getForObject",
            "getForEntity",
            "postForObject",
            "postForEntity",
            "postForLocation",
            "patchForObject",
            "exchange",
            "execute",
            "uri",
            "newBuilder");
    private static final Set<String> URL_TYPES = Set.of("java.net.URL", "java.net.URI");
    private static final Pattern FIXED_HOST =
            Pattern.compile("(?i)^[a-z][a-z0-9+.-]*://[^/?#{}]+[/?#].*", Pattern.DOTALL);

    private static final WhyItMatters WHY = new WhyItMatters(
            "When the caller picks the URL, the server makes requests on their behalf from inside your network: to"
                    + " cloud metadata endpoints that hand out credentials, admin interfaces, or other internal"
                    + " services.",
            List.of(
                    SecurityReferences.cwe(918, "Server-side request forgery"),
                    SecurityReferences.owaspCheatSheet(
                            "Server Side Request Forgery Prevention",
                            "Server_Side_Request_Forgery_Prevention_Cheat_Sheet")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Keep the scheme and host fixed in code or configuration and put request values only into the path or"
                    + " query through URI templates. If the host must vary, check it against an allow-list.");

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
            boolean client = CLIENT_METHODS.contains(call.getNameAsString());
            boolean uriCreate = call.getNameAsString().equals("create")
                    && call.getScope()
                            .map(scope -> file.refersTo(scope.toString(), "java.net.URI"))
                            .orElse(false);
            if ((client || uriCreate) && !call.getArguments().isEmpty()) {
                check(file, call, call.getArgument(0)).ifPresent(findings::add);
            }
        }
        for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
            String type = creation.getType().getNameWithScope();
            if (URL_TYPES.stream().anyMatch(urlType -> file.refersTo(type, urlType))
                    && !creation.getArguments().isEmpty()) {
                check(file, creation, creation.getArgument(0)).ifPresent(findings::add);
            }
        }
        return findings;
    }

    private Optional<Finding> check(JavaFile file, Node use, Expression url) {
        if (!RequestInputs.isRequestInput(file, url) || hasFixedHost(url)) {
            return Optional.empty();
        }
        return Optional.of(finding(
                file,
                use,
                Severity.MAJOR,
                Confidence.HIGH,
                "An outgoing request goes to a URL taken from the request",
                WHY,
                FIX));
    }

    private static boolean hasFixedHost(Expression url) {
        Expression value = StringExpressions.followLocal(url);
        while (value instanceof BinaryExpr concatenation) {
            value = StringExpressions.followLocal(concatenation.getLeft());
        }
        if (value instanceof MethodCallExpr call
                && call.getNameAsString().equals("format")
                && !call.getArguments().isEmpty()) {
            value = call.getArgument(0);
        }
        return value instanceof StringLiteralExpr literal
                && FIXED_HOST.matcher(literal.getValue()).matches();
    }
}
