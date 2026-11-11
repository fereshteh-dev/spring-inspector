package io.github.fereshtehdev.springinspector.document.design;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.javamodel.settings.SettingsFile;
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
 * Reads from disk what the system design is read from: the Java files the project map already lists, and the
 * Spring Boot settings files. Hidden folders, build output and tests are skipped, links are not followed, and file
 * size and count are bounded.
 */
public final class ProjectSources {

    static final long MAX_SETTINGS_BYTES = 1024 * 1024;
    static final int MAX_SETTINGS_FILES = 500;
    private static final Set<String> SKIPPED = Set.of("target", "build", "out", "bin", "node_modules", "generated");

    private ProjectSources() {}

    public static SystemDesign scan(Path projectRoot, ImportGraph graph) {
        return SystemDesignScanner.scan(graph, javaSources(projectRoot, graph), settingsFiles(projectRoot));
    }

    static List<SourceFile> javaSources(Path projectRoot, ImportGraph graph) {
        List<SourceFile> sources = new ArrayList<>();
        for (JavaUnit unit : graph.units()) {
            read(projectRoot, projectRoot.resolve(unit.file())).ifPresent(sources::add);
        }
        return sources;
    }

    static List<SourceFile> settingsFiles(Path projectRoot) {
        List<SourceFile> files = new ArrayList<>();
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (directory.equals(projectRoot)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String folder = name(directory);
                    boolean tests =
                            folder.equals("test") && name(directory.getParent()).equals("src");
                    return folder.startsWith(".") || SKIPPED.contains(folder) || tests
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile()
                            && attributes.size() <= MAX_SETTINGS_BYTES
                            && SettingsFile.isSettingsFile(projectRoot.relativize(file))) {
                        read(projectRoot, file).ifPresent(files::add);
                    }
                    return files.size() >= MAX_SETTINGS_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException unreadable) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException unreadable) {
            return files;
        }
        return files;
    }

    private static String name(@org.jspecify.annotations.Nullable Path path) {
        Path name = path == null ? null : path.getFileName();
        return name == null ? "" : name.toString();
    }

    private static Optional<SourceFile> read(Path projectRoot, Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            return Optional.of(new SourceFile(projectRoot.relativize(file), text));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
