package io.github.fereshtehdev.springinspector.git;

import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangedLines;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.change.Hunk;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

final class DiffChanges {

    private DiffChanges() {}

    static ChangeSet between(
            Repository repository, ProjectPrefix prefix, AbstractTreeIterator before, AbstractTreeIterator after)
            throws IOException {
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDiffComparator(RawTextComparator.DEFAULT);
            formatter.setDetectRenames(true);
            formatter.setPathFilter(prefix.filter());
            List<FileChange> changes = new ArrayList<>();
            for (DiffEntry entry : formatter.scan(before, after)) {
                if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    continue;
                }
                prefix.toProjectPath(entry.getNewPath())
                        .flatMap(path -> toChange(formatter, entry, path))
                        .ifPresent(changes::add);
            }
            return new ChangeSet(changes);
        }
    }

    private static Optional<FileChange> toChange(DiffFormatter formatter, DiffEntry entry, Path path) {
        try {
            FileHeader header = formatter.toFileHeader(entry);
            if (header.getPatchType() != FileHeader.PatchType.UNIFIED) {
                return Optional.empty();
            }
            return switch (entry.getChangeType()) {
                case ADD, COPY -> Optional.of(new FileChange(path, ChangedLines.entireFile()));
                case MODIFY, RENAME -> hunks(header).map(hunks -> new FileChange(path, ChangedLines.hunks(hunks)));
                case DELETE -> Optional.empty();
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Could not diff " + path, e);
        }
    }

    private static Optional<List<Hunk>> hunks(FileHeader header) {
        List<Hunk> hunks = new ArrayList<>();
        for (Edit edit : header.toEditList()) {
            if (edit.getEndB() > edit.getBeginB()) {
                hunks.add(Hunk.lines(edit.getBeginB() + 1, edit.getEndB()));
            }
        }
        return hunks.isEmpty() ? Optional.empty() : Optional.of(hunks);
    }
}
