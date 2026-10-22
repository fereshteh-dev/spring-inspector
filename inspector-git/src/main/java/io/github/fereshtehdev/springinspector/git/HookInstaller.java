package io.github.fereshtehdev.springinspector.git;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;

public final class HookInstaller {

    static final String MARKER = "# spring-inspector: managed hook";
    static final String HOOK_NAME = "pre-commit";
    static final String BACKUP_SUFFIX = ".spring-inspector-backup";

    private final Map<String, String> environment;

    public HookInstaller() {
        this(System.getenv());
    }

    public HookInstaller(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    public HookInstallation install(Path projectRoot, String command, boolean force) {
        if (command.isBlank() || command.chars().anyMatch(c -> c == '\n' || c == '\r' || c == 0)) {
            throw new IllegalArgumentException("The hook command must be a single non-empty line");
        }
        Repositories.OpenedRepository opened = Repositories.open(projectRoot, environment);
        try (Repository repository = opened.repository()) {
            Path hook = hooksDirectory(repository).resolve(HOOK_NAME);
            String script = script(command, opened.prefix());
            return write(hook, script, force);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not install the pre-commit hook", e);
        }
    }

    private static Path hooksDirectory(Repository repository) {
        String configured = repository
                .getConfig()
                .getString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_HOOKS_PATH);
        if (configured != null && !configured.isBlank()) {
            return repository.getWorkTree().toPath().resolve(configured);
        }
        return repository.getCommonDirectory().toPath().resolve("hooks");
    }

    private static String script(String command, ProjectPrefix prefix) {
        StringBuilder script = new StringBuilder()
                .append("#!/bin/sh\n")
                .append(MARKER)
                .append('\n')
                .append("# Skip once with: git commit --no-verify\n");
        if (!prefix.isRoot()) {
            String directory = prefix.value().substring(0, prefix.value().length() - 1);
            script.append("cd -- '").append(directory.replace("'", "'\\''")).append("' || exit 1\n");
        }
        return script.append(
                        "# Refresh vulnerability advisories at most once a day; a failed refresh never blocks the commit.\n")
                .append(command)
                .append(" update --max-age 1d --quiet || true\n")
                .append("exec ")
                .append(command)
                .append(" check --staged\n")
                .toString();
    }

    private static HookInstallation write(Path hook, String script, boolean force) throws IOException {
        Files.createDirectories(hook.getParent());
        if (!Files.exists(hook)) {
            writeExecutable(hook, script);
            return new HookInstallation.Installed(hook);
        }
        if (Files.readString(hook, StandardCharsets.UTF_8).contains(MARKER)) {
            writeExecutable(hook, script);
            return new HookInstallation.Updated(hook);
        }
        if (!force) {
            return new HookInstallation.Refused(
                    hook,
                    "A pre-commit hook not managed by spring-inspector already exists; use --force to replace it"
                            + " (the existing hook is kept as a backup)");
        }
        Path backup = freeBackupPath(hook);
        Files.move(hook, backup);
        writeExecutable(hook, script);
        return new HookInstallation.Replaced(hook, backup);
    }

    private static Path freeBackupPath(Path hook) {
        Path backup = hook.resolveSibling(HOOK_NAME + BACKUP_SUFFIX);
        for (int n = 1; Files.exists(backup); n++) {
            backup = hook.resolveSibling(HOOK_NAME + BACKUP_SUFFIX + "." + n);
        }
        return backup;
    }

    private static void writeExecutable(Path hook, String script) throws IOException {
        Files.writeString(hook, script, StandardCharsets.UTF_8);
        PosixFileAttributeView posix = Files.getFileAttributeView(hook, PosixFileAttributeView.class);
        if (posix == null) {
            hook.toFile().setExecutable(true, false);
            return;
        }
        Set<PosixFilePermission> permissions =
                EnumSet.copyOf(posix.readAttributes().permissions());
        permissions.addAll(EnumSet.of(
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_EXECUTE));
        posix.setPermissions(permissions);
    }
}
