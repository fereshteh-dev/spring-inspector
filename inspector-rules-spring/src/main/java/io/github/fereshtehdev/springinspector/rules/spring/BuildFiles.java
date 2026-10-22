package io.github.fereshtehdev.springinspector.rules.spring;

import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The build file of a change, for rules about the project's versions: they report once, on the build file, and only
 * when it is part of the change, so a version decision is reviewed when it is made.
 */
final class BuildFiles {

    private static final Set<String> NAMES = Set.of("pom.xml", "build.gradle", "build.gradle.kts");

    private BuildFiles() {}

    /** The first changed build file that mentions any of {@code hints}. */
    static Optional<SourceFile> changed(InspectionInput input, List<String> hints) {
        return input.changeSet().files().stream()
                .map(FileChange::path)
                .filter(BuildFiles::isBuildFile)
                .flatMap(path -> input.sources().read(path).stream())
                .filter(source -> hints.stream().anyMatch(source.content()::contains))
                .findFirst();
    }

    /** The line of the first hint found, else line 1. */
    static LineRange lineOf(SourceFile build, List<String> hints) {
        for (String hint : hints) {
            int index = build.content().indexOf(hint);
            if (index >= 0) {
                return LineRange.line((int) build.content()
                                .substring(0, index)
                                .chars()
                                .filter(c -> c == '\n')
                                .count()
                        + 1);
            }
        }
        return LineRange.line(1);
    }

    private static boolean isBuildFile(Path path) {
        Path name = path.getFileName();
        return name != null && NAMES.contains(name.toString());
    }
}
