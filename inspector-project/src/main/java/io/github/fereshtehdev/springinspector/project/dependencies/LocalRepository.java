package io.github.fereshtehdev.springinspector.project.dependencies;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads POMs, such as BOMs and parents, that Maven or Gradle already downloaded to this machine. It only reads, never
 * downloads, and accepts only coordinates that are plain names, as they come from untrusted build files.
 */
public final class LocalRepository {

    static final long MAX_POM_BYTES = 2L * 1024 * 1024;
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,199}");

    private final List<Path> mavenRoots;
    private final List<Path> gradleRoots;

    public LocalRepository(List<Path> mavenRoots, List<Path> gradleRoots) {
        this.mavenRoots = List.copyOf(mavenRoots);
        this.gradleRoots = List.copyOf(gradleRoots);
    }

    /** {@code ~/.m2/repository} and Gradle's module cache, honouring {@code GRADLE_USER_HOME}. */
    public static LocalRepository forUser(Path userHome, Map<String, String> environment) {
        String gradleHome = environment.getOrDefault("GRADLE_USER_HOME", "");
        Path gradle = gradleHome.isBlank() ? userHome.resolve(".gradle") : Path.of(gradleHome);
        return new LocalRepository(
                List.of(userHome.resolve(".m2").resolve("repository")),
                List.of(gradle.resolve("caches").resolve("modules-2").resolve("files-2.1")));
    }

    public static LocalRepository none() {
        return new LocalRepository(List.of(), List.of());
    }

    public Optional<String> pom(String groupId, String artifactId, String version) {
        if (!SAFE.matcher(groupId).matches()
                || !SAFE.matcher(artifactId).matches()
                || !SAFE.matcher(version).matches()) {
            return Optional.empty();
        }
        String file = artifactId + "-" + version + ".pom";
        for (Path root : mavenRoots) {
            Path pom = root.resolve(groupId.replace('.', '/'));
            Optional<String> text =
                    read(pom.resolve(artifactId).resolve(version).resolve(file));
            if (text.isPresent()) {
                return text;
            }
        }
        for (Path root : gradleRoots) {
            Path versionDirectory = root.resolve(groupId).resolve(artifactId).resolve(version);
            if (!Files.isDirectory(versionDirectory)) {
                continue;
            }
            try (DirectoryStream<Path> hashes = Files.newDirectoryStream(versionDirectory)) {
                for (Path hash : hashes) {
                    Optional<String> text = read(hash.resolve(file));
                    if (text.isPresent()) {
                        return text;
                    }
                }
            } catch (IOException unreadable) {
                // Try the next cache.
            }
        }
        return Optional.empty();
    }

    private static Optional<String> read(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_POM_BYTES) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
