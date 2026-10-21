package io.github.fereshtehdev.springinspector.javamodel.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A per-user key that signs the findings cache. The cache lives inside the inspected repository, which is untrusted,
 * so a committed cache file must never be able to hide findings.
 */
public final class CacheSecret {

    private static final int KEY_BYTES = 32;
    private static final int SIGNATURE_BYTES = 32;
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    private CacheSecret(byte[] key) {
        this.key = key.clone();
    }

    public static CacheSecret random() {
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return new CacheSecret(key);
    }

    /** Reads the key from {@code keyFile}, creating it (readable by the owner only on POSIX) when missing. */
    public static CacheSecret loadOrCreate(Path keyFile) {
        try {
            if (Files.isRegularFile(keyFile, LinkOption.NOFOLLOW_LINKS) && Files.size(keyFile) == KEY_BYTES) {
                return new CacheSecret(Files.readAllBytes(keyFile));
            }
            Path parent = keyFile.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, "cache", ".key");
            try {
                Files.write(temporary, random().key);
                Files.move(temporary, keyFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            // Another process may have written its own key at the same moment; whichever landed wins.
            return new CacheSecret(Files.readAllBytes(keyFile));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the cache key " + keyFile, e);
        }
    }

    byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is required by every Java platform", e);
        }
    }

    boolean verifies(byte[] payload, byte[] signature) {
        return MessageDigest.isEqual(sign(payload), signature);
    }

    static int signatureLength() {
        return SIGNATURE_BYTES;
    }

    @Override
    public String toString() {
        return "CacheSecret[redacted]";
    }
}
