package io.github.fereshtehdev.springinspector.git;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

final class Repositories {

    static final String INDEX_FILE_VARIABLE = "GIT_INDEX_FILE";
    static final String CEILING_VARIABLE = "GIT_CEILING_DIRECTORIES";

    private Repositories() {}

    static OpenedRepository open(Path projectRoot, Map<String, String> environment) {
        return find(projectRoot, environment)
                .orElseThrow(() -> new IllegalArgumentException("Not inside a git repository: " + projectRoot));
    }

    /** The repository containing the project, or empty when the project is not inside one. */
    static Optional<OpenedRepository> find(Path projectRoot, Map<String, String> environment) {
        Path root = realPath(projectRoot);
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            ceilings(environment).forEach(builder::addCeilingDirectory);
            builder.findGitDir(root.toFile());
            if (builder.getGitDir() == null) {
                return Optional.empty();
            }
            builder.setMustExist(true).setup();
            if (builder.getWorkTree() == null) {
                throw new IllegalArgumentException("Bare repositories have no work tree: " + builder.getGitDir());
            }
            Path workTree = realPath(builder.getWorkTree().toPath());
            String indexFile = environment.get(INDEX_FILE_VARIABLE);
            if (indexFile != null && !indexFile.isBlank()) {
                builder.setIndexFile(workTree.resolve(indexFile).toFile());
            }
            Repository repository = builder.build();
            return Optional.of(new OpenedRepository(repository, ProjectPrefix.of(workTree.relativize(root))));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open the git repository for " + projectRoot, e);
        }
    }

    private static List<File> ceilings(Map<String, String> environment) {
        String ceilings = environment.getOrDefault(CEILING_VARIABLE, "");
        return Arrays.stream(ceilings.split(File.pathSeparator, -1))
                .filter(entry -> !entry.isBlank())
                .map(File::new)
                .toList();
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Project directory does not exist: " + path, e);
        }
    }

    record OpenedRepository(Repository repository, ProjectPrefix prefix) {}
}
