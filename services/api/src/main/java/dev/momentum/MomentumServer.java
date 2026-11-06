package dev.momentum;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.momentum.ai.AiProxy;
import dev.momentum.auth.AccountStore;
import dev.momentum.auth.OAuthService;
import dev.momentum.core.HttpUtil;
import dev.momentum.core.PasswordHasher;
import dev.momentum.core.RateLimiter;
import dev.momentum.sync.SyncStore;
import dev.momentum.web.StaticFiles;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MOMENTUM's dependency-free Java API and static web host.
 *
 * Run from the repository root:
 *   java -cp services/api/build/classes dev.momentum.MomentumServer
 */
public final class MomentumServer {
    private static final String VERSION = "1.0.0";
    private static final int MAX_AUTH_BODY = 32 * 1024;
    private static final int MAX_AI_BODY = 120 * 1024;
    private static final int MAX_SYNC_BODY = 4_300_000;

    private final AccountStore accounts;
    private final OAuthService oauth;
    private final SyncStore sync;
    private final AiProxy ai;
    private final StaticFiles staticFiles;
    private final HttpServer server;
    private final Set<String> allowedOrigins;
    private final boolean secureCookies;
    private final boolean allowLocalAiKey;
    private final RateLimiter authLimiter = new RateLimiter(40, Duration.ofMinutes(15));
    private final RateLimiter aiLimiter = new RateLimiter(24, Duration.ofMinutes(1));
    private final RateLimiter syncLimiter = new RateLimiter(40, Duration.ofMinutes(1));
    private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "momentum-maintenance");
        thread.setDaemon(true);
        return thread;
    });
    private int port;
    private String publicBase;
    private String appUrl;

    public MomentumServer() throws IOException {
        Path dataDirectory = Path.of(env("MOMENTUM_DATA_DIR", "data")).toAbsolutePath().normalize();
        Path webRoot = Path.of(env("MOMENTUM_WEB_DIST", "apps/web/dist")).toAbsolutePath().normalize();
        this.publicBase = stripTrailingSlash(env("MOMENTUM_PUBLIC_URL", "http://127.0.0.1:8787"));
        this.secureCookies = Boolean.parseBoolean(env("MOMENTUM_COOKIE_SECURE", "false"))
                || publicBase.startsWith("https://");
        this.allowLocalAiKey = Boolean.parseBoolean(env("MOMENTUM_ALLOW_LOCAL_AI_KEY", "false"));
        this.accounts = new AccountStore(dataDirectory);
        this.oauth = new OAuthService(accounts);
        this.appUrl = oauth.appUrl();
        this.sync = new SyncStore(dataDirectory);
        this.ai = new AiProxy();
        this.staticFiles = new StaticFiles(webRoot, secureCookies);
        this.allowedOrigins = buildAllowedOrigins();
        String host = env("MOMENTUM_HOST", "127.0.0.1");
        int configuredPort = Integer.parseInt(env("MOMENTUM_PORT", env("PORT", "8787")));
        this.server = HttpServer.create(new InetSocketAddress(host, configuredPort), 0);
        this.port = configuredPort;
        this.server.createContext("/api", this::handleApi);
        this.server.createContext("/api/", this::handleApi);
        this.server.createContext("/", this::handleStatic);
        this.server.setExecutor(Executors.newFixedThreadPool(
                Math.max(4, Math.min(24, Runtime.getRuntime().availableProcessors() * 2))));
    }

    public void start() {
        server.start();
        port = server.getAddress().getPort();
        publicBase = publicBase.replaceFirst(":\\d+$", ":" + port);
        maintenance.scheduleAtFixedRate(() -> {
            accounts.cleanupSessions();
            authLimiter.cleanup();
            aiLimiter.cleanup();
            syncLimiter.cleanup();
        }, 15, 15, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            maintenance.shutdownNow();
            server.stop(1);
        }, "momentum-shutdown"));
        System.out.println("MOMENTUM API " + VERSION + " listening on http://127.0.0.1:" + port);
        System.out.println("  Web root: " + staticFiles.root());
        System.out.println("  Data dir: " + Path.of(env("MOMENTUM_DATA_DIR", "data")).toAbsolutePath().normalize());
        System.out.println("  OAuth configured: " + configuredProviders());
        if (!Files.isDirectory(staticFiles.root())) {
            System.out.println("  Web client not built yet. Run: npm run build");
        }
    }

    public int port() {
        return port;
    }

    public void stop() {
        maintenance.shutdownNow();
        server.stop(0);
    }

    private void handleStatic(HttpExchange exchange) {
        try {
            staticFiles.handle(exchange);
        } catch (Exception exception) {
            try {
                HttpUtil.sendError(exchange, 500, "The web client could not be served.");
            } catch (IOException ignored) {
                // Connection already closed.
            }
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            HttpUtil.securityHeaders(exchange, secureCookies);
            applyCors(exchange);
            if (HttpUtil.methodIs(exchange, "OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!originAllowedForMutation(exchange)) {
                HttpUtil.sendError(exchange, 403, "Request origin is not allowed.");
                return;
            }
            route(exchange, path);
        } catch (HttpUtil.PayloadTooLargeException exception) {
            safeError(exchange, 413, "Request body is too large.");
        } catch (HttpUtil.BadRequestException exception) {
            safeError(exchange, 400, exception.getMessage());
        } catch (AccountStore.AuthException exception) {
            safeError(exchange, exception.status(), exception.getMessage());
        } catch (OAuthService.OAuthException exception) {
            safeError(exchange, 400, exception.getMessage());
        } catch (AiProxy.AiException exception) {
            safeError(exchange, exception.status(), exception.getMessage());
        } catch (SyncStore.ConflictException exception) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "The encrypted workspace changed on another device.");
            payload.put("revision", exception.revision());
            if (exception.current() != null) {
                payload.put("current", Map.of(
                        "envelope", exception.current().envelope(),
                        "revision", exception.current().revision(),
                        "updatedAt", exception.current().updatedAt()));
            }
            HttpUtil.sendJson(exchange, 409, payload);
        } catch (IllegalArgumentException exception) {
            safeError(exchange, 400, exception.getMessage());
        } catch (ResponseAlreadySent ignored) {
            // The route wrote a 405/429 response before unwinding.
        } catch (Exception exception) {
            System.err.println("[api] " + exchange.getRequestMethod() + " " + path + " failed: "
                    + exception.getClass().getSimpleName() + ": " + sanitize(exception.getMessage()));
            safeError(exchange, 500, "An internal server error occurred.");
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/health")) {
            requireMethod(exchange, "GET");
            Map<String, Object> health = new LinkedHashMap<>();
            health.put("status", "ok");
            health.put("version", VERSION);
            health.put("time", Instant.now().toString());
            health.put("aiProviders", List.of("deepseek", "openai", "gemini", "groq", "ollama", "custom"));
            HttpUtil.sendJson(exchange, 200, health);
            return;
        }
        if (path.equals("/api/auth/providers")) {
            requireMethod(exchange, "GET");
            HttpUtil.sendJson(exchange, 200, Map.of("providers", oauth.providerStatuses()));
            return;
        }
        if (path.equals("/api/auth/register")) {
            requireMethod(exchange, "POST");
            requireRate(authLimiter, exchange, "auth");
            Map<String, Object> body = HttpUtil.readObject(exchange, MAX_AUTH_BODY);
            AccountStore.AuthResult result = accounts.register(
                    string(body.get("email")), string(body.get("password")), string(body.get("name")));
            setSessionCookie(exchange, result);
            HttpUtil.sendJson(exchange, 201, Map.of("user", AccountStore.view(result.user())));
            return;
        }
        if (path.equals("/api/auth/login")) {
            requireMethod(exchange, "POST");
            requireRate(authLimiter, exchange, "auth");
            Map<String, Object> body = HttpUtil.readObject(exchange, MAX_AUTH_BODY);
            AccountStore.AuthResult result = accounts.login(string(body.get("email")), string(body.get("password")));
            setSessionCookie(exchange, result);
            HttpUtil.sendJson(exchange, 200, Map.of("user", AccountStore.view(result.user())));
            return;
        }
        if (path.equals("/api/auth/logout")) {
            requireMethod(exchange, "POST");
            accounts.logout(HttpUtil.sessionToken(exchange));
            clearSessionCookie(exchange);
            HttpUtil.sendJson(exchange, 200, Map.of("ok", true));
            return;
        }
        if (path.equals("/api/auth/me")) {
            requireMethod(exchange, "GET");
            Optional<AccountStore.User> user = currentUser(exchange);
            if (user.isEmpty()) {
                HttpUtil.sendError(exchange, 401, "Not signed in.");
                return;
            }
            HttpUtil.sendJson(exchange, 200, Map.of("user", AccountStore.view(user.get())));
            return;
        }
        if (path.startsWith("/api/auth/oauth/")) {
            handleOAuth(exchange, path);
            return;
        }
        if (path.equals("/api/account") || path.equals("/api/account/delete")) {
            if (!HttpUtil.methodIs(exchange, "DELETE") && !HttpUtil.methodIs(exchange, "POST")) {
                requireMethod(exchange, "DELETE");
            }
            handleDeleteAccount(exchange);
            return;
        }
        if (path.equals("/api/sync/envelope")) {
            handleSync(exchange);
            return;
        }
        if (path.equals("/api/ai/chat")) {
            requireMethod(exchange, "POST");
            requireRate(aiLimiter, exchange, "ai");
            handleAi(exchange);
            return;
        }
        if (path.equals("/api") || path.equals("/api/")) {
            HttpUtil.sendJson(exchange, 200, Map.of("name", "MOMENTUM API", "version", VERSION));
            return;
        }
        HttpUtil.sendError(exchange, 404, "API route not found.");
    }

    private void handleOAuth(HttpExchange exchange, String path) throws IOException {
        String prefix = "/api/auth/oauth/";
        String rest = path.substring(prefix.length());
        boolean callback = rest.endsWith("/callback");
        String provider = (callback ? rest.substring(0, rest.length() - "/callback".length()) : rest).toLowerCase(Locale.ROOT);
        if (!Set.of("google", "apple", "github", "discord").contains(provider)) {
            HttpUtil.sendError(exchange, 404, "Unknown sign-in provider.");
            return;
        }
        if (!callback) {
            requireMethod(exchange, "GET");
            try {
                String url = oauth.authorizationUrl(provider);
                HttpUtil.redirect(exchange, url);
            } catch (OAuthService.OAuthException exception) {
                HttpUtil.sendError(exchange, 503, exception.getMessage());
            }
            return;
        }
        requireRate(authLimiter, exchange, "oauth");
        Map<String, String> params;
        if (HttpUtil.methodIs(exchange, "POST")) {
            String contentType = HttpUtil.header(exchange, "Content-Type").orElse("");
            if (!contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
                redirectAuthError(exchange, "Sign-in callback used an unsupported format.");
                return;
            }
            params = HttpUtil.parseForm(HttpUtil.readText(exchange, 64 * 1024));
        } else if (HttpUtil.methodIs(exchange, "GET")) {
            params = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery()).entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().isEmpty() ? "" : entry.getValue().getLast(),
                            (left, right) -> right,
                            LinkedHashMap::new));
        } else {
            requireMethod(exchange, "GET");
            return;
        }
        try {
            AccountStore.AuthResult result = oauth.finish(provider, params);
            setSessionCookie(exchange, result);
            HttpUtil.redirect(exchange, appendQuery(appUrl, "auth", "success", "provider", provider));
        } catch (OAuthService.OAuthException | AccountStore.AuthException exception) {
            redirectAuthError(exchange, exception.getMessage());
        }
    }

    private void handleDeleteAccount(HttpExchange exchange) throws IOException {
        AccountStore.User user = requireUser(exchange);
        Map<String, Object> body;
        try {
            body = HttpUtil.readObject(exchange, 32 * 1024);
        } catch (HttpUtil.BadRequestException exception) {
            body = Map.of();
        }
        if (user.passwordHash() != null) {
            String password = string(body.get("password"));
            if (password == null || !PasswordHasher.verify(password, user.passwordHash())) {
                throw new AccountStore.AuthException("Password is required to delete this account.", 401);
            }
        }
        sync.delete(user.id());
        accounts.deleteUser(user.id());
        clearSessionCookie(exchange);
        HttpUtil.sendJson(exchange, 200, Map.of("ok", true));
    }

    private void handleSync(HttpExchange exchange) throws IOException {
        AccountStore.User user = requireUser(exchange);
        requireRate(syncLimiter, exchange, "sync");
        if (HttpUtil.methodIs(exchange, "GET")) {
            Optional<SyncStore.Snapshot> snapshot = sync.get(user.id());
            if (snapshot.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("envelope", null);
                empty.put("revision", 0);
                empty.put("updatedAt", null);
                HttpUtil.sendJson(exchange, 200, empty);
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("revision", snapshot.get().revision());
            body.put("updatedAt", snapshot.get().updatedAt());
            body.put("envelope", snapshot.get().envelope());
            HttpUtil.sendJson(exchange, 200, body);
            return;
        }
        if (HttpUtil.methodIs(exchange, "PUT")) {
            Map<String, Object> body = HttpUtil.readObject(exchange, MAX_SYNC_BODY);
            String envelope = string(body.get("envelope"));
            Object revisionValue = body.get("baseRevision");
            long baseRevision = revisionValue instanceof Number number ? number.longValue() : -1;
            if (baseRevision < 0) throw new IllegalArgumentException("baseRevision is required.");
            SyncStore.Snapshot snapshot = sync.put(user.id(), envelope, baseRevision);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("revision", snapshot.revision());
            response.put("updatedAt", snapshot.updatedAt());
            HttpUtil.sendJson(exchange, 200, response);
            return;
        }
        if (HttpUtil.methodIs(exchange, "DELETE")) {
            sync.delete(user.id());
            HttpUtil.sendJson(exchange, 200, Map.of("ok", true));
            return;
        }
        requireMethod(exchange, "GET");
    }

    private void handleAi(HttpExchange exchange) throws IOException {
        Map<String, Object> body = HttpUtil.readObject(exchange, MAX_AI_BODY);
        String provider = string(body.get("provider"));
        if (provider == null) throw new IllegalArgumentException("AI provider is required.");
        provider = provider.toLowerCase(Locale.ROOT);
        String apiKey = string(body.get("apiKey"));
        String model = string(body.get("model"));
        String baseUrl = string(body.get("baseUrl"));
        boolean json = Boolean.TRUE.equals(body.get("json"));
        Object messagesValue = body.get("messages");
        if (!(messagesValue instanceof List<?> rawMessages)) throw new IllegalArgumentException("Messages are required.");
        List<Map<String, String>> messages = new ArrayList<>();
        for (Object item : rawMessages) {
            if (!(item instanceof Map<?, ?> map)) throw new IllegalArgumentException("Invalid message.");
            String role = string(map.get("role"));
            String content = string(map.get("content"));
            if (role == null || content == null) throw new IllegalArgumentException("Invalid message.");
            Map<String, String> message = new LinkedHashMap<>();
            message.put("role", role);
            message.put("content", content);
            messages.add(message);
        }
        boolean serverKeyAllowed = currentUser(exchange).isPresent()
                || (allowLocalAiKey && HttpUtil.isLoopback(exchange));
        AiProxy.ChatResponse response = ai.chat(
                new AiProxy.ChatRequest(provider, apiKey, model, baseUrl, messages, json), serverKeyAllowed);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", response.text());
        payload.put("provider", response.provider());
        payload.put("model", response.model());
        if (response.usage() != null) payload.put("usage", response.usage());
        HttpUtil.sendJson(exchange, 200, payload);
    }

    private Optional<AccountStore.User> currentUser(HttpExchange exchange) {
        return accounts.sessionUser(HttpUtil.sessionToken(exchange));
    }

    private AccountStore.User requireUser(HttpExchange exchange) {
        return currentUser(exchange).orElseThrow(() -> new AccountStore.AuthException("Sign in to use this feature.", 401));
    }

    private void setSessionCookie(HttpExchange exchange, AccountStore.AuthResult result) {
        long seconds = Duration.between(Instant.now(), result.expiresAt()).toSeconds();
        exchange.getResponseHeaders().add("Set-Cookie",
                HttpUtil.sessionCookie(result.token(), (int) Math.max(60, seconds), secureCookies));
    }

    private void clearSessionCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Set-Cookie", HttpUtil.sessionCookie("", 0, secureCookies));
    }

    private void requireRate(RateLimiter limiter, HttpExchange exchange, String bucket) throws IOException {
        if (!limiter.allow(HttpUtil.clientIp(exchange) + "|" + bucket)) {
            exchange.getResponseHeaders().set("Retry-After", "60");
            HttpUtil.sendError(exchange, 429, "Too many requests. Wait a moment and try again.");
            throw new ResponseAlreadySent();
        }
    }

    private void requireMethod(HttpExchange exchange, String method) throws IOException {
        if (!HttpUtil.methodIs(exchange, method)) {
            HttpUtil.sendError(exchange, 405, "Method not allowed.");
            throw new ResponseAlreadySent();
        }
    }

    private void applyCors(HttpExchange exchange) {
        String origin = HttpUtil.header(exchange, "Origin").orElse(null);
        if (origin != null && allowedOrigins.contains(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept");
            exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
            exchange.getResponseHeaders().add("Vary", "Origin");
        }
    }

    private boolean originAllowedForMutation(HttpExchange exchange) {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS")) return true;
        String origin = HttpUtil.header(exchange, "Origin").orElse(null);
        if (origin == null) return true;
        return allowedOrigins.contains(origin);
    }

    private void safeError(HttpExchange exchange, int status, String message) {
        try {
            HttpUtil.sendError(exchange, status, message);
        } catch (IOException ignored) {
            // Client disconnected.
        }
    }

    private void redirectAuthError(HttpExchange exchange, String message) throws IOException {
        HttpUtil.redirect(exchange, appendQuery(appUrl, "auth", "error", "message", message == null ? "Sign-in failed." : message));
    }

    private Set<String> buildAllowedOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        origins.add("http://127.0.0.1:5173");
        origins.add("http://localhost:5173");
        origins.add(publicBase);
        String configured = System.getenv("MOMENTUM_ALLOWED_ORIGINS");
        if (configured != null && !configured.isBlank()) {
            for (String origin : configured.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) origins.add(stripTrailingSlash(trimmed));
            }
        }
        return origins;
    }

    private String configuredProviders() {
        List<String> configured = new ArrayList<>();
        for (Map<String, Object> provider : oauth.providerStatuses()) {
            if (Boolean.TRUE.equals(provider.get("configured"))) configured.add(String.valueOf(provider.get("label")));
        }
        return configured.isEmpty() ? "email/password only" : String.join(", ", configured);
    }

    private static String appendQuery(String base, String... pairs) {
        StringBuilder url = new StringBuilder(base);
        url.append(base.contains("?") ? '&' : '?');
        for (int index = 0; index < pairs.length; index += 2) {
            if (index > 0) url.append('&');
            url.append(HttpUtil.encode(pairs[index])).append('=').append(HttpUtil.encode(pairs[index + 1]));
        }
        return url.toString();
    }

    private static String string(Object value) {
        return value instanceof String string ? string : null;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\p{Cntrl}]", " ").substring(0, Math.min(240, value.length()));
    }

    public static void main(String[] args) throws Exception {
        MomentumServer server = new MomentumServer();
        server.start();
    }

    /** Internal control-flow marker for responses already written. */
    private static final class ResponseAlreadySent extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private ResponseAlreadySent() {
            super(null, null, false, false);
        }
    }
}
