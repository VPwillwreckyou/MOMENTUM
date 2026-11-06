package dev.momentum.sync;

import dev.momentum.core.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Stores only opaque, client-encrypted workspace envelopes. The server cannot
 * read goal titles, notes, tasks, or categories.
 */
public final class SyncStore {
    private static final Pattern SAFE_USER_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
    private static final int MAX_ENVELOPE_BYTES = 4 * 1024 * 1024;

    public record Snapshot(String envelope, long revision, String updatedAt) {}

    private final Path directory;

    public SyncStore(Path dataDirectory) {
        this.directory = dataDirectory.resolve("sync");
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the sync directory.", exception);
        }
    }

    public synchronized Optional<Snapshot> get(String userId) {
        Path file = fileFor(userId);
        if (!Files.exists(file)) return Optional.empty();
        try {
            Map<String, Object> record = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            String envelope = string(record.get("envelope"));
            long revision = number(record.get("revision"));
            String updatedAt = string(record.get("updatedAt"));
            if (envelope == null) return Optional.empty();
            return Optional.of(new Snapshot(envelope, revision, updatedAt));
        } catch (Exception exception) {
            throw new IllegalStateException("Encrypted sync data is unreadable.", exception);
        }
    }

    public synchronized Snapshot put(String userId, String envelope, long baseRevision) {
        if (!SAFE_USER_ID.matcher(userId).matches()) throw new IllegalArgumentException("Invalid user id.");
        if (envelope == null || envelope.isBlank()) throw new IllegalArgumentException("Encrypted envelope is required.");
        if (envelope.getBytes(StandardCharsets.UTF_8).length > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Encrypted workspace exceeds the 4 MB sync limit.");
        }
        validateEnvelope(envelope);
        Optional<Snapshot> existing = get(userId);
        if (existing.isEmpty() && baseRevision != 0) {
            throw new ConflictException(null, 0);
        }
        if (existing.isPresent() && existing.get().revision() != baseRevision) {
            throw new ConflictException(existing.get(), existing.get().revision());
        }
        long revision = existing.map(Snapshot::revision).orElse(0L) + 1;
        String now = Instant.now().toString();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("envelope", envelope);
        record.put("revision", revision);
        record.put("updatedAt", now);
        try {
            Path file = fileFor(userId);
            Path temp = Files.createTempFile(directory, "envelope-", ".tmp");
            Files.writeString(temp, Json.pretty(record), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save encrypted sync data.", exception);
        }
        return new Snapshot(envelope, revision, now);
    }

    public synchronized void delete(String userId) {
        if (!SAFE_USER_ID.matcher(userId).matches()) return;
        try {
            Files.deleteIfExists(fileFor(userId));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete encrypted sync data.", exception);
        }
    }

    private static void validateEnvelope(String envelope) {
        // Envelope content is opaque, but ensure it is syntactically valid JSON
        // with the expected client-side encrypted payload fields.
        try {
            Map<String, Object> parsed = Json.parseObject(envelope);
            Object version = parsed.get("version");
            Object salt = parsed.get("salt");
            Object iv = parsed.get("iv");
            Object ciphertext = parsed.get("ciphertext");
            if (!(version instanceof Number) || salt == null || iv == null || ciphertext == null) {
                throw new IllegalArgumentException("Encrypted envelope has an invalid shape.");
            }
        } catch (Json.JsonException exception) {
            throw new IllegalArgumentException("Encrypted envelope is not valid JSON.");
        }
    }

    private Path fileFor(String userId) {
        if (!SAFE_USER_ID.matcher(userId).matches()) throw new IllegalArgumentException("Invalid user id.");
        return directory.resolve(userId + ".json");
    }

    private static String string(Object value) {
        return value instanceof String string ? string : null;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    public static final class ConflictException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient Snapshot current;
        private final long revision;

        public ConflictException(Snapshot current, long revision) {
            super("The encrypted workspace changed on another device.");
            this.current = current;
            this.revision = revision;
        }

        public Snapshot current() {
            return current;
        }

        public long revision() {
            return revision;
        }
    }
}
