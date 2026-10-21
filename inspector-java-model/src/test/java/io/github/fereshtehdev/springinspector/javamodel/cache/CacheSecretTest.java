package io.github.fereshtehdev.springinspector.javamodel.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheSecretTest {

    private static final byte[] PAYLOAD = {1, 2, 3};

    @TempDir
    Path home;

    @Test
    void createsAKeyOnceAndReusesItAfterwards() {
        Path keyFile = home.resolve(".spring-inspector").resolve("cache.key");

        byte[] signature = CacheSecret.loadOrCreate(keyFile).sign(PAYLOAD);

        assertThat(keyFile).hasSize(32);
        assertThat(CacheSecret.loadOrCreate(keyFile).verifies(PAYLOAD, signature))
                .isTrue();
    }

    @Test
    void replacesAKeyFileOfTheWrongSize() throws IOException {
        Path keyFile = home.resolve("cache.key");
        Files.write(keyFile, new byte[] {1});

        CacheSecret.loadOrCreate(keyFile);

        assertThat(keyFile).hasSize(32);
    }

    @Test
    void differentKeysProduceDifferentSignatures() {
        byte[] signature = CacheSecret.random().sign(PAYLOAD);

        assertThat(CacheSecret.random().verifies(PAYLOAD, signature)).isFalse();
    }

    @Test
    void neverPrintsTheKey() {
        assertThat(CacheSecret.random()).hasToString("CacheSecret[redacted]");
    }
}
