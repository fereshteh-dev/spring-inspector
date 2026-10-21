package io.github.fereshtehdev.springinspector.javamodel;

import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.resolution.types.ResolvedType;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.javamodel.patch.LineIndex;
import io.github.fereshtehdev.springinspector.javamodel.patch.Patches;
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One parsed source file as a check sees it: the syntax tree, best-effort type resolution, and helpers to report
 * findings and build patches. Type queries return empty when the type cannot be resolved; checks then fall back to
 * syntax and imports and lower their confidence.
 */
public final class JavaFile {

    private final ParsedUnit parsed;
    private final Resolver resolver;
    private final DependencyRecorder recorder;

    JavaFile(ParsedUnit parsed, Resolver resolver, DependencyRecorder recorder) {
        this.parsed = parsed;
        this.resolver = resolver;
        this.recorder = recorder;
    }

    public Path path() {
        return parsed.source().path();
    }

    public SourceFile source() {
        return parsed.source();
    }

    public CompilationUnit unit() {
        return parsed.unit();
    }

    public String lineSeparator() {
        return parsed.lines().lineSeparator();
    }

    public LineRange lines(Node node) {
        return node.getRange()
                .map(range -> LineRange.of(range.begin.line, range.end.line))
                .orElse(LineRange.line(1));
    }

    public Location location(Node node) {
        return new Location(path(), lines(node));
    }

    public String snippet(Node node) {
        return source().snippet(lines(node));
    }

    /** The node exactly as written in the source. */
    public String text(Node node) {
        return source().content().substring(start(node), end(node));
    }

    /** Leading whitespace of the line the node starts on. */
    public String indentation(Node node) {
        LineIndex lines = parsed.lines();
        String line = lines.line(lines.lineOf(start(node)));
        int length = 0;
        while (length < line.length() && (line.charAt(length) == ' ' || line.charAt(length) == '\t')) {
            length++;
        }
        return line.substring(0, length);
    }

    /** Qualified name of the expression's type, or empty when it cannot be resolved. */
    public Optional<String> typeOf(Expression expression) {
        return resolver.resolve(recorder, () -> typeName(expression.calculateResolvedType()));
    }

    /** Qualified name of the type that declares the called method, or empty when it cannot be resolved. */
    public Optional<String> declaringTypeOf(MethodCallExpr call) {
        return resolver.resolve(recorder, () -> call.resolve().declaringType().getQualifiedName());
    }

    /**
     * Whether a type name as written in this file means {@code qualifiedName}, judged from the package and imports
     * alone, which works without the type's jar.
     */
    public boolean refersTo(String nameAsWritten, String qualifiedName) {
        if (nameAsWritten.equals(qualifiedName)) {
            return true;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        String packageName = lastDot < 0 ? "" : qualifiedName.substring(0, lastDot);
        if (!nameAsWritten.equals(qualifiedName.substring(lastDot + 1))) {
            return false;
        }
        for (ImportDeclaration declaration : unit().getImports()) {
            String imported = declaration.getNameAsString();
            if (!declaration.isStatic()
                    && (declaration.isAsterisk() ? imported.equals(packageName) : imported.equals(qualifiedName))) {
                return true;
            }
        }
        return unit().getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString().equals(packageName))
                .orElse(packageName.isEmpty());
    }

    public Optional<AnnotationExpr> annotation(NodeWithAnnotations<?> node, String qualifiedName) {
        return node.getAnnotations().stream()
                .filter(annotation -> refersTo(annotation.getNameAsString(), qualifiedName))
                .findFirst();
    }

    public boolean hasAnnotation(NodeWithAnnotations<?> node, String qualifiedName) {
        return annotation(node, qualifiedName).isPresent();
    }

    public TextEdit replace(Node node, String replacement) {
        return new TextEdit(start(node), end(node), replacement);
    }

    public TextEdit insertBefore(Node node, String text) {
        return TextEdit.insert(start(node), text);
    }

    /** Inserts text after the line break that ends the node's last line. */
    public TextEdit insertAfterLine(Node node, String text) {
        LineIndex lines = parsed.lines();
        return TextEdit.insert(lines.end(lines.lineOf(end(node) - 1)), text);
    }

    /** Deletes the node and the blanks after it, or its whole line when nothing else is on it. */
    public TextEdit delete(Node node) {
        String content = source().content();
        LineIndex lines = parsed.lines();
        int start = start(node);
        int end = end(node);
        while (end < content.length() && (content.charAt(end) == ' ' || content.charAt(end) == '\t')) {
            end++;
        }
        int lineStart = lines.start(lines.lineOf(start));
        boolean aloneOnLine = content.substring(lineStart, start).isBlank()
                && (end == content.length() || content.charAt(end) == '\n' || content.charAt(end) == '\r');
        if (aloneOnLine) {
            return TextEdit.delete(lineStart, end == content.length() ? end : lines.end(lines.lineOf(end)));
        }
        return TextEdit.delete(start, end);
    }

    public Patch patch(List<TextEdit> edits) {
        return Patches.unifiedDiff(source(), edits);
    }

    public int start(Node node) {
        return offset(node, true);
    }

    public int end(Node node) {
        return offset(node, false);
    }

    private int offset(Node node, boolean begin) {
        Position position = node.getRange()
                .map(range -> begin ? range.begin : range.end)
                .orElseThrow(() -> new IllegalArgumentException("Node has no position: " + node));
        int offset = parsed.lines().offset(position.line, position.column);
        return begin ? offset : Math.min(offset + 1, source().content().length());
    }

    private static String typeName(ResolvedType type) {
        return type.isReferenceType() ? type.asReferenceType().getQualifiedName() : type.describe();
    }
}
