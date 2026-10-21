package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.Node;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;

/** Lines that hold code, leaving out blank lines, comments and the braces that open and close the node. */
final class CodeLines {

    private CodeLines() {}

    static int count(JavaFile file, Node block) {
        String[] lines = file.source().snippet(file.lines(block)).split("\\R", -1);
        int code = 0;
        boolean inBlockComment = false;
        for (int i = 1; i < lines.length - 1; i++) {
            String line = lines[i].strip();
            if (inBlockComment) {
                inBlockComment = !line.contains("*/");
                continue;
            }
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            if (line.startsWith("/*")) {
                inBlockComment = !line.contains("*/");
                continue;
            }
            code++;
        }
        return code;
    }
}
