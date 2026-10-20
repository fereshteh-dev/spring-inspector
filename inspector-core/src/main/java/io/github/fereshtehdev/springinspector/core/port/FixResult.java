package io.github.fereshtehdev.springinspector.core.port;

import java.nio.file.Path;
import java.util.List;

public sealed interface FixResult {

    record Applied(List<Path> changedFiles) implements FixResult {

        public Applied {
            changedFiles = List.copyOf(changedFiles);
        }
    }

    record Rejected(String reason) implements FixResult {}
}
