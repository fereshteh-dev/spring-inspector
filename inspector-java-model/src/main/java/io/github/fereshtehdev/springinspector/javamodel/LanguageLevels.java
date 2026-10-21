package io.github.fereshtehdev.springinspector.javamodel;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Picks the parser's language level from the project's Java version. */
final class LanguageLevels {

    static final int DEFAULT_FEATURE = 25;

    private static final Pattern RELEASE = Pattern.compile("JAVA_([0-9]+)");

    private LanguageLevels() {}

    static LanguageLevel forJava(Optional<Version> java) {
        var supported = Releases.ALL.floorEntry(feature(java));
        return supported != null
                ? supported.getValue()
                : Releases.ALL.firstEntry().getValue();
    }

    /** The Java feature release, such as 8 for "1.8" and 25 for "25.0.1". */
    static int feature(Optional<Version> java) {
        return java.map(version -> version.major() == 1 ? version.minor() : version.major())
                .orElse(DEFAULT_FEATURE);
    }

    private static NavigableMap<Integer, LanguageLevel> releases() {
        NavigableMap<Integer, LanguageLevel> releases = new TreeMap<>();
        for (LanguageLevel level : LanguageLevel.values()) {
            Matcher release = RELEASE.matcher(level.name());
            if (release.matches() && Integer.parseInt(release.group(1)) >= 8) {
                releases.put(Integer.parseInt(release.group(1)), level);
            }
        }
        return releases;
    }

    /** Holder, so the expensive enum loads only when a level is actually needed. */
    private static final class Releases {

        static final NavigableMap<Integer, LanguageLevel> ALL = releases();

        private Releases() {}
    }
}
