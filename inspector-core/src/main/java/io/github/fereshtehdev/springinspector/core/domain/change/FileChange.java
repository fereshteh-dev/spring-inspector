package io.github.fereshtehdev.springinspector.core.domain.change;

import io.github.fereshtehdev.springinspector.core.domain.ProjectPath;
import java.nio.file.Path;

public record FileChange(Path path, ChangedLines changedLines) {

    public FileChange {
        path = ProjectPath.requireRelative(path);
    }

    public static FileChange entireFile(String path) {
        return new FileChange(Path.of(path), ChangedLines.entireFile());
    }
}
