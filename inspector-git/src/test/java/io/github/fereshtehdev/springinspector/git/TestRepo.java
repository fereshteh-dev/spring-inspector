package io.github.fereshtehdev.springinspector.git;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;

final class TestRepo implements AutoCloseable {

    @FunctionalInterface
    private interface GitAction {
        void run() throws GitAPIException;
    }

    private static final PersonIdent AUTHOR = new PersonIdent("Test Author", "author@example.org");

    private final Path root;
    private final Git git;

    private TestRepo(Path root, Git git) {
        this.root = root;
        this.git = git;
    }

    static TestRepo init(Path directory) {
        try {
            Git git = Git.init()
                    .setDirectory(directory.toFile())
                    .setInitialBranch("main")
                    .call();
            StoredConfig config = git.getRepository().getConfig();
            config.setBoolean("core", null, "autocrlf", false);
            config.setBoolean("commit", null, "gpgsign", false);
            config.save();
            return new TestRepo(directory, git);
        } catch (GitAPIException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    Path root() {
        return root;
    }

    Map<String, String> environment() {
        return Map.of(
                Repositories.CEILING_VARIABLE,
                Objects.requireNonNull(root.getParent()).toString());
    }

    TestRepo write(String path, String content) {
        return writeBytes(path, content.getBytes(StandardCharsets.UTF_8));
    }

    TestRepo writeBytes(String path, byte[] content) {
        try {
            Path file = root.resolve(path);
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            Files.write(file, content);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    TestRepo stage(String path) {
        return run(() -> git.add().addFilepattern(path).call());
    }

    TestRepo writeAndStage(String path, String content) {
        return write(path, content).stage(path);
    }

    TestRepo remove(String path) {
        return run(() -> git.rm().addFilepattern(path).call());
    }

    TestRepo commit(String message) {
        return run(() -> git.commit()
                .setMessage(message)
                .setAuthor(AUTHOR)
                .setCommitter(AUTHOR)
                .setSign(false)
                .call());
    }

    TestRepo newBranch(String name) {
        return run(() -> git.checkout().setCreateBranch(true).setName(name).call());
    }

    TestRepo orphanBranch(String name) {
        return run(() -> git.checkout().setOrphan(true).setName(name).call());
    }

    TestRepo checkout(String name) {
        return run(() -> git.checkout().setName(name).call());
    }

    TestRepo configure(String section, String name, String value) {
        try {
            StoredConfig config = git.getRepository().getConfig();
            config.setString(section, null, name, value);
            config.save();
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TestRepo run(GitAction action) {
        try {
            action.run();
            return this;
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        git.close();
    }
}
