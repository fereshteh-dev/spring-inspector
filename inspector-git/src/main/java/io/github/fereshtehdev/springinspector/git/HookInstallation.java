package io.github.fereshtehdev.springinspector.git;

import java.nio.file.Path;

public sealed interface HookInstallation {

    Path hook();

    record Installed(Path hook) implements HookInstallation {}

    record Updated(Path hook) implements HookInstallation {}

    record Replaced(Path hook, Path backup) implements HookInstallation {}

    record Refused(Path hook, String reason) implements HookInstallation {}
}
