package dev.momentum.ai;

import dev.momentum.core.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Provider-neutral AI proxy. Cloud calls are made server-side so user keys never
 * travel to any third party from the browser. Keys are never logged.
 */
public final class AiProxy {
    private static final Pattern MODEL = Pattern.compile("^[A-Za-z0-9._:/-]{1,160}$");
    private static final Set<String> PROVIDERS = Set.of("deepseek", "openai", "gemini", "groq", "ollama", "custom");
    private static final int MAX_MESSAGES = 24;
    private static final int MAX_TOTAL_CHARS = 60_000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    public record ChatRequest(
            String provider,
            String apiKey,
            String model,
            String baseUrl,
            List<Map<String, String>> messages,
            boolean json) {}

    public record ChatResponse(String text, String provider, String model, String usage) {}

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final boolean allowCustom;
    private final Set<String> customHosts;
    private final String configuredOllamaUrl;
    private final Set<String> allowedProviders;

    public AiProxy() {
        this.allowCustom = "true".equalsIgnoreCase(env("MOMENTUM_ALLOW_CUSTOM_AI", "false"));
        String hosts = env("MOMENTUM_CUSTOM_AI_HOSTS", "");
        this.customHosts = Set.of(hosts.isBlank() ? new String[0] : hosts.trim().split("\\s*,\\s*"));
        String ollamaUrl = env("MOMENTUM_OLLAMA_URL", "");
        this.configuredOllamaUrl = ollamaUrl.isBlank() ? null : normalizeBase(ollamaUrl);
        String configuredProviders = env("MOMENTUM_AI_PROVIDERS", "");
        this.allowedProviders = configuredProviders.isBlank()
                ? PROVIDERS
                : Set.of(configuredProviders.toLowerCase(Locale.ROOT).trim().split("\\s*,\\s*"));
    }

    public ChatResponse chat(ChatRequest request, boolean serverKeyAllowed) {
        if (!PROVIDERS.contains(request.provider())) throw new AiException(400, "Unsupported AI provider.");
        if (!allowedProviders.contains(request.provider())) {
            throw new AiException(400, "This MOMENTUM installation is configured for "
                    + String.join(", ", allowedProviders) + " only.");
        }
        if (request.messages().isEmpty()) throw new AiException(400, "At least one message is required.");
        if (request.messages().size() > MAX_MESSAGES) throw new AiException(400, "Conversation is too long.");
        int total = 0;
        for (Map<String, String> message : request.messages()) {
            String role = message.get("role");
            String content = message.get("content");
            if (!Set.of("system", "user", "assistant").contains(role)) {
                throw new AiException(400, "Invalid message role.");
            }
            if (content == null || content.isBlank()) throw new AiException(400, "Message content is required.");
            total += content.length();
        }
        if (total > MAX_TOTAL_CHARS) throw new AiException(400, "Conversation is too long.");

        String model = resolveModel(request);
        URI base = validateBaseUrl(request.provider(), request.baseUrl());
        String apiKey = resolveKey(request.provider(), request.apiKey(), serverKeyAllowed);
        if (!"ollama".equals(request.provider()) && (apiKey == null || apiKey.isBlank())) {
            throw new AiException(400, "Add a " + label(request.provider()) + " API key in Settings, or sign in if the server provides one.");
        }
        return switch (request.provider()) {
            case "gemini" -> callGemini(base, apiKey, model, request);
            case "ollama" -> callOllama(base, model, request);
            default -> callOpenAiCompatible(base, apiKey, model, request);
        };
    }

    private ChatResponse callOpenAiCompatible(URI base, String apiKey, String model, ChatRequest request) {
        URI endpoint = endpoint(base, "/chat/completions");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", request.messages());
        body.put("temperature", 0.35);
        body.put("stream", false);
        if (request.json()) body.put("response_format", Map.of("type", "json_object"));
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(75))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                .build();
        Map<String, Object> response = send(httpRequest, request.provider());
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw providerError(response, request.provider());
        }
        Object first = choices.getFirst();
        if (!(first instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw providerError(response, request.provider());
        }
        String content = string(message.get("content"));
        if (content == null || content.isBlank()) content = string(message.get("reasoning_content"));
        if (content == null || content.isBlank()) throw new AiException(502, "The AI provider returned an empty response.");
        String usage = response.get("usage") == null ? null : Json.stringify(response.get("usage"));
        return new ChatResponse(content, request.provider(), model, usage);
    }

    private ChatResponse callGemini(URI base, String apiKey, String model, ChatRequest request) {
        if (model.contains("/") || model.contains("..")) {
            throw new AiException(400, "Gemini model name is invalid.");
        }
        String basePath = base.toString();
        while (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
        URI endpoint = URI.create(basePath + "/models/" + model + ":generateContent?key=" + dev.momentum.core.HttpUtil.encode(apiKey));
        List<Object> contents = new ArrayList<>();
        String system = null;
        for (Map<String, String> message : request.messages()) {
            if ("system".equals(message.get("role"))) {
                system = system == null ? message.get("content") : system + "\n\n" + message.get("content");
            } else {
                contents.add(Map.of(
                        "role", "assistant".equals(message.get("role")) ? "model" : "user",
                        "parts", List.of(Map.of("text", message.get("content")))));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", contents);
        if (system != null) body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("temperature", 0.35);
        generation.put("maxOutputTokens", 8192);
        if (request.json()) generation.put("responseMimeType", "application/json");
        body.put("generationConfig", generation);
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(75))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                .build();
        Map<String, Object> response = send(httpRequest, "gemini");
        Object candidatesValue = response.get("candidates");
        if (!(candidatesValue instanceof List<?> candidates) || candidates.isEmpty()) {
            throw providerError(response, "gemini");
        }
        if (!(candidates.getFirst() instanceof Map<?, ?> candidate)
                || !(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)) {
            throw new AiException(502, "Gemini returned an incomplete response.");
        }
        StringBuilder text = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Map<?, ?> map && map.get("text") instanceof String piece) text.append(piece);
        }
        if (text.isEmpty()) throw new AiException(502, "Gemini returned an empty response.");
        String usage = response.get("usageMetadata") == null ? null : Json.stringify(response.get("usageMetadata"));
        return new ChatResponse(text.toString(), "gemini", model, usage);
    }

    private ChatResponse callOllama(URI base, String model, ChatRequest request) {
        String basePath = base.toString();
        while (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
        URI endpoint = URI.create(basePath + "/api/chat");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", request.messages());
        body.put("stream", false);
        body.put("options", Map.of("temperature", 0.35, "num_predict", 1800));
        if (request.json()) body.put("format", "json");
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(360))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                .build();
        Map<String, Object> response = send(httpRequest, "ollama");
        if (!(response.get("message") instanceof Map<?, ?> message)
                || !(message.get("content") instanceof String content)
                || content.isBlank()) {
            throw providerError(response, "ollama");
        }
        return new ChatResponse(content, "ollama", model, null);
    }

    private Map<String, Object> send(HttpRequest request, String provider) {
        try {
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] bodyBytes;
            try (java.io.InputStream stream = response.body()) {
                bodyBytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (bodyBytes.length > MAX_RESPONSE_BYTES) {
                throw new AiException(502, "The AI provider returned an unexpectedly large response.");
            }
            String body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = "";
                try {
                    Object parsed = Json.parse(body);
                    if (parsed instanceof Map<?, ?> map && map.get("error") instanceof Map<?, ?> error) {
                        detail = string(error.get("message"));
                    } else if (parsed instanceof Map<?, ?> map && map.get("error") instanceof String string) {
                        detail = string;
                    }
                } catch (Exception ignored) {
                    // Provider error bodies are not guaranteed to be JSON.
                }
                int status = response.statusCode() == 401 || response.statusCode() == 403 ? 401 : 502;
                String message = status == 401
                        ? "The AI provider rejected the API key."
                        : "The AI provider returned an error." + (detail == null || detail.isBlank() ? "" : " " + truncate(detail, 220));
                throw new AiException(status, message);
            }
            return Json.parseObject(body);
        } catch (AiException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new AiException(504, "The AI provider timed out. Try again or choose another model.");
        } catch (Exception exception) {
            throw new AiException(502, "Could not reach the AI provider. Check your connection and provider settings.");
        }
    }

    private AiException providerError(Map<String, Object> response, String provider) {
        Object error = response.get("error");
        String detail = null;
        if (error instanceof Map<?, ?> map) detail = string(map.get("message"));
        if (detail == null) detail = "The " + label(provider) + " provider returned an invalid response.";
        return new AiException(502, truncate(detail, 260));
    }

    private String resolveModel(ChatRequest request) {
        String requested = request.model() == null ? "" : request.model().trim();
        String model = requested.isEmpty() ? switch (request.provider()) {
            case "deepseek" -> "deepseek-chat";
            case "openai" -> "gpt-4o-mini";
            case "gemini" -> "gemini-2.0-flash";
            case "groq" -> "openai/gpt-oss-120b";
            case "ollama" -> "llama3.2";
            default -> "";
        } : requested;
        if (!MODEL.matcher(model).matches()) throw new AiException(400, "Model name contains invalid characters.");
        return model;
    }

    private String resolveKey(String provider, String supplied, boolean serverKeyAllowed) {
        if (supplied != null && !supplied.isBlank()) {
            if (supplied.length() > 500) throw new AiException(400, "API key is too long.");
            return supplied.trim();
        }
        if (!serverKeyAllowed) return null;
        return switch (provider) {
            case "deepseek" -> env("DEEPSEEK_API_KEY", "");
            case "openai" -> env("OPENAI_API_KEY", "");
            case "gemini" -> env("GEMINI_API_KEY", env("GOOGLE_AI_API_KEY", ""));
            case "groq" -> env("GROQ_API_KEY", "");
            default -> "";
        };
    }

    private URI validateBaseUrl(String provider, String raw) {
        String fallback = switch (provider) {
            case "deepseek" -> "https://api.deepseek.com/v1";
            case "openai" -> "https://api.openai.com/v1";
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta";
            case "groq" -> "https://api.groq.com/openai/v1";
            case "ollama" -> "http://127.0.0.1:11434";
            default -> "";
        };
        String candidate = raw == null || raw.isBlank() ? fallback : raw.trim();
        if (candidate.isBlank()) throw new AiException(400, "A provider base URL is required.");
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException exception) {
            throw new AiException(400, "Provider base URL is invalid.");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new AiException(400, "Provider base URL is invalid.");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        switch (provider) {
            case "deepseek" -> require(uri, host, "https", "api.deepseek.com");
            case "openai" -> require(uri, host, "https", "api.openai.com");
            case "groq" -> require(uri, host, "https", "api.groq.com");
            case "gemini" -> require(uri, host, "https", "generativelanguage.googleapis.com");
            case "ollama" -> {
                boolean loopback = host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1");
                boolean defaultLocal = loopback
                        && "http".equalsIgnoreCase(uri.getScheme())
                        && (uri.getPort() == -1 || uri.getPort() == 11434);
                boolean operatorConfigured = configuredOllamaUrl != null
                        && configuredOllamaUrl.equalsIgnoreCase(normalizeBase(candidate));
                if (!defaultLocal && !operatorConfigured) {
                    throw new AiException(400, "Ollama must run at localhost:11434, or match the server's configured Ollama URL.");
                }
            }
            case "custom" -> {
                if (!allowCustom) throw new AiException(400, "Custom providers are disabled on this server.");
                if (!customHosts.contains(host)) throw new AiException(400, "This custom provider host is not configured on the server.");
                if (!"https".equalsIgnoreCase(uri.getScheme())) throw new AiException(400, "Custom providers must use HTTPS.");
            }
            default -> throw new AiException(400, "Unsupported AI provider.");
        }
        return uri;
    }

    private void require(URI uri, String host, String scheme, String allowedHost) {
        if (!scheme.equalsIgnoreCase(uri.getScheme()) || !allowedHost.equals(host)) {
            throw new AiException(400, "Provider base URL does not match " + allowedHost + ".");
        }
    }

    private URI endpoint(URI base, String suffix) {
        String value = base.toString();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("/chat/completions")) return URI.create(value);
        return URI.create(value + suffix);
    }

    private static String label(String provider) {
        return switch (provider) {
            case "deepseek" -> "DeepSeek";
            case "openai" -> "OpenAI";
            case "gemini" -> "Gemini";
            case "groq" -> "Groq";
            case "ollama" -> "Ollama";
            default -> "custom";
        };
    }

    private static String normalizeBase(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "…";
    }

    private static String string(Object value) {
        return value instanceof String string ? string : null;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static final class AiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;

        public AiException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
