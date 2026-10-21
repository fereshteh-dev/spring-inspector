package io.github.fereshtehdev.springinspector.intellij.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Picks the Java that runs the inspector, which needs Java 25 or newer: the one set in the plugin's settings, or the
 * IDE's own runtime when it is new enough, as in IntelliJ IDEA 2026.2.
 */
public final class JavaRuntimes {

    public static final int REQUIRED_FEATURE = 25;

    private JavaRuntimes() {}

    /**
     * The Java executable to use, or empty when none qualifies.
     *
     * @param configured the Java home or executable from the settings; blank when not set
     * @param ideHome the IDE's own {@code java.home}
     * @param ideFeature the IDE runtime's feature version, such as 21 or 25
     */
    public static Optional<Path> find(String configured, Path ideHome, int ideFeature, boolean windows) {
        if (!configured.isBlank()) {
            return executable(Path.of(configured.strip()), windows);
        }
        return ideFeature >= REQUIRED_FEATURE ? executable(ideHome, windows) : Optional.empty();
    }

    /** Accepts a Java home or the executable itself. */
    static Optional<Path> executable(Path path, boolean windows) {
        if (Files.isRegularFile(path)) {
            return Optional.of(path);
        }
        Path inHome = path.resolve("bin").resolve(windows ? "java.exe" : "java");
        return Files.isRegularFile(inHome) ? Optional.of(inHome) : Optional.empty();
    }
}
