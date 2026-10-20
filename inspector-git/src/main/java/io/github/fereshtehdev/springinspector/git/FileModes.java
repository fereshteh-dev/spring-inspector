package io.github.fereshtehdev.springinspector.git;

import org.eclipse.jgit.lib.FileMode;

final class FileModes {

    private FileModes() {}

    static boolean isRegularFile(FileMode mode) {
        return (mode.getBits() & FileMode.TYPE_MASK) == FileMode.TYPE_FILE;
    }
}
