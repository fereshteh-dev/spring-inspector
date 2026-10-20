package io.github.fereshtehdev.springinspector.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

public final class Fixtures {

    private static final String SUFFIX = ".fixture";

    private Fixtures() {}

    public static Path copyProject(String name, Path target) {
        Path source = resource("/projects/" + name);
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = source.relativize(file).toString();
                Path destination = target.resolve(relative.substring(0, relative.length() - SUFFIX.length()));
                Files.createDirectories(Objects.requireNonNull(destination.getParent()));
                Files.copy(file, destination);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target;
    }

    private static Path resource(String name) {
        URL url = Objects.requireNonNull(Fixtures.class.getResource(name), name);
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
