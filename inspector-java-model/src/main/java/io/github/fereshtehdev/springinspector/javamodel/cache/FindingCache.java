package io.github.fereshtehdev.springinspector.javamodel.cache;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Findings of earlier runs, per check and file, so unchanged files are neither parsed nor checked again. Thread-safe;
 * checks run concurrently.
 */
public final class FindingCache {

    static final int MAX_IDLE_DAYS = 30;
    static final int MAX_ENTRIES = 100_000;

    private final @Nullable CacheStore store;
    private final Clock clock;
    private final ConcurrentMap<CacheKey, CacheEntry> entries;
    private final AtomicBoolean dirty = new AtomicBoolean();

    private FindingCache(@Nullable CacheStore store, Clock clock, Map<CacheKey, CacheEntry> entries) {
        this.store = store;
        this.clock = clock;
        this.entries = new ConcurrentHashMap<>(entries);
    }

    public static FindingCache disabled() {
        return new FindingCache(null, Clock.systemUTC(), Map.of());
    }

    public static FindingCache open(Path projectRoot, CacheSecret secret) {
        return open(projectRoot, secret, Clock.systemUTC());
    }

    static FindingCache open(Path projectRoot, CacheSecret secret, Clock clock) {
        CacheStore store = new CacheStore(projectRoot, secret);
        return new FindingCache(store, clock, store.load());
    }

    public boolean enabled() {
        return store != null;
    }

    /**
     * Returns the cached findings if every file the result depended on still has the same content.
     *
     * @param currentHashes the current content hash of a project file, or empty if it does not exist
     */
    public Optional<List<Finding>> lookup(CacheKey key, Function<Path, Optional<String>> currentHashes) {
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        for (Map.Entry<Path, Optional<String>> dependency : entry.dependencies().entrySet()) {
            if (!currentHashes.apply(dependency.getKey()).equals(dependency.getValue())) {
                return Optional.empty();
            }
        }
        long today = today();
        if (entry.lastUsedDay() != today) {
            entries.put(key, entry.usedOn(today));
            dirty.set(true);
        }
        return Optional.of(entry.findings());
    }

    public void store(CacheKey key, List<Finding> findings, Map<Path, Optional<String>> dependencies) {
        if (store == null) {
            return;
        }
        entries.put(key, new CacheEntry(findings, dependencies, today()));
        dirty.set(true);
    }

    /** Writes the cache if anything changed; returns whether the cache on disk is now current. */
    public boolean save() {
        if (store == null || !dirty.getAndSet(false)) {
            return store != null;
        }
        boolean saved = store.save(retained());
        if (!saved) {
            dirty.set(true);
        }
        return saved;
    }

    int size() {
        return entries.size();
    }

    private Map<CacheKey, CacheEntry> retained() {
        long oldestKept = today() - MAX_IDLE_DAYS;
        Map<CacheKey, CacheEntry> retained = new HashMap<>();
        entries.entrySet().stream()
                .filter(entry -> entry.getValue().lastUsedDay() >= oldestKept)
                .sorted(Comparator.comparingLong((Map.Entry<CacheKey, CacheEntry> entry) ->
                                entry.getValue().lastUsedDay())
                        .reversed())
                .limit(MAX_ENTRIES)
                .forEach(entry -> retained.put(entry.getKey(), entry.getValue()));
        return retained;
    }

    private long today() {
        return LocalDate.now(clock).toEpochDay();
    }
}
