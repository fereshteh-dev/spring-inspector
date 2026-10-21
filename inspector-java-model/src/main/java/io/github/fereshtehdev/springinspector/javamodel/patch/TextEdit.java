package io.github.fereshtehdev.springinspector.javamodel.patch;

/** Replaces the characters in {@code [start, end)} with {@code replacement}. */
public record TextEdit(int start, int end, String replacement) {

    public TextEdit {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid edit range [" + start + ", " + end + ")");
        }
    }

    public static TextEdit insert(int offset, String text) {
        return new TextEdit(offset, offset, text);
    }

    public static TextEdit delete(int start, int end) {
        return new TextEdit(start, end, "");
    }
}
