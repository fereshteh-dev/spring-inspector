package io.github.fereshtehdev.springinspector.javamodel;

import io.github.fereshtehdev.springinspector.core.port.Check;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Identifies the code of a check, so a cached result is never reused by a different build of that check. */
@FunctionalInterface
interface CheckFingerprints {

    /** Empty when the code cannot be identified, which turns caching off for that check. */
    Optional<String> of(Check check);

    /**
     * Fingerprints the jars holding the check and this model. Classes loaded from a directory, as in an IDE or a test
     * run, change without a new jar, so they are never cached.
     */
    static CheckFingerprints fromCodeSource() {
        Map<Class<?>, Optional<String>> jars = new ConcurrentHashMap<>();
        return check -> {
            Optional<String> model = jars.computeIfAbsent(JavaModel.class, CheckFingerprints::jar);
            Optional<String> rules = jars.computeIfAbsent(check.getClass(), CheckFingerprints::jar);
            return model.flatMap(modelJar -> rules.map(rulesJar -> check.id() + "@" + rulesJar + "+" + modelJar));
        };
    }

    private static Optional<String> jar(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null) {
            return Optional.empty();
        }
        try {
            return jar(Path.of(source.getLocation().toURI()));
        } catch (URISyntaxException | RuntimeException unidentifiable) {
            return Optional.empty();
        }
    }

    static Optional<String> jar(Path location) {
        try {
            if (!Files.isRegularFile(location)) {
                return Optional.empty();
            }
            return Optional.of(location.getFileName() + ":" + Files.size(location) + ":"
                    + Files.getLastModifiedTime(location).toMillis());
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }
}
