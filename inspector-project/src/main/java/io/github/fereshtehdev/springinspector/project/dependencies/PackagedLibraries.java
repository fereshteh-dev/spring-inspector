package io.github.fereshtehdev.springinspector.project.dependencies;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * The libraries inside application archives the project has already built ({@code target/*.jar}, {@code
 * build/libs/*.jar}, also {@code .war}): each nested library's {@code pom.properties} names it exactly, including
 * transitive dependencies that no build file mentions. The archives are read as data, never run, and bounded.
 */
public final class PackagedLibraries {

    static final int MAX_ARCHIVES = 8;
    static final int MAX_NESTED = 5_000;
    static final long MAX_NESTED_BYTES = 256L * 1024 * 1024;
    static final int MAX_PROPERTIES_BYTES = 64 * 1024;

    private static final List<Path> OUTPUT_DIRECTORIES = List.of(Path.of("target"), Path.of("build", "libs"));
    private static final Pattern POM_PROPERTIES = Pattern.compile("META-INF/maven/[^/]+/[^/]+/pom\\.properties");

    private PackagedLibraries() {}

    /**
     * A library found in an archive.
     *
     * @param module the project directory, relative to the root, whose build produced the archive
     * @param archive the archive, relative to the root
     */
    public record Library(String groupId, String artifactId, String version, Path module, Path archive) {

        public String coordinate() {
            return groupId + ":" + artifactId;
        }
    }

    /** Libraries in the archives below the root and each module, which are directories relative to the root. */
    public static List<Library> find(Path root, List<Path> modules) {
        List<Library> libraries = new ArrayList<>();
        int archives = 0;
        List<Path> directories = new ArrayList<>(List.of(Path.of("")));
        modules.stream().filter(module -> !directories.contains(module)).forEach(directories::add);
        for (Path module : directories) {
            for (Path output : OUTPUT_DIRECTORIES) {
                for (Path archive : archives(root, module.resolve(output))) {
                    if (archives++ >= MAX_ARCHIVES) {
                        return libraries;
                    }
                    libraries.addAll(read(root, module, archive));
                }
            }
        }
        return libraries;
    }

    private static List<Path> archives(Path root, Path directory) {
        Path absolute = root.resolve(directory);
        if (!Files.isDirectory(absolute)) {
            return List.of();
        }
        List<Path> archives = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(absolute, "*.{jar,war}")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                boolean application = !name.startsWith("original-")
                        && !name.endsWith("-plain.jar")
                        && !name.endsWith("-sources.jar")
                        && !name.endsWith("-javadoc.jar");
                if (application
                        && Files.isRegularFile(file)
                        && file.toRealPath().startsWith(root.toRealPath())) {
                    archives.add(directory.resolve(name));
                }
            }
        } catch (IOException unreadable) {
            return List.of();
        }
        archives.sort(null);
        return archives;
    }

    private static List<Library> read(Path root, Path module, Path archive) {
        List<Library> libraries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(root.resolve(archive).toFile())) {
            int nested = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements() && nested < MAX_NESTED) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if ((name.startsWith("BOOT-INF/lib/") || name.startsWith("WEB-INF/lib/")) && name.endsWith(".jar")) {
                    nested++;
                    String fileName = name.substring(name.lastIndexOf('/') + 1);
                    try (InputStream in = new Bounded(zip.getInputStream(entry))) {
                        describe(in, fileName)
                                .map(found -> new Library(
                                        found.groupId(), found.artifactId(), found.version(), module, archive))
                                .ifPresent(libraries::add);
                    }
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            return libraries;
        }
        return libraries;
    }

    /** The coordinates in a library's {@code pom.properties}, preferring the one that matches its file name. */
    private static Optional<Library> describe(InputStream jar, String fileName) throws IOException {
        Optional<Library> first = Optional.empty();
        ZipInputStream in = new ZipInputStream(jar);
        for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
            if (!POM_PROPERTIES.matcher(entry.getName()).matches()) {
                continue;
            }
            Optional<Library> library = properties(in.readNBytes(MAX_PROPERTIES_BYTES));
            if (library.isPresent() && fileName.startsWith(library.get().artifactId() + "-")) {
                return library;
            }
            first = first.or(() -> library);
        }
        return first;
    }

    private static Optional<Library> properties(byte[] bytes) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(new String(bytes, StandardCharsets.ISO_8859_1)));
        String group = properties.getProperty("groupId", "").strip();
        String artifact = properties.getProperty("artifactId", "").strip();
        String version = properties.getProperty("version", "").strip();
        if (group.isEmpty() || artifact.isEmpty() || version.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Library(group, artifact, version, Path.of(""), Path.of("")));
    }

    /** Stops reading one nested library after {@link #MAX_NESTED_BYTES}, against archives that expand without end. */
    private static final class Bounded extends FilterInputStream {

        private long read;

        Bounded(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            count(value < 0 ? 0 : 1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            count(Math.max(count, 0));
            return count;
        }

        private void count(int bytes) throws IOException {
            read += bytes;
            if (read > MAX_NESTED_BYTES) {
                throw new IOException("A library in the archive is larger than " + MAX_NESTED_BYTES + " bytes");
            }
        }
    }
}
