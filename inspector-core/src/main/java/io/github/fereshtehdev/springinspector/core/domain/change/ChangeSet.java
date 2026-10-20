package io.github.fereshtehdev.springinspector.core.domain.change;

import io.github.fereshtehdev.springinspector.core.domain.Location;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ChangeSet(List<FileChange> files) {

    public ChangeSet {
        files = List.copyOf(files);
        Set<Path> seen = new HashSet<>();
        for (FileChange file : files) {
            if (!seen.add(file.path())) {
                throw new IllegalArgumentException("File listed twice in change set: " + file.path());
            }
        }
    }

    public static ChangeSet of(FileChange... files) {
        return new ChangeSet(List.of(files));
    }

    public Optional<FileChange> find(Path path) {
        Path normalized = path.normalize();
        return files.stream().filter(file -> file.path().equals(normalized)).findFirst();
    }

    public boolean touches(Location location) {
        return find(location.file())
                .map(file -> file.changedLines().touches(location.lines()))
                .orElse(false);
    }
}
