package dev.momentum.core;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class HttpUtil {
    public static final String SESSION_COOKIE = "momentum_session";
    private HttpUtil() {}

    public static byte[] readBody(HttpExchange exchange, int maxBytes) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            byte[] data = input.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) throw new PayloadTooLargeException();
            return data;
        }
    }

    public static String readText(HttpExchange exchange, int maxBytes) throws IOException {
        return new String(readBody(exchange, maxBytes), StandardCharsets.UTF_8);
    }

    public static Map<String, Object> readObject(HttpExchange exchange, int maxBytes) throws IOException {
        String body = readText(exchange, maxBytes);
        if (body.isBlank()) throw new BadRequestException("Request body is required.");
        try {
            return Json.parseObject(body);
        } catch (Json.JsonException exception) {
            throw new BadRequestException("Invalid JSON body.");
        }
    }

    public static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = Json.stringify(value).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message == null ? "Request failed." : message));
    }

    public static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    public static Optional<String> header(HttpExchange exchange, String name) {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst(name));
    }

    public static Map<String, String> parseCookies(HttpExchange exchange) {
        Map<String, String> cookies = new LinkedHashMap<>();
        String raw = exchange.getRequestHeaders().getFirst("Cookie");
        if (raw == null) return cookies;
        for (String part : raw.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String name = part.substring(0, equals).trim();
            String value = part.substring(equals + 1).trim();
            if (!name.isEmpty()) cookies.put(name, value);
        }
        return cookies;
    }

    public static String sessionToken(HttpExchange exchange) {
        return parseCookies(exchange).get(SESSION_COOKIE);
    }

    public static String sessionCookie(String token, int maxAgeSeconds, boolean secure) {
        String safeToken = token == null ? "" : token.replaceAll("[^A-Za-z0-9_-]", "");
        StringBuilder cookie = new StringBuilder()
                .append(SESSION_COOKIE).append('=').append(safeToken)
                .append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(maxAgeSeconds);
        if (secure) cookie.append("; Secure");
        return cookie.toString();
    }

    public static Map<String, List<String>> parseQuery(String rawQuery) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return values;
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) continue;
            int equals = pair.indexOf('=');
            String key = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = decode(equals < 0 ? "" : pair.substring(equals + 1));
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return values;
    }

    public static String queryValue(String rawQuery, String key) {
        List<String> values = parseQuery(rawQuery).get(key);
        return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
    }

    public static Map<String, String> parseForm(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        if (body == null) return values;
        for (String pair : body.split("&")) {
            if (pair.isBlank()) continue;
            int equals = pair.indexOf('=');
            values.put(
                    decode(equals < 0 ? pair : pair.substring(0, equals)),
                    decode(equals < 0 ? "" : pair.substring(equals + 1)));
        }
        return values;
    }

    public static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static Map<String, String> parseUrlEncoded(String body) {
        return parseForm(body);
    }

    public static String clientIp(HttpExchange exchange) {
        return exchange.getRemoteAddress() == null
                ? "unknown"
                : exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    public static boolean isLoopback(HttpExchange exchange) {
        String ip = clientIp(exchange);
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    public static boolean acceptsJson(HttpExchange exchange) {
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        return accept == null || accept.contains("application/json") || accept.contains("*/*");
    }

    public static void securityHeaders(HttpExchange exchange, boolean secure) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Frame-Options", "DENY");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (secure) headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }

    public static boolean methodIs(HttpExchange exchange, String method) {
        return exchange.getRequestMethod().toUpperCase(Locale.ROOT).equals(method);
    }

    public static final class BadRequestException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BadRequestException(String message) {
            super(message);
        }
    }

    public static final class PayloadTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PayloadTooLargeException() {
            super("Request body is too large.");
        }
    }
}
