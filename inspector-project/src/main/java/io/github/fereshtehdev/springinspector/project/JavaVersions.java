package io.github.fereshtehdev.springinspector.project;

import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaVersions {

    private static final Pattern FIRST_STANDALONE_NUMBER =
            Pattern.compile("(?:^|[^A-Za-z0-9.])(\\d{1,4}(?:\\.\\d{1,4}){0,2})");

    private JavaVersions() {}

    public static Optional<Version> parse(String text) {
        Matcher matcher = FIRST_STANDALONE_NUMBER.matcher(text.replace('_', '.'));
        if (!matcher.find()) {
            return Optional.empty();
        }
        Version version = Version.parse(matcher.group(1));
        if (version.major() == 1 && version.minor() > 0) {
            return Optional.of(Version.of(version.minor(), 0, 0));
        }
        return Optional.of(version);
    }
}
