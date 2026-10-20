package io.github.fereshtehdev.springinspector.core.domain;

import java.nio.file.Path;

public record Location(Path file, LineRange lines) {

    public Location {
        file = ProjectPath.requireRelative(file);
    }

    public static Location of(String file, LineRange lines) {
        return new Location(Path.of(file), lines);
    }

    @Override
    public String toString() {
        return file.toString().replace('\\', '/') + ":" + lines;
    }
}
