package io.github.fereshtehdev.springinspector.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Optional;

/** The shell command that starts this exact installation, for the git hook to run. */
final class Launcher {

    static final String CLASS_DATA_ARCHIVE = "spring-inspector.jsa";

    private Launcher() {}

    /** Empty when not running from a jar, for example from an IDE, where no stable command exists. */
    static Optional<String> current(InspectorHome home) {
        CodeSource source = Launcher.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            return Optional.empty();
        }
        try {
            Path jar = Path.of(source.getLocation().toURI());
            if (!Files.isRegularFile(jar)) {
                return Optional.empty();
            }
            Files.createDirectories(home.directory());
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            return Optional.of(
                    command(java, jar.toAbsolutePath(), home.directory().resolve(CLASS_DATA_ARCHIVE)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + home.directory(), e);
        } catch (URISyntaxException | RuntimeException notAJar) {
            return Optional.empty();
        }
    }

    /**
     * {@code java -jar} for {@code sh}, which git runs hooks with on every platform, Windows included. The JVM keeps a
     * class-data archive of the tool next to the cache key, creating and refreshing it by itself: that cuts the start
     * up of every commit by about a fifth. Its log lines are switched off so the hook prints only the report.
     */
    static String command(Path java, Path jar, Path classDataArchive) {
        return quote(java) + " -XX:SharedArchiveFile=" + quote(classDataArchive)
                + " -XX:+AutoCreateSharedArchive -Xlog:cds*=off -Xlog:aot*=off -jar " + quote(jar);
    }

    private static String quote(Path path) {
        return "'" + path.toString().replace('\\', '/').replace("'", "'\\''") + "'";
    }
}
