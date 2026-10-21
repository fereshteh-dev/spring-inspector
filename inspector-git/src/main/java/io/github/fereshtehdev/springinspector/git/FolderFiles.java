package io.github.fereshtehdev.springinspector.git;

import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangedLines;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.port.CollectedChanges;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every file of a project that is not under git, for inspecting the whole project. Hidden folders and the usual build
 * output are skipped, links are not followed, and the number of files is bounded.
 */
public final class FolderFiles {

    static final int MAX_FILES = 100_000;
    private static final Set<String> SKIPPED =
            Set.of("target", "build", "out", "bin", "node_modules", "dist", "generated", "generated-sources");

    private FolderFiles() {}

    public static CollectedChanges all(Path projectRoot) {
        List<FileChange> files = new ArrayList<>();
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (directory.equals(projectRoot)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path name = directory.getFileName();
                    boolean skipped =
                            name == null || name.toString().startsWith(".") || SKIPPED.contains(name.toString());
                    return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && attributes.size() <= SourceLimits.MAX_SOURCE_BYTES) {
                        files.add(new FileChange(projectRoot.relativize(file), ChangedLines.entireFile()));
                    }
                    return files.size() >= MAX_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException unreadable) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list the files of " + projectRoot, e);
        }
        return new CollectedChanges(new ChangeSet(files), new WorkingTreeSources(projectRoot));
    }
}
