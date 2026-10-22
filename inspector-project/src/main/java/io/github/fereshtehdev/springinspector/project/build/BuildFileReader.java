package io.github.fereshtehdev.springinspector.project.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BuildFileReader {

    public static final long MAX_BYTES = 1024 * 1024;

    private final Path root;
    private final List<String> warnings = new ArrayList<>();

    public BuildFileReader(Path root) {
        this.root = root;
    }

    public boolean exists(Path relative) {
        return Files.isRegularFile(root.resolve(relative));
    }

    public Optional<String> read(Path relative) {
        Path file = root.resolve(relative);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            if (!file.toRealPath().startsWith(root.toRealPath())) {
                warn(relative + " points outside the project and was skipped");
                return Optional.empty();
            }
            if (Files.size(file) > MAX_BYTES) {
                warn(relative + " is larger than " + MAX_BYTES / 1024 + " KiB and was skipped");
                return Optional.empty();
            }
            return Optional.of(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            warn(relative + " could not be read: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void warn(String warning) {
        warnings.add(warning);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
