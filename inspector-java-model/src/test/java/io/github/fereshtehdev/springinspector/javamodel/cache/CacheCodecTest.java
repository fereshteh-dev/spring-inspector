package io.github.fereshtehdev.springinspector.javamodel.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CacheCodecTest {

    private static final CacheKey KEY = new CacheKey("rule@jar", SampleFindings.FILE, "abc123");

    private static final CacheEntry ENTRY = new CacheEntry(
            List.of(SampleFindings.withPatch(), SampleFindings.withoutPatch()),
            Map.of(Path.of("src", "Dep.java"), Optional.of("def456"), Path.of("src", "Gone.java"), Optional.empty()),
            20_000);

    @Test
    void decodesExactlyWhatWasEncoded() {
        byte[] encoded = CacheCodec.encode(Map.of(KEY, ENTRY));

        assertThat(CacheCodec.decode(encoded)).isEqualTo(Map.of(KEY, ENTRY));
    }

    @Test
    void rejectsDataThatIsNotACache() {
        assertThatThrownBy(() -> CacheCodec.decode("not a cache".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsATruncatedCache() {
        byte[] encoded = CacheCodec.encode(Map.of(KEY, ENTRY));

        assertThatThrownBy(() -> CacheCodec.decode(Arrays.copyOf(encoded, encoded.length - 5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] encoded = CacheCodec.encode(Map.of());

        assertThatThrownBy(() -> CacheCodec.decode(Arrays.copyOf(encoded, encoded.length + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trailing");
    }

    @Test
    void rejectsCountsLargerThanTheDataInsteadOfAllocatingThem() {
        byte[] hostile = ByteBuffer.allocate(12)
                .putInt(0x53494346)
                .putInt(2)
                .putInt(Integer.MAX_VALUE)
                .array();

        assertThatThrownBy(() -> CacheCodec.decode(hostile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Corrupt count");
    }

    @Test
    void rejectsReferencesToStringsThatAreNotInTheTable() {
        byte[] hostile = ByteBuffer.allocate(20)
                .putInt(0x53494346)
                .putInt(2)
                .putInt(0)
                .putInt(1)
                .putInt(7)
                .array();

        assertThatThrownBy(() -> CacheCodec.decode(hostile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Corrupt string reference");
    }

    @Test
    void storesRepeatedStringsOnlyOnce() {
        String longFingerprint = "rule@jar;".repeat(200);
        Map<CacheKey, CacheEntry> one = Map.of(new CacheKey(longFingerprint, SampleFindings.FILE, "a"), ENTRY);
        Map<CacheKey, CacheEntry> two = Map.of(
                new CacheKey(longFingerprint, SampleFindings.FILE, "a"), ENTRY,
                new CacheKey(longFingerprint, SampleFindings.FILE, "b"), ENTRY);

        int growth = CacheCodec.encode(two).length - CacheCodec.encode(one).length;

        assertThat(growth).isLessThan(longFingerprint.length() / 4);
    }
}
