package dev.momentum.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * File-based attachment storage for the MOMENTUM desktop data layer.
 *
 * Layout: {dataDir}/attachments/{attachmentId}/{sanitizedFileName}
 * Only file paths relative to the attachments root are stored in SQLite,
 * so the whole data directory stays location-independent (copyable, backup-friendly).
 */
public final class AttachmentStore {

    private final Path root;

    public AttachmentStore(Path dataDirectory) {
        this.root = dataDirectory.resolve("attachments");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create attachment directory: " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    /** Writes an attachment and returns its relative path ("{id}/{filename}"). */
    public String save(String attachmentId, String fileName, byte[] bytes) throws IOException {
        String fileNameClean = sanitizeFileName(fileName);
        Path directory = root.resolve(attachmentId);
        Files.createDirectories(directory);
        Path target = uniqueTarget(directory, fileNameClean);
        Files.write(target, bytes);
        return attachmentId + "/" + target.getFileName().toString();
    }

    /** Resolves a stored relative path to an absolute file path, rejecting escapes. */
    public Path resolveRelative(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Attachment path escapes the attachments directory.");
        }
        return resolved;
    }

    public boolean fileExists(String relativePath) {
        return Files.isRegularFile(resolveRelative(relativePath));
    }

    public long fileSize(String relativePath) throws IOException {
        return Files.size(resolveRelative(relativePath));
    }

    /** SHA-256 checksum of a stored attachment (hex), for integrity verification. */
    public String checksum(String relativePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Files.copy(resolveRelative(relativePath), new java.io.OutputStream() {
            @Override public void write(int b) { digest.update((byte) b); }
            @Override public void write(byte[] data, int off, int len) { digest.update(data, off, len); }
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Deletes one attachment (file + the per-attachment directory if left empty). */
    public void delete(String relativePath) throws IOException {
        Path file = resolveRelative(relativePath);
        Files.deleteIfExists(file);
        Path parent = file.getParent();
        if (parent != null && parent.startsWith(root)) {
            try (var entries = Files.list(parent)) {
                if (entries.findAny().isEmpty()) {
                    Files.deleteIfExists(parent);
                }
            }
        }
    }

    /** Recursively deletes the entire attachments directory (used only for rollback of temp copies). */
    public static void wipeDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Rollback best effort.
                }
            });
        }
    }

    public static String sanitizeFileName(String fileName) {
        String clean = fileName == null || fileName.isBlank() ? "attachment" : fileName;
        clean = clean.replace('\\', '_').replace('/', '_').replace(':', '_');
        // Control characters and the rest of a Windows path.
        clean = clean.replaceAll("[\\x00-\\x1f<>\"|?*]", "_");
        if (clean.length() > 120) {
            int dot = clean.lastIndexOf('.');
            String extension = dot > 0 ? clean.substring(dot) : "";
            clean = clean.substring(0, Math.max(1, 120 - extension.length())) + extension;
        }
        return clean;
    }

    private static Path uniqueTarget(Path directory, String fileName) {
        Path target = directory.resolve(fileName);
        if (!Files.exists(target)) return target;
        String stem;
        String extension = "";
        String base = fileName;
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            stem = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        } else {
            stem = fileName;
        }
        for (int counter = 1; counter < 10_000; counter++) {
            Path candidate = directory.resolve(stem + "-" + counter + extension);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not find a free attachment file name: " + fileName);
    }

    /** Decodes a "data:{mime};base64,{payload}" URL produced by the web backup exporter. */
    public static byte[] decodeDataUrl(String dataUrl, String[] mimeTypeOut) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            throw new IllegalArgumentException("Attachment payload is not a data URL.");
        }
        int base64Marker = dataUrl.indexOf(";base64,");
        if (base64Marker < 0) {
            throw new IllegalArgumentException("Attachment data URL has no base64 payload.");
        }
        if (mimeTypeOut != null && mimeTypeOut.length > 0) {
            mimeTypeOut[0] = dataUrl.substring(5, base64Marker);
        }
        String payload = dataUrl.substring(base64Marker + ";base64,".length());
        return java.util.Base64.getMimeDecoder().decode(payload.getBytes(StandardCharsets.US_ASCII));
    }
}
