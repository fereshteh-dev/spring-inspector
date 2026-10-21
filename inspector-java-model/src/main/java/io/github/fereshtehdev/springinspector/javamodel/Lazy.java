package io.github.fereshtehdev.springinspector.javamodel;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Computes a value once, even when several checks ask for it at the same time. */
final class Lazy<T> {

    private final Supplier<T> supplier;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile @Nullable T value;

    Lazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    T get() {
        T current = value;
        if (current != null) {
            return current;
        }
        lock.lock();
        try {
            current = value;
            if (current == null) {
                current = supplier.get();
                value = current;
            }
            return current;
        } finally {
            lock.unlock();
        }
    }
}
