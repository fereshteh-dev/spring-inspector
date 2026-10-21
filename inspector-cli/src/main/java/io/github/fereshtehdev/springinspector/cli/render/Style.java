package io.github.fereshtehdev.springinspector.cli.render;

import java.util.Map;

/**
 * How rich terminal output may be. Colour only on a real terminal and never with {@code NO_COLOR}; emoji and other
 * non-ASCII symbols only when the output is UTF-8, since Windows consoles often are not.
 */
public record Style(boolean color, boolean unicode) {

    public static final Style PLAIN = new Style(false, false);

    public static Style detect(Map<String, String> environment, boolean terminal, String encoding) {
        boolean noColor = environment.containsKey("NO_COLOR") || "dumb".equals(environment.get("TERM"));
        return new Style(terminal && !noColor, encoding.equalsIgnoreCase("UTF-8"));
    }

    String paint(String text, Ansi... codes) {
        if (!color || codes.length == 0) {
            return text;
        }
        StringBuilder painted = new StringBuilder();
        for (Ansi code : codes) {
            painted.append(code.sequence);
        }
        return painted.append(text).append(Ansi.RESET.sequence).toString();
    }

    /** {@code fancy} when the output can show it, otherwise the ASCII {@code plain}. */
    public String symbol(String fancy, String plain) {
        return unicode ? fancy : plain;
    }

    enum Ansi {
        RESET("\u001B[0m"),
        BOLD("\u001B[1m"),
        DIM("\u001B[2m"),
        RED("\u001B[31m"),
        GREEN("\u001B[32m"),
        YELLOW("\u001B[33m"),
        BLUE("\u001B[34m"),
        CYAN("\u001B[36m");

        private final String sequence;

        Ansi(String sequence) {
            this.sequence = sequence;
        }
    }
}
