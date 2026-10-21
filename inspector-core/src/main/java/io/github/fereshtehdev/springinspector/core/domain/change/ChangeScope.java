package io.github.fereshtehdev.springinspector.core.domain.change;

import io.github.fereshtehdev.springinspector.core.domain.ProjectPath;
import java.nio.file.Path;
import java.util.List;

public sealed interface ChangeScope {

    static ChangeScope staged() {
        return Staged.INSTANCE;
    }

    static ChangeScope againstBase(String baseRef) {
        return new AgainstBase(baseRef);
    }

    static ChangeScope all() {
        return All.INSTANCE;
    }

    /** Whole files, named relative to the project root, such as the file open in an editor. */
    static ChangeScope files(List<Path> paths) {
        return new Files(paths);
    }

    enum Staged implements ChangeScope {
        INSTANCE
    }

    record AgainstBase(String baseRef) implements ChangeScope {

        public AgainstBase {
            if (baseRef.isBlank()) {
                throw new IllegalArgumentException("A base ref is required");
            }
        }
    }

    enum All implements ChangeScope {
        INSTANCE
    }

    record Files(List<Path> paths) implements ChangeScope {

        public Files {
            paths = List.copyOf(paths);
            if (paths.isEmpty()) {
                throw new IllegalArgumentException("Name at least one file");
            }
            for (Path path : paths) {
                ProjectPath.requireRelative(path);
            }
        }
    }
}
