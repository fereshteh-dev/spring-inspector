package io.github.fereshtehdev.springinspector.core.domain.project;

import java.nio.file.Path;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A glob over project-relative paths, written with {@code /} on every platform: {@code *} matches within one
 * directory, {@code **} across directories, and {@code **}{@code /} also matches no directory at all, so
 * {@code **}{@code /generated/**} covers {@code generated/A.java} as well as {@code src/generated/A.java}.
 */
public final class PathPattern {

    private final String glob;
    private final Pattern regex;

    private PathPattern(String glob, Pattern regex) {
        this.glob = glob;
        this.regex = regex;
    }

    public static PathPattern of(String glob) {
        String trimmed = glob.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("An ignore pattern cannot be empty");
        }
        return new PathPattern(trimmed, Pattern.compile(toRegex(trimmed)));
    }

    public boolean matches(Path projectRelativePath) {
        return regex.matcher(projectRelativePath.toString().replace('\\', '/')).matches();
    }

    public String glob() {
        return glob;
    }

    private static String toRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && glob.startsWith("**/", i)) {
                regex.append("(?:.*/)?");
                i += 2;
            } else if (c == '*' && glob.startsWith("**", i)) {
                regex.append(".*");
                i++;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return regex.toString();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof PathPattern pattern && pattern.glob.equals(glob);
    }

    @Override
    public int hashCode() {
        return glob.hashCode();
    }

    @Override
    public String toString() {
        return glob;
    }
}
