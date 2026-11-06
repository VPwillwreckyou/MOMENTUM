package dev.momentum.web;

import com.sun.net.httpserver.HttpExchange;
import dev.momentum.core.HttpUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Serves the built web client and falls back to the SPA shell. */
public final class StaticFiles {
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("webmanifest", "application/manifest+json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("pdf", "application/pdf"));

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self' data:",
            "connect-src 'self'",
            "media-src 'self' blob:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    private final Path root;
    private final boolean secure;

    public StaticFiles(Path root, boolean secure) {
        this.root = root.toAbsolutePath().normalize();
        this.secure = secure;
    }

    public Path root() {
        return root;
    }

    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.methodIs(exchange, "GET") && !HttpUtil.methodIs(exchange, "HEAD")) {
            HttpUtil.sendError(exchange, 405, "Method not allowed.");
            return;
        }
        HttpUtil.securityHeaders(exchange, secure);
        exchange.getResponseHeaders().set("Content-Security-Policy", CSP);
        String requestPath;
        try {
            requestPath = exchange.getRequestURI().getPath();
        } catch (Exception exception) {
            HttpUtil.sendError(exchange, 400, "Invalid path.");
            return;
        }
        if (requestPath == null || requestPath.contains("\0")) {
            HttpUtil.sendError(exchange, 400, "Invalid path.");
            return;
        }
        Path file = resolve(requestPath);
        if (file == null) {
            HttpUtil.sendError(exchange, 404, "Not found.");
            return;
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            if (hasExtension(requestPath)) {
                HttpUtil.sendError(exchange, 404, "Not found.");
                return;
            }
            file = root.resolve("index.html");
            if (!Files.isRegularFile(file)) {
                sendNotBuilt(exchange);
                return;
            }
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "html";
        String contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
        long size = Files.size(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", cacheControl(requestPath));
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (HttpUtil.methodIs(exchange, "HEAD")) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        boolean compressible = contentType.startsWith("text/")
                || contentType.contains("javascript")
                || contentType.contains("json")
                || contentType.contains("svg")
                || contentType.contains("manifest");
        String acceptsEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        boolean gzip = compressible && acceptsEncoding != null
                && acceptsEncoding.toLowerCase(Locale.ROOT).contains("gzip");
        if (gzip) {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.getResponseHeaders().add("Vary", "Accept-Encoding");
            byte[] compressed;
            try {
                compressed = gzip(Files.readAllBytes(file));
            } catch (IOException exception) {
                HttpUtil.sendError(exchange, 500, "Could not read the requested asset.");
                return;
            }
            exchange.sendResponseHeaders(200, compressed.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(compressed);
            }
            return;
        }
        exchange.sendResponseHeaders(200, size);
        try (OutputStream output = exchange.getResponseBody()) {
            Files.copy(file, output);
        } catch (IOException exception) {
            exchange.close();
        }
    }

    private static byte[] gzip(byte[] input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(input);
        }
        return buffer.toByteArray();
    }

    private static String cacheControl(String requestPath) {
        if (requestPath.startsWith("/assets/")) return "public, max-age=31536000, immutable";
        if (requestPath.equals("/") || requestPath.endsWith(".html")) return "no-cache";
        return "public, max-age=3600";
    }

    private Path resolve(String requestPath) {
        String relative = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        if (relative.isBlank()) return root.resolve("index.html");
        try {
            Path resolved = root.resolve(relative).normalize();
            if (!resolved.startsWith(root)) return null;
            return resolved;
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean hasExtension(String path) {
        String segment = path.substring(path.lastIndexOf('/') + 1);
        int dot = segment.lastIndexOf('.');
        return dot > 0 && dot < segment.length() - 1;
    }

    private void sendNotBuilt(HttpExchange exchange) throws IOException {
        byte[] message = ("<!doctype html><html><body style=\"font-family:sans-serif;background:#070a10;color:#e7edf7;padding:48px\">"
                + "<h1>MOMENTUM web client is not built</h1>"
                + "<p>Run <code>npm run build</code> in the project root, then restart the service.</p>"
                + "</body></html>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(503, message.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(message);
        }
    }
}
