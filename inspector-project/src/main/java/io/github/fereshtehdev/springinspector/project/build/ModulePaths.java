package io.github.fereshtehdev.springinspector.project.build;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

public final class ModulePaths {

    private ModulePaths() {}

    public static Optional<Path> insideProject(Path parent, String declared) {
        String trimmed = declared.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        try {
            Path declaredPath = Path.of(trimmed);
            if (declaredPath.isAbsolute() || declaredPath.getRoot() != null) {
                return Optional.empty();
            }
            Path resolved = parent.resolve(declaredPath).normalize();
            if (resolved.startsWith("..")) {
                return Optional.empty();
            }
            return Optional.of(resolved);
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
