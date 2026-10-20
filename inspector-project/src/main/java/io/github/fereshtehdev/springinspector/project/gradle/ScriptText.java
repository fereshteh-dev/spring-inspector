package io.github.fereshtehdev.springinspector.project.gradle;

final class ScriptText {

    private ScriptText() {}

    static String withoutComments(String script) {
        StringBuilder out = new StringBuilder(script.length());
        int i = 0;
        while (i < script.length()) {
            char c = script.charAt(i);
            if (startsWith(script, i, "//")) {
                i = skipLineComment(script, i);
            } else if (startsWith(script, i, "/*")) {
                i = skipBlockComment(script, i, out);
            } else if (startsWith(script, i, "\"\"\"") || startsWith(script, i, "'''")) {
                i = copyString(script, i, script.substring(i, i + 3), out);
            } else if (c == '"' || c == '\'') {
                i = copyString(script, i, String.valueOf(c), out);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int skipLineComment(String script, int start) {
        int end = script.indexOf('\n', start);
        return end < 0 ? script.length() : end;
    }

    private static int skipBlockComment(String script, int start, StringBuilder out) {
        int end = script.indexOf("*/", start + 2);
        int stop = end < 0 ? script.length() : end + 2;
        script.substring(start, stop).chars().filter(ch -> ch == '\n').forEach(ch -> out.append('\n'));
        return stop;
    }

    private static int copyString(String script, int start, String quote, StringBuilder out) {
        out.append(quote);
        int i = start + quote.length();
        while (i < script.length()) {
            if (script.charAt(i) == '\\' && i + 1 < script.length()) {
                out.append(script, i, i + 2);
                i += 2;
            } else if (startsWith(script, i, quote)) {
                out.append(quote);
                return i + quote.length();
            } else {
                out.append(script.charAt(i));
                i++;
            }
        }
        return i;
    }

    private static boolean startsWith(String script, int index, String prefix) {
        return script.startsWith(prefix, index);
    }
}
