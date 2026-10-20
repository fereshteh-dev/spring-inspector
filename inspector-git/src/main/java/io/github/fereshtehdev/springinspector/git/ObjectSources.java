package io.github.fereshtehdev.springinspector.git;

import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.port.SourceProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

final class ObjectSources implements SourceProvider {

    @FunctionalInterface
    interface BlobLookup {
        Optional<ObjectId> blobAt(String repoPath) throws IOException;
    }

    private final Repository repository;
    private final ProjectPrefix prefix;
    private final BlobLookup lookup;

    private ObjectSources(Repository repository, ProjectPrefix prefix, BlobLookup lookup) {
        this.repository = repository;
        this.prefix = prefix;
        this.lookup = lookup;
    }

    static ObjectSources fromIndex(Repository repository, ProjectPrefix prefix, DirCache index) {
        return new ObjectSources(repository, prefix, path -> {
            DirCacheEntry entry = index.getEntry(path);
            return entry != null && FileModes.isRegularFile(entry.getFileMode())
                    ? Optional.of(entry.getObjectId())
                    : Optional.empty();
        });
    }

    static ObjectSources fromTree(Repository repository, ProjectPrefix prefix, ObjectId tree) {
        return new ObjectSources(repository, prefix, path -> {
            try (TreeWalk walk = TreeWalk.forPath(repository, path, tree)) {
                return walk != null && FileModes.isRegularFile(walk.getFileMode(0))
                        ? Optional.of(walk.getObjectId(0))
                        : Optional.empty();
            }
        });
    }

    @Override
    public Optional<SourceFile> read(Path projectRelativePath) {
        try {
            Optional<ObjectId> blob = lookup.blobAt(prefix.toRepoPath(projectRelativePath));
            if (blob.isEmpty()) {
                return Optional.empty();
            }
            byte[] bytes =
                    repository.open(blob.get(), Constants.OBJ_BLOB).getCachedBytes(SourceLimits.MAX_SOURCE_BYTES);
            return Optional.of(new SourceFile(projectRelativePath, new String(bytes, StandardCharsets.UTF_8)));
        } catch (LargeObjectException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + projectRelativePath + " from git", e);
        }
    }
}
