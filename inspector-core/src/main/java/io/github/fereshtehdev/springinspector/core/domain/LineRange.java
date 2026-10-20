package io.github.fereshtehdev.springinspector.core.domain;

public record LineRange(int start, int end) implements Comparable<LineRange> {

    public LineRange {
        if (start < 1) {
            throw new IllegalArgumentException("Lines are 1-based, got start " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("End line " + end + " is before start line " + start);
        }
    }

    public static LineRange of(int start, int end) {
        return new LineRange(start, end);
    }

    public static LineRange line(int line) {
        return new LineRange(line, line);
    }

    public boolean overlaps(LineRange other) {
        return start <= other.end && other.start <= end;
    }

    @Override
    public int compareTo(LineRange other) {
        int byStart = Integer.compare(start, other.start);
        return byStart != 0 ? byStart : Integer.compare(end, other.end);
    }

    @Override
    public String toString() {
        return start == end ? String.valueOf(start) : start + "-" + end;
    }
}
