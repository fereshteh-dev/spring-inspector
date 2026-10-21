package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
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
 * Deserialization that lets the input choose which classes to create: Java's {@code ObjectInputStream} without a
 * filter, Jackson default typing or class-name type ids, and XStream without a type allow-list.
 */
public final class UnsafeDeserializationCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.unsafe-deserialization");

    private static final Set<String> DEFAULT_TYPING = Set.of("activateDefaultTyping", "enableDefaultTyping");
    private static final String JSON_TYPE_INFO = "com.fasterxml.jackson.annotation.JsonTypeInfo";

    private static final WhyItMatters WHY = new WhyItMatters(
            "When the data decides which classes are instantiated, a crafted payload can chain classes on the class"
                    + " path into running arbitrary code (deserialization gadgets), with no bug in your own code needed.",
            List.of(
                    SecurityReferences.cwe(502, "Deserialization of untrusted data"),
                    SecurityReferences.owaspCheatSheet("Deserialization", "Deserialization_Cheat_Sheet")));

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
        List<Finding> findings = new ArrayList<>();
        for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
            String type = creation.getType().getNameWithScope();
            if (file.refersTo(type, "java.io.ObjectInputStream") && !text.contains("setObjectInputFilter")) {
                findings.add(
                        report(
                                file,
                                creation,
                                Confidence.MEDIUM,
                                "ObjectInputStream reads objects without a deserialization filter",
                                "Prefer a data format such as JSON. If Java serialization is unavoidable, call"
                                        + " setObjectInputFilter with an allow-list of the expected classes before readObject."));
            } else if (file.refersTo(type, "com.thoughtworks.xstream.XStream")
                    && !text.contains("allowTypes")
                    && !text.contains("addPermission")) {
                findings.add(report(
                        file,
                        creation,
                        Confidence.MEDIUM,
                        "XStream is used without an allow-list of types",
                        "Call xstream.allowTypes(...) or addPermission(...) with exactly the classes you expect."));
            }
        }
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            if (DEFAULT_TYPING.contains(call.getNameAsString())) {
                findings.add(report(
                        file,
                        call,
                        Confidence.HIGH,
                        "Jackson default typing lets JSON choose the classes to create",
                        "Remove default typing. Model polymorphism with @JsonTypeInfo(use = Id.NAME) and"
                                + " @JsonSubTypes listing the allowed subtypes."));
            }
        }
        for (AnnotationExpr annotation : file.unit().findAll(AnnotationExpr.class)) {
            String written = annotation.toString();
            if (file.refersTo(annotation.getNameAsString(), JSON_TYPE_INFO)
                    && (written.contains("Id.CLASS") || written.contains("Id.MINIMAL_CLASS"))) {
                findings.add(report(
                        file,
                        annotation,
                        Confidence.HIGH,
                        "@JsonTypeInfo uses class names from the JSON as type ids",
                        "Use JsonTypeInfo.Id.NAME with @JsonSubTypes, so only the listed subtypes can be created."));
            }
        }
        return findings;
    }

    private Finding report(JavaFile file, Node node, Confidence confidence, String what, String fix) {
        return finding(file, node, Severity.MAJOR, confidence, what, WHY, HowToFix.withoutPatch(fix));
    }
}
