package io.github.fereshtehdev.springinspector.javamodel.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes {@code .spring-inspector/cache/findings.bin} inside the inspected project. The project is
 * untrusted: the file must carry this user's signature, and the cache directories must be real directories inside
 * the project, not links that point elsewhere.
 */
final class CacheStore {

    static final Path DIRECTORY = Path.of(".spring-inspector", "cache");
    static final String FILE_NAME = "findings.bin";
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    private final Path projectRoot;
    private final CacheSecret secret;

    CacheStore(Path projectRoot, CacheSecret secret) {
        this.projectRoot = projectRoot;
        this.secret = secret;
    }

    /** Never fails: a missing, foreign, corrupt or oversized cache is an empty one. */
    Map<CacheKey, CacheEntry> load() {
        try {
            Optional<Path> directory = directory(false);
            if (directory.isEmpty()) {
                return Map.of();
            }
            Path file = directory.get().resolve(FILE_NAME);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE_BYTES) {
                return Map.of();
            }
            byte[] bytes = Files.readAllBytes(file);
            int signatureLength = CacheSecret.signatureLength();
            if (bytes.length < signatureLength) {
                return Map.of();
            }
            byte[] signature = Arrays.copyOfRange(bytes, 0, signatureLength);
            byte[] payload = Arrays.copyOfRange(bytes, signatureLength, bytes.length);
            return secret.verifies(payload, signature) ? CacheCodec.decode(payload) : Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    /** Returns whether the cache was written; a cache that cannot be written is simply not used next time. */
    boolean save(Map<CacheKey, CacheEntry> entries) {
        try {
            Optional<Path> directory = directory(true);
            if (directory.isEmpty()) {
                return false;
            }
            byte[] payload = CacheCodec.encode(entries);
            Path temporary = Files.createTempFile(directory.get(), "findings", ".tmp");
            try {
                Files.write(temporary, secret.sign(payload));
                Files.write(temporary, payload, StandardOpenOption.APPEND);
                move(temporary, directory.get().resolve(FILE_NAME));
                return true;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Optional<Path> directory(boolean create) throws IOException {
        Path realRoot = projectRoot.toRealPath();
        Path current = realRoot;
        for (Path part : DIRECTORY) {
            current = current.resolve(part);
            if (create && Files.notExists(current, LinkOption.NOFOLLOW_LINKS)) {
                createDirectory(current);
            }
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)
                    || !current.toRealPath().equals(current)) {
                return Optional.empty();
            }
        }
        if (create) {
            ignoreInGit(current);
        }
        return Optional.of(current);
    }

    private static void createDirectory(Path directory) throws IOException {
        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException raced) {
            // Another run created it; the checks after this call decide whether it can be used.
        }
    }

    private static void ignoreInGit(Path directory) throws IOException {
        try {
            Files.writeString(
                    directory.resolve(".gitignore"), "*\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException alreadyIgnored) {
            // Never overwrite: CREATE_NEW also refuses a planted link in its place.
        }
    }
}
