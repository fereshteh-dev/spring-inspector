package io.github.fereshtehdev.springinspector.advisories;

import io.github.fereshtehdev.springinspector.advisories.Advisory.AffectedPackage;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryDatabase.Metadata;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Keeps the advisory database in {@code ~/.spring-inspector/advisories} as one compact binary file with a string
 * table and a CRC32 checksum. It is read on every run, so it loads in milliseconds without a database engine
 * (ADR-0003); a damaged or older-format file reads as missing, and {@code update} rewrites it.
 */
public final class AdvisoryStore {

    static final String FILE_NAME = "osv-maven.bin";
    static final String ATTEMPT_FILE_NAME = "last-attempt";
    private static final int MAGIC = 0x53494144;
    private static final int FORMAT_VERSION = 1;

    private final Path directory;

    public AdvisoryStore(Path directory) {
        this.directory = directory;
    }

    public Path file() {
        return directory.resolve(FILE_NAME);
    }

    /** When the advisories were last updated, read cheaply from the file instead of loading it. */
    public Optional<Instant> lastUpdated() {
        try {
            return Files.isRegularFile(file())
                    ? Optional.of(Files.getLastModifiedTime(file()).toInstant())
                    : Optional.empty();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /** When an automatic update was last tried, successful or not. */
    public Optional<Instant> lastAttempt() {
        try {
            return Optional.of(Instant.parse(
                    Files.readString(directory.resolve(ATTEMPT_FILE_NAME)).strip()));
        } catch (IOException | RuntimeException missingOrDamaged) {
            return Optional.empty();
        }
    }

    public void recordAttempt(Instant attempt) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(ATTEMPT_FILE_NAME), attempt.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write to " + directory, e);
        }
    }

    public Optional<AdvisoryDatabase> load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(decode(Files.readAllBytes(file)));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    public void save(AdvisoryDatabase database) {
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "advisories", ".tmp");
            try {
                Files.write(temporary, encode(database));
                try {
                    Files.move(temporary, file(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the advisory database in " + directory, e);
        }
    }

    static byte[] encode(AdvisoryDatabase database) {
        try {
            Strings strings = new Strings();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(body);
            out.writeLong(database.metadata().fetchedAt().toEpochMilli());
            optional(out, strings, database.metadata().sourceVersion());
            out.writeInt(database.advisories().size());
            for (Advisory advisory : database.advisories()) {
                out.writeInt(strings.index(advisory.id()));
                list(out, strings, advisory.aliases());
                out.writeInt(strings.index(advisory.summary()));
                out.writeInt(strings.index(advisory.severity().name()));
                out.writeInt(advisory.packages().size());
                for (AffectedPackage affected : advisory.packages()) {
                    out.writeInt(strings.index(affected.coordinate()));
                    out.writeInt(affected.ranges().size());
                    for (AffectedRange range : affected.ranges()) {
                        optional(out, strings, range.introduced());
                        optional(out, strings, range.fixed());
                        optional(out, strings, range.lastAffected());
                    }
                    list(out, strings, List.copyOf(affected.versions()));
                }
            }
            ByteArrayOutputStream file = new ByteArrayOutputStream();
            DataOutputStream header = new DataOutputStream(file);
            header.writeInt(MAGIC);
            header.writeInt(FORMAT_VERSION);
            header.writeInt(strings.values.size());
            for (String value : strings.values.keySet()) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                header.writeInt(bytes.length);
                header.write(bytes);
            }
            body.writeTo(header);
            CRC32 crc = new CRC32();
            crc.update(file.toByteArray());
            header.writeLong(crc.getValue());
            header.flush();
            return file.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static AdvisoryDatabase decode(byte[] data) {
        if (data.length < 16) {
            throw new IllegalArgumentException("Too short for an advisory database");
        }
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length - 8);
        ByteBuffer in = ByteBuffer.wrap(data);
        if (in.getLong(data.length - 8) != crc.getValue()) {
            throw new IllegalArgumentException("Checksum mismatch");
        }
        try {
            if (in.getInt() != MAGIC || in.getInt() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Not an advisory database of this format");
            }
            String[] strings = new String[count(in)];
            for (int i = 0; i < strings.length; i++) {
                byte[] bytes = new byte[count(in)];
                in.get(bytes);
                strings[i] = new String(bytes, StandardCharsets.UTF_8);
            }
            Instant fetchedAt = Instant.ofEpochMilli(in.getLong());
            Optional<String> sourceVersion = optional(in, strings);
            int advisoryCount = count(in);
            List<Advisory> advisories = new ArrayList<>(advisoryCount);
            for (int i = 0; i < advisoryCount; i++) {
                String id = string(in, strings);
                List<String> aliases = list(in, strings);
                String summary = string(in, strings);
                AdvisorySeverity severity = AdvisorySeverity.valueOf(string(in, strings));
                int packageCount = count(in);
                List<AffectedPackage> packages = new ArrayList<>(packageCount);
                for (int p = 0; p < packageCount; p++) {
                    String coordinate = string(in, strings);
                    int rangeCount = count(in);
                    List<AffectedRange> ranges = new ArrayList<>(rangeCount);
                    for (int r = 0; r < rangeCount; r++) {
                        ranges.add(
                                new AffectedRange(optional(in, strings), optional(in, strings), optional(in, strings)));
                    }
                    Set<String> versions = new HashSet<>(list(in, strings));
                    packages.add(new AffectedPackage(coordinate, ranges, versions));
                }
                advisories.add(new Advisory(id, aliases, summary, severity, packages));
            }
            return new AdvisoryDatabase(advisories, new Metadata(fetchedAt, sourceVersion));
        } catch (BufferUnderflowException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Truncated advisory database", e);
        }
    }

    private static void optional(DataOutputStream out, Strings strings, Optional<String> value) throws IOException {
        out.writeInt(value.map(strings::index).orElse(-1));
    }

    private static Optional<String> optional(ByteBuffer in, String[] strings) {
        int index = in.getInt();
        return index < 0 ? Optional.empty() : Optional.of(strings[index]);
    }

    private static void list(DataOutputStream out, Strings strings, List<String> values) throws IOException {
        out.writeInt(values.size());
        for (String value : values) {
            out.writeInt(strings.index(value));
        }
    }

    private static List<String> list(ByteBuffer in, String[] strings) {
        int size = count(in);
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(string(in, strings));
        }
        return values;
    }

    private static String string(ByteBuffer in, String[] strings) {
        return strings[in.getInt()];
    }

    private static int count(ByteBuffer in) {
        int count = in.getInt();
        if (count < 0 || count > in.remaining()) {
            throw new IllegalArgumentException("Corrupt count " + count);
        }
        return count;
    }

    private static final class Strings {

        private final Map<String, Integer> values = new LinkedHashMap<>();

        int index(String value) {
            return values.computeIfAbsent(value, key -> values.size());
        }
    }
}
