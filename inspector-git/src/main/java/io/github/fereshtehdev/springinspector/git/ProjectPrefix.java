package io.github.fereshtehdev.springinspector.git;

import io.github.fereshtehdev.springinspector.core.domain.ProjectPath;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

record ProjectPrefix(String value) {

    static ProjectPrefix of(Path projectRootInWorkTree) {
        String prefix = projectRootInWorkTree.toString().replace('\\', '/');
        return new ProjectPrefix(prefix.isEmpty() ? "" : prefix + "/");
    }

    boolean isRoot() {
        return value.isEmpty();
    }

    String toRepoPath(Path projectRelative) {
        return value + ProjectPath.requireRelative(projectRelative).toString().replace('\\', '/');
    }

    Optional<Path> toProjectPath(String repoPath) {
        if (!repoPath.startsWith(value)) {
            return Optional.empty();
        }
        return Optional.of(Path.of(repoPath.substring(value.length())));
    }

    TreeFilter filter() {
        return isRoot() ? TreeFilter.ALL : PathFilter.create(value.substring(0, value.length() - 1));
    }
}
