package io.github.fereshtehdev.springinspector.git;

import io.github.fereshtehdev.springinspector.core.domain.change.ChangeScope;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangedLines;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.port.ChangeCollector;
import io.github.fereshtehdev.springinspector.core.port.CollectedChanges;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;

/** Sources returned by {@link #collect} stay readable until this workspace is closed. */
public final class GitWorkspace implements ChangeCollector, AutoCloseable {

    private final Repository repository;
    private final ProjectPrefix prefix;
    private final Path projectRoot;

    private GitWorkspace(Repository repository, ProjectPrefix prefix, Path projectRoot) {
        this.repository = repository;
        this.prefix = prefix;
        this.projectRoot = projectRoot;
    }

    public static GitWorkspace open(Path projectRoot) {
        return open(projectRoot, System.getenv());
    }

    public static GitWorkspace open(Path projectRoot, Map<String, String> environment) {
        Repositories.OpenedRepository opened = Repositories.open(projectRoot, environment);
        return new GitWorkspace(opened.repository(), opened.prefix(), projectRoot);
    }

    /** The workspace of the repository containing the project, or empty when the project is not under git. */
    public static Optional<GitWorkspace> find(Path projectRoot, Map<String, String> environment) {
        return Repositories.find(projectRoot, environment)
                .map(opened -> new GitWorkspace(opened.repository(), opened.prefix(), projectRoot));
    }

    @Override
    public CollectedChanges collect(ChangeScope scope) {
        try {
            return switch (scope) {
                case ChangeScope.Staged staged -> staged();
                case ChangeScope.AgainstBase(String baseRef) -> againstBase(baseRef);
                case ChangeScope.All all -> all();
                case ChangeScope.Files(List<Path> paths) -> FolderFiles.only(projectRoot, paths);
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read changes from git", e);
        }
    }

    private CollectedChanges staged() throws IOException {
        DirCache index = repository.readDirCache();
        ObjectId head = repository.resolve("HEAD^{tree}");
        try (ObjectReader reader = repository.newObjectReader()) {
            AbstractTreeIterator before =
                    head == null ? new EmptyTreeIterator() : new CanonicalTreeParser(null, reader, head);
            ChangeSet changes = DiffChanges.between(repository, prefix, before, new DirCacheIterator(index));
            return new CollectedChanges(changes, ObjectSources.fromIndex(repository, prefix, index));
        }
    }

    private CollectedChanges againstBase(String baseRef) throws IOException {
        ObjectId base = repository.resolve(baseRef + "^{commit}");
        if (base == null) {
            throw new IllegalArgumentException("Unknown base ref: " + baseRef);
        }
        ObjectId head = repository.resolve("HEAD^{commit}");
        if (head == null) {
            throw new IllegalArgumentException("HEAD has no commits to compare with " + baseRef);
        }
        try (RevWalk walk = new RevWalk(repository);
                ObjectReader reader = repository.newObjectReader()) {
            RevCommit headCommit = walk.parseCommit(head);
            ObjectId headTree = headCommit.getTree().copy();
            ObjectId mergeBaseTree = mergeBase(walk, headCommit, walk.parseCommit(base), baseRef);
            ChangeSet changes = DiffChanges.between(
                    repository,
                    prefix,
                    new CanonicalTreeParser(null, reader, mergeBaseTree),
                    new CanonicalTreeParser(null, reader, headTree));
            return new CollectedChanges(changes, ObjectSources.fromTree(repository, prefix, headTree));
        }
    }

    private static ObjectId mergeBase(RevWalk walk, RevCommit head, RevCommit base, String baseRef) throws IOException {
        walk.setRevFilter(RevFilter.MERGE_BASE);
        walk.markStart(head);
        walk.markStart(base);
        RevCommit mergeBase = walk.next();
        if (mergeBase == null) {
            throw new IllegalArgumentException("HEAD and " + baseRef + " have no common ancestor");
        }
        walk.reset();
        walk.setRevFilter(RevFilter.ALL);
        return walk.parseCommit(mergeBase).getTree().copy();
    }

    private CollectedChanges all() throws IOException {
        List<FileChange> files = new ArrayList<>();
        try (TreeWalk walk = new TreeWalk(repository)) {
            walk.addTree(new FileTreeIterator(repository));
            walk.addTree(new DirCacheIterator(repository.readDirCache()));
            walk.setFilter(prefix.filter());
            walk.setRecursive(false);
            while (walk.next()) {
                WorkingTreeIterator workingTree = walk.getTree(0, WorkingTreeIterator.class);
                boolean tracked = walk.getTree(1, DirCacheIterator.class) != null;
                if (workingTree == null || (!tracked && workingTree.isEntryIgnored())) {
                    continue;
                }
                if (walk.isSubtree()) {
                    walk.enterSubtree();
                } else if (FileModes.isRegularFile(walk.getFileMode(0))) {
                    prefix.toProjectPath(walk.getPathString())
                            .map(path -> new FileChange(path, ChangedLines.entireFile()))
                            .ifPresent(files::add);
                }
            }
        }
        return new CollectedChanges(new ChangeSet(files), new WorkingTreeSources(projectRoot));
    }

    @Override
    public void close() {
        repository.close();
    }
}
