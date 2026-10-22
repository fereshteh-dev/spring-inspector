package io.github.fereshtehdev.springinspector.project.architecture;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Maps every Java source file of a project for the architecture rules, reading only text: hidden folders, build
 * output and test sources are skipped, links are not followed, and file size and count are bounded.
 */
public final class JavaImportScanner {

    static final int MAX_FILES = 50_000;
    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> SKIPPED = Set.of("target", "build", "out", "bin", "node_modules", "generated");

    private JavaImportScanner() {}

    public static ImportGraph scan(Path projectRoot) {
        List<JavaUnit> units = new ArrayList<>();
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (directory.equals(projectRoot)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path name = directory.getFileName();
                    String folder = name == null ? "" : name.toString();
                    boolean testSources = folder.equals("test") && isSourceRoot(directory);
                    return folder.startsWith(".") || SKIPPED.contains(folder) || testSources
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String name =
                            file.getFileName() == null ? "" : file.getFileName().toString();
                    if (attributes.isRegularFile()
                            && name.endsWith(".java")
                            && !name.equals("module-info.java")
                            && attributes.size() <= MAX_FILE_BYTES) {
                        read(projectRoot, file).ifPresent(units::add);
                    }
                    return units.size() >= MAX_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException unreadable) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException unreadable) {
            return new ImportGraph(units);
        }
        return new ImportGraph(units);
    }

    /** {@code src/test}: the tests of a module, whose imports say nothing about the application's architecture. */
    private static boolean isSourceRoot(Path directory) {
        Path parent = directory.getParent();
        return parent != null
                && parent.getFileName() != null
                && parent.getFileName().toString().equals("src");
    }

    private static Optional<JavaUnit> read(Path projectRoot, Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            return Optional.of(JavaUnit.parse(projectRoot.relativize(file), text));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
