package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.project.config.ConfigFileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "init",
        description = "Write a commented " + ConfigFileReader.FILE_NAME + " with the default settings.",
        sortOptions = false)
final class InitCommand implements Callable<Integer> {

    @Option(names = "--project", paramLabel = "<dir>", description = "Project directory. Default: current directory.")
    @Nullable
    Path project;

    private final Path workingDirectory;
    private final PrintWriter out;
    private final PrintWriter err;

    InitCommand(Path workingDirectory, PrintWriter out, PrintWriter err) {
        this.workingDirectory = workingDirectory;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() throws IOException {
        Path root = (project == null ? workingDirectory : workingDirectory.resolve(project))
                .toAbsolutePath()
                .normalize();
        Path file = root.resolve(ConfigFileReader.FILE_NAME);
        try {
            Files.write(file, sample(), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException exists) {
            err.println("error: " + file + " already exists; edit it, or delete it to start over");
            err.flush();
            return ExitCodes.BLOCKED;
        }
        out.println("Wrote " + file);
        out.println("Commit it so everyone on the project uses the same settings.");
        out.flush();
        return ExitCodes.OK;
    }

    static byte[] sample() {
        try (InputStream in = Objects.requireNonNull(
                InitCommand.class.getResourceAsStream(ConfigFileReader.FILE_NAME), "sample settings resource")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
