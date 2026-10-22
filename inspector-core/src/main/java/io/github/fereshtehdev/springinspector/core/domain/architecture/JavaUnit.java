package io.github.fereshtehdev.springinspector.core.domain.architecture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One Java source file as the architecture rules see it: its package, its main type, the Spring stereotypes on that
 * type, and what it imports. Read from the text alone, so the whole project can be mapped quickly.
 *
 * @param file relative to the project root
 * @param stereotypes simple names of annotations such as {@code RestController}, {@code Service}, {@code Entity}
 */
public record JavaUnit(Path file, String packageName, String typeName, Set<String> stereotypes, List<Import> imports) {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+)(\\.\\*)?\\s*;", Pattern.MULTILINE);
    private static final Pattern ANNOTATION = Pattern.compile("@(\\w+)");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    public JavaUnit {
        stereotypes = Set.copyOf(stereotypes);
        imports = List.copyOf(imports);
    }

    public String qualifiedName() {
        return packageName.isEmpty() ? typeName : packageName + "." + typeName;
    }

    /**
     * An import statement.
     *
     * @param name the imported type, or the package for a wildcard import
     * @param line the 1-based line of the statement
     */
    public record Import(String name, boolean wildcard, int line) {}

    /** Reads a source file's package, imports and the annotations before its main type declaration. */
    public static JavaUnit parse(Path file, String text) {
        String code = withoutComments(text);
        Matcher packageMatcher = PACKAGE.matcher(code);
        boolean hasPackage = packageMatcher.find();
        String packageName = hasPackage ? packageMatcher.group(1) : "";
        List<Import> imports = new ArrayList<>();
        Matcher importMatcher = IMPORT.matcher(code);
        int lastImportEnd = hasPackage ? packageMatcher.end() : 0;
        while (importMatcher.find()) {
            if (importMatcher.group(1) == null) {
                imports.add(new Import(
                        importMatcher.group(2), importMatcher.group(3) != null, lineOf(code, importMatcher.start(2))));
            }
            lastImportEnd = importMatcher.end();
        }
        String typeName = typeName(file);
        Set<String> stereotypes = new LinkedHashSet<>();
        Matcher declaration = Pattern.compile(
                        "\\b(?:class|interface|record|enum)\\s+" + Pattern.quote(typeName) + "\\b")
                .matcher(code);
        int header = declaration.find(lastImportEnd) ? declaration.start() : code.length();
        Matcher annotation = ANNOTATION.matcher(code.substring(Math.min(lastImportEnd, header), header));
        while (annotation.find()) {
            stereotypes.add(annotation.group(1));
        }
        return new JavaUnit(file, packageName, typeName, stereotypes, imports);
    }

    /** The first import of {@code name}, also through a wildcard import of its package. */
    public Optional<Import> importOf(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        String packageOf = dot < 0 ? "" : qualifiedName.substring(0, dot);
        return imports.stream()
                .filter(candidate -> candidate.wildcard()
                        ? candidate.name().equals(packageOf)
                        : candidate.name().equals(qualifiedName))
                .findFirst();
    }

    private static String typeName(Path file) {
        Path name = file.getFileName();
        String fileName = name == null ? "" : name.toString();
        return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    /** Comments are blanked out but keep their line breaks, so line numbers stay right. */
    private static String withoutComments(String text) {
        StringBuilder result = new StringBuilder(text);
        for (Pattern comment : List.of(BLOCK_COMMENT, LINE_COMMENT)) {
            Matcher matcher = comment.matcher(result);
            List<int[]> ranges = new ArrayList<>();
            while (matcher.find()) {
                ranges.add(new int[] {matcher.start(), matcher.end()});
            }
            for (int[] range : ranges) {
                for (int i = range[0]; i < range[1]; i++) {
                    if (result.charAt(i) != '\n') {
                        result.setCharAt(i, ' ');
                    }
                }
            }
        }
        return result.toString();
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
