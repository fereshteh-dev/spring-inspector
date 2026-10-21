package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.expr.Expression;
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
import java.util.Map;
import java.util.Optional;

/**
 * XML parser factories created without turning off DTDs or external entities anywhere in the file. A file that
 * hardens its parser with any of the usual settings is trusted to do so correctly.
 */
public final class XxeCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.xxe");

    private static final Map<String, String> FACTORIES = Map.of(
            "javax.xml.parsers.DocumentBuilderFactory", "newInstance",
            "javax.xml.parsers.SAXParserFactory", "newInstance",
            "javax.xml.stream.XMLInputFactory", "new",
            "javax.xml.transform.TransformerFactory", "newInstance",
            "javax.xml.validation.SchemaFactory", "newInstance");

    private static final List<String> HARDENING = List.of(
            "disallow-doctype-decl",
            "external-general-entities",
            "FEATURE_SECURE_PROCESSING",
            "ACCESS_EXTERNAL_DTD",
            "SUPPORT_DTD",
            "IS_SUPPORTING_EXTERNAL_ENTITIES",
            "load-external-dtd");

    private static final WhyItMatters WHY = new WhyItMatters(
            "By default these parsers resolve external entities, so an XML document can make the server read local"
                    + " files such as credentials, call internal URLs, or exhaust memory.",
            List.of(
                    SecurityReferences.cwe(611, "Improper restriction of XML external entity reference"),
                    SecurityReferences.owaspCheatSheet(
                            "XML External Entity Prevention", "XML_External_Entity_Prevention_Cheat_Sheet")));

    private static final HowToFix FIX = HowToFix.withoutPatch("Disable DTDs on the factory before parsing, for example"
            + " factory.setFeature(\"http://apache.org/xml/features/disallow-doctype-decl\", true), or for StAX"
            + " factory.setProperty(XMLInputFactory.SUPPORT_DTD, false).");

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
        String text = file.source().content();
        if (HARDENING.stream().anyMatch(text::contains)) {
            return List.of();
        }
        return file.unit().findAll(MethodCallExpr.class).stream()
                .flatMap(call -> factory(file, call).stream()
                        .map(factory -> finding(
                                file,
                                call,
                                Severity.MAJOR,
                                Confidence.MEDIUM,
                                factory + " is created without disabling external entities",
                                WHY,
                                FIX)))
                .toList();
    }

    private static Optional<String> factory(JavaFile file, MethodCallExpr call) {
        Optional<String> scope = call.getScope().map(Expression::toString);
        if (scope.isEmpty()) {
            return Optional.empty();
        }
        return FACTORIES.entrySet().stream()
                .filter(factory -> file.refersTo(scope.get(), factory.getKey()))
                .filter(factory -> call.getNameAsString().startsWith(factory.getValue()))
                .map(factory -> factory.getKey().substring(factory.getKey().lastIndexOf('.') + 1))
                .findFirst();
    }
}
