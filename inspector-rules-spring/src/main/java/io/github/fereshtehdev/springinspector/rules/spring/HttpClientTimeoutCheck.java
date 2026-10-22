package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * HTTP clients created without timeouts: the JDK {@code HttpClient}, and {@code RestClient} or {@code RestTemplate}
 * built without a request factory. Clients from Spring Boot's auto-configured builders are not reported, because they
 * take their timeouts from configuration.
 */
public final class HttpClientTimeoutCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.http-client-without-timeout");

    private static final String JDK_CLIENT = "java.net.http.HttpClient";
    private static final String REST_CLIENT = "org.springframework.web.client.RestClient";
    private static final String REST_TEMPLATE = "org.springframework.web.client.RestTemplate";

    private static final WhyItMatters WHY = new WhyItMatters(
            "Without timeouts, a request to a server that stops answering waits forever. Every request that reaches"
                    + " it holds a thread, so one slow dependency exhausts the thread pool and takes the whole"
                    + " application down with it.",
            List.of(SpringCode.springDocs(
                    "Spring Framework: REST clients", "spring-framework/reference/integration/rest-clients.html")));

    private static final String SPRING_FIX = "Give the client a request factory with timeouts, for example a"
            + " SimpleClientHttpRequestFactory with setConnectTimeout and setReadTimeout, or build it from the"
            + " RestClient.Builder that Spring Boot configures, and set the timeouts there.";

    private static final HowToFix JDK_FIX = HowToFix.withoutPatch(
            "Set connectTimeout on the client builder, and a timeout on each HttpRequest for the response.");

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
        List<Finding> findings = new ArrayList<>();
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            if (isStaticCallOn(file, call, JDK_CLIENT)) {
                if (name.equals("newHttpClient")) {
                    findings.add(report(
                            file,
                            call,
                            Confidence.HIGH,
                            "HttpClient.newHttpClient() has no connect timeout",
                            HowToFix.withPatch(
                                    "Build the client with a connect timeout, and set a timeout on each HttpRequest"
                                            + " for the response.",
                                    withConnectTimeout(file, call))));
                } else if (name.equals("newBuilder") && isBuiltWithout(call, "connectTimeout")) {
                    findings.add(report(
                            file, call, Confidence.HIGH, "HttpClient is built without a connect timeout", JDK_FIX));
                }
            } else if (isStaticCallOn(file, call, REST_CLIENT)) {
                boolean created = name.equals("create") && takesNoClient(file, call);
                boolean built = name.equals("builder")
                        && call.getArguments().isEmpty()
                        && isBuiltWithout(call, "requestFactory");
                if (created || built) {
                    findings.add(report(
                            file,
                            call,
                            Confidence.MEDIUM,
                            "RestClient is created without timeouts",
                            HowToFix.withoutPatch(SPRING_FIX)));
                }
            }
        }
        boolean factorySetLater = file.unit().toString().contains("setRequestFactory");
        for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
            if (!factorySetLater
                    && creation.getArguments().isEmpty()
                    && file.refersTo(creation.getType().getNameWithScope(), REST_TEMPLATE)) {
                findings.add(report(
                        file,
                        creation,
                        Confidence.HIGH,
                        "new RestTemplate() has no timeouts",
                        HowToFix.withoutPatch(SPRING_FIX)));
            }
        }
        return findings;
    }

    private static boolean isStaticCallOn(JavaFile file, MethodCallExpr call, String qualifiedType) {
        return call.getScope()
                .filter(scope -> scope.isNameExpr() || scope.isFieldAccessExpr())
                .filter(scope -> file.refersTo(scope.toString(), qualifiedType))
                .isPresent();
    }

    /** {@code RestClient.create()} or {@code create(baseUrl)}; {@code create(restTemplate)} keeps that template's settings. */
    private static boolean takesNoClient(JavaFile file, MethodCallExpr call) {
        if (call.getArguments().isEmpty()) {
            return true;
        }
        Expression argument = call.getArgument(0);
        return argument instanceof StringLiteralExpr
                || file.typeOf(argument).filter("java.lang.String"::equals).isPresent();
    }

    /**
     * Whether a builder chain started by {@code start} ends in {@code build()} without calling {@code setting}. A
     * builder kept in a variable and configured later is not judged.
     */
    private static boolean isBuiltWithout(MethodCallExpr start, String setting) {
        List<String> chain = new ArrayList<>();
        for (Optional<MethodCallExpr> outer = callOn(start); outer.isPresent(); outer = callOn(outer.get())) {
            chain.add(outer.get().getNameAsString());
        }
        return chain.contains("build") && !chain.contains(setting);
    }

    /** The call made on the result of {@code inner}, as {@code build} is on {@code newBuilder()} in a chain. */
    private static Optional<MethodCallExpr> callOn(Expression inner) {
        return inner.getParentNode()
                .filter(parent -> parent instanceof MethodCallExpr outer
                        && outer.getScope().filter(inner::equals).isPresent())
                .map(MethodCallExpr.class::cast);
    }

    private static Patch withConnectTimeout(JavaFile file, MethodCallExpr call) {
        String client = call.getScope().map(Node::toString).orElse("HttpClient");
        List<TextEdit> edits = new ArrayList<>();
        edits.add(file.replace(call, client + ".newBuilder().connectTimeout(Duration.ofSeconds(10)).build()"));
        file.addImport("java.time.Duration").ifPresent(edits::add);
        return file.patch(edits);
    }

    private Finding report(JavaFile file, Node node, Confidence confidence, String whatsWrong, HowToFix fix) {
        return finding(file, node, Severity.MAJOR, confidence, whatsWrong, WHY, fix);
    }
}
