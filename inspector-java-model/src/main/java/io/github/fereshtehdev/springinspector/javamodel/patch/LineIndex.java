package io.github.fereshtehdev.springinspector.javamodel.patch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LineIndex {

    private final String text;
    private final int[] starts;

    private LineIndex(String text, int[] starts) {
        this.text = text;
        this.starts = starts;
    }

    public static LineIndex of(String text) {
        List<Integer> starts = new ArrayList<>();
        if (!text.isEmpty()) {
            starts.add(0);
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                i++;
            }
            if ((c == '\n' || c == '\r') && i + 1 < text.length()) {
                starts.add(i + 1);
            }
        }
        return new LineIndex(text, starts.stream().mapToInt(Integer::intValue).toArray());
    }

    public int lineCount() {
        return starts.length;
    }

    public int start(int line) {
        return line < starts.length ? starts[line] : text.length();
    }

    public int end(int line) {
        return line + 1 < starts.length ? starts[line + 1] : text.length();
    }

    public String line(int line) {
        return text.substring(start(line), end(line));
    }

    public List<String> lines() {
        List<String> lines = new ArrayList<>(starts.length);
        for (int line = 0; line < starts.length; line++) {
            lines.add(line(line));
        }
        return lines;
    }

    /** Zero-based line of an offset; the end of a text that ends with a line break is the line after the last. */
    public int lineOf(int offset) {
        if (offset < 0 || offset > text.length()) {
            throw new IllegalArgumentException("Offset " + offset + " is outside the text");
        }
        if (offset == text.length() && (starts.length == 0 || endsWithLineBreak())) {
            return starts.length;
        }
        int found = Arrays.binarySearch(starts, offset);
        return found >= 0 ? found : -found - 2;
    }

    /** Offset of a one-based line and column, as JavaParser reports positions. */
    public int offset(int line, int column) {
        if (line < 1 || line > starts.length || column < 1) {
            throw new IllegalArgumentException("No position " + line + ":" + column + " in the text");
        }
        return Math.min(starts[line - 1] + column - 1, text.length());
    }

    public String lineSeparator() {
        int lineFeed = text.indexOf('\n');
        if (lineFeed > 0 && text.charAt(lineFeed - 1) == '\r') {
            return "\r\n";
        }
        return lineFeed < 0 && text.indexOf('\r') >= 0 ? "\r" : "\n";
    }

    private boolean endsWithLineBreak() {
        char last = text.charAt(text.length() - 1);
        return last == '\n' || last == '\r';
    }
}
