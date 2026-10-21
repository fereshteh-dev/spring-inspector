package io.github.fereshtehdev.springinspector.project.dependencies;

import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedVersions;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes projects, cached POMs and built archives for dependency tests. */
final class Files2 {

    private Files2() {}

    static Path write(Path root, String relative, String content) {
        try {
            Path file = root.resolve(relative);
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            Files.writeString(file, content);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A POM in Maven's repository layout below {@code repository}. */
    static void cachedPom(Path repository, String groupId, String artifactId, String version, String body) {
        write(
                repository,
                groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version
                        + ".pom",
                "<project><modelVersion>4.0.0</modelVersion><groupId>" + groupId + "</groupId><artifactId>" + artifactId
                        + "</artifactId><version>" + version + "</version>" + body + "</project>");
    }

    static String managed(String groupId, String artifactId, String version) {
        return "<dependency><groupId>" + groupId + "</groupId><artifactId>" + artifactId + "</artifactId><version>"
                + version + "</version></dependency>";
    }

    /** An application archive with one nested library per coordinate {@code group:artifact:version}. */
    static void bootJar(Path file, String libraryFolder, List<String> coordinates) {
        try {
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            try (OutputStream out = Files.newOutputStream(file);
                    ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                zip.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
                for (String coordinate : coordinates) {
                    String[] parts = coordinate.split(":", 3);
                    zip.putNextEntry(new ZipEntry(libraryFolder + parts[1] + "-" + parts[2] + ".jar"));
                    zip.write(library(parts[0], parts[1], parts[2]));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] library(String groupId, String artifactId, String version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("org/example/Library.class"));
            zip.write(new byte[] {(byte) 0xCA, (byte) 0xFE});
            zip.putNextEntry(new ZipEntry("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties"));
            zip.write(("groupId=" + groupId + "\nartifactId=" + artifactId + "\nversion=" + version + "\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        return bytes.toByteArray();
    }

    static ProjectContext project(Path root, BuildTool tool, List<Path> modules, Optional<String> springBoot) {
        return new ProjectContext(
                root,
                new DetectedVersions(Optional.empty(), springBoot.map(Version::parse), Optional.empty()),
                tool,
                modules,
                InspectorConfig.defaults());
    }

    static Map<String, String> noEnvironment() {
        return Map.of();
    }
}
