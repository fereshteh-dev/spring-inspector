package io.github.fereshtehdev.springinspector.cli;

/** Process exit codes; scripts and the git hook rely on them. */
final class ExitCodes {

    static final int OK = 0;
    /** The gate blocked, or install-hook was refused. */
    static final int BLOCKED = 1;
    /** Wrong arguments or a project the command cannot work on. */
    static final int USAGE = 2;
    /** The tool itself failed. */
    static final int FAILURE = 3;

    private ExitCodes() {}
}
