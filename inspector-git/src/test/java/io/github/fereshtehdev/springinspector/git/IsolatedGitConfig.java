package io.github.fereshtehdev.springinspector.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Hides the developer's own ~/.gitconfig and system config so tests behave the same everywhere. */
final class IsolatedGitConfig implements BeforeAllCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(IsolatedGitConfig.class);

    @Override
    public void beforeAll(ExtensionContext context) throws IOException {
        SystemReader original = SystemReader.getInstance();
        context.getStore(NAMESPACE).put(SystemReader.class, original);
        File missing =
                Files.createTempDirectory("no-gitconfig").resolve("gitconfig").toFile();
        SystemReader.setInstance(new SystemReader.Delegate(original) {
            @Override
            public FileBasedConfig openUserConfig(Config parent, FS fs) {
                return new FileBasedConfig(parent, missing, fs);
            }

            @Override
            public FileBasedConfig openSystemConfig(Config parent, FS fs) {
                return new FileBasedConfig(parent, missing, fs);
            }

            @Override
            public FileBasedConfig openJGitConfig(Config parent, FS fs) {
                return new FileBasedConfig(parent, missing, fs);
            }
        });
    }

    @Override
    public void afterAll(ExtensionContext context) {
        SystemReader.setInstance(context.getStore(NAMESPACE).get(SystemReader.class, SystemReader.class));
    }
}
