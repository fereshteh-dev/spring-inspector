package io.github.fereshtehdev.springinspector.javamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.javamodel.Fixtures.ScriptedCheck;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckFingerprintsTest {

    @TempDir
    Path temp;

    @Test
    void checksLoadedFromAClassDirectoryAreNotFingerprinted() {
        ScriptedCheck fromTestClasses = new ScriptedCheck("any", file -> List.of());

        assertThat(CheckFingerprints.fromCodeSource().of(fromTestClasses)).isEmpty();
    }

    @Test
    void aJarIsIdentifiedByItsNameSizeAndModificationTime() throws IOException {
        Path jar = Files.writeString(temp.resolve("rules.jar"), "jar");
        Files.setLastModifiedTime(jar, FileTime.fromMillis(1_000));

        assertThat(CheckFingerprints.jar(jar)).contains("rules.jar:3:1000");
    }

    @Test
    void aRebuiltJarHasADifferentFingerprint() throws IOException {
        Path jar = Files.writeString(temp.resolve("rules.jar"), "jar");
        String before = CheckFingerprints.jar(jar).orElseThrow();
        Files.writeString(jar, "rebuilt jar");

        assertThat(CheckFingerprints.jar(jar)).isPresent().get().isNotEqualTo(before);
    }
}
