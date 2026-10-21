package io.github.fereshtehdev.springinspector.javamodel;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The only way checks reach the symbol solver. JavaParser's resolution caches are not thread-safe, so queries run one
 * at a time; each answer is a plain value, never a live resolved declaration that could resolve more later.
 */
final class Resolver {

    private final ReentrantLock lock = new ReentrantLock();
    private @Nullable DependencyRecorder recording;

    <T> Optional<T> resolve(DependencyRecorder recorder, Supplier<@Nullable T> query) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while waiting to resolve a symbol");
        }
        try {
            recording = recorder;
            return Optional.ofNullable(query.get());
        } catch (RuntimeException | StackOverflowError unresolvable) {
            return Optional.empty();
        } finally {
            recording = null;
            lock.unlock();
        }
    }

    /** Called by the type solver, which only runs inside {@link #resolve}. */
    void dependsOn(Path file, Optional<String> contentHash) {
        DependencyRecorder recorder = recording;
        if (recorder != null) {
            recorder.record(file, contentHash);
        }
    }
}
