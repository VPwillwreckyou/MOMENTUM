package dev.momentum.auth;

import dev.momentum.core.HttpUtil;
import dev.momentum.core.Json;
import dev.momentum.core.Tokens;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth 2.0 authorization-code flow with PKCE for Google, GitHub and
 * Discord. Credentials are read only from server environment variables.
 */
public final class OAuthService {
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);
    private static final String USER_AGENT = "MOMENTUM/1.0";

    private record OAuthState(String provider, String verifier, Instant createdAt) {}

    private record Profile(String provider, String subject, String email, String name, boolean verified) {}

    private final AccountStore accounts;
    private final Map<String, OAuthState> states = new LinkedHashMap<>();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String publicBase;
    private final String appUrl;

    public OAuthService(AccountStore accounts) {
        this.accounts = accounts;
        this.publicBase = stripTrailingSlash(env("MOMENTUM_PUBLIC_URL", "http://127.0.0.1:8787"));
        String configuredAppUrl = env("MOMENTUM_APP_URL", "");
        this.appUrl = stripTrailingSlash(configuredAppUrl.isBlank() ? publicBase : configuredAppUrl);
    }

    public String appUrl() {
        return appUrl;
    }

    public boolean configured(String provider) {
        return switch (provider) {
            case "google" -> both("GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET");
            case "github" -> both("GITHUB_CLIENT_ID", "GITHUB_CLIENT_SECRET");
            case "discord" -> both("DISCORD_CLIENT_ID", "DISCORD_CLIENT_SECRET");
            default -> false;
        };
    }

    public List<Map<String, Object>> providerStatuses() {
        List<Map<String, Object>> providers = new ArrayList<>();
        providers.add(status("google", "Google"));
        providers.add(status("github", "GitHub"));
        providers.add(status("discord", "Discord"));
        return providers;
    }

    public String authorizationUrl(String provider) {
        if (!configured(provider)) throw new OAuthException("This sign-in method is not configured on the server.");
        String state = Tokens.randomToken(24);
        String verifier = Tokens.randomToken(48);
        String challenge = pkceChallenge(verifier);
        synchronized (states) {
            cleanupStates();
            states.put(state, new OAuthState(provider, verifier, Instant.now()));
        }
        String redirect = redirectUri(provider);
        return switch (provider) {
            case "google" -> "https://accounts.google.com/o/oauth2/v2/auth?"
                    + "client_id=" + enc(clientId(provider))
                    + "&redirect_uri=" + enc(redirect)
                    + "&response_type=code&scope=" + enc("openid email profile")
                    + "&state=" + enc(state)
                    + "&code_challenge=" + enc(challenge)
                    + "&code_challenge_method=S256&prompt=select_account";
            case "github" -> "https://github.com/login/oauth/authorize?"
                    + "client_id=" + enc(clientId(provider))
                    + "&redirect_uri=" + enc(redirect)
                    + "&response_type=code&scope=" + enc("read:user user:email")
                    + "&state=" + enc(state)
                    + "&code_challenge=" + enc(challenge)
                    + "&code_challenge_method=S256";
            case "discord" -> "https://discord.com/oauth2/authorize?"
                    + "client_id=" + enc(clientId(provider))
                    + "&redirect_uri=" + enc(redirect)
                    + "&response_type=code&scope=" + enc("identify email")
                    + "&state=" + enc(state)
                    + "&code_challenge=" + enc(challenge)
                    + "&code_challenge_method=S256&prompt=consent";
            default -> throw new OAuthException("Unsupported sign-in method.");
        };
    }

    public AccountStore.AuthResult finish(String provider, Map<String, String> params) {
        String error = params.get("error");
        if (error != null) throw new OAuthException("Sign-in was cancelled or denied.");
        String stateValue = params.get("state");
        String code = params.get("code");
        if (stateValue == null || code == null || code.isBlank()) throw new OAuthException("Sign-in response is incomplete.");
        OAuthState state;
        synchronized (states) {
            cleanupStates();
            state = states.remove(stateValue);
        }
        if (state == null || !state.provider().equals(provider)
                || Duration.between(state.createdAt(), Instant.now()).compareTo(STATE_LIFETIME) > 0) {
            throw new OAuthException("Sign-in session expired. Please try again.");
        }
        Profile profile = switch (provider) {
            case "google" -> googleProfile(code, state.verifier());
            case "github" -> githubProfile(code, state.verifier());
            case "discord" -> discordProfile(code, state.verifier());
            default -> throw new OAuthException("Unsupported sign-in method.");
        };
        return accounts.oauth(profile.provider(), profile.subject(), profile.email(), profile.name(), profile.verified());
    }

    private Map<String, Object> status(String id, String label) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("label", label);
        map.put("configured", configured(id));
        return map;
    }

    private Profile googleProfile(String code, String verifier) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId("google"));
        form.put("client_secret", clientSecret("google"));
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", redirectUri("google"));
        form.put("code_verifier", verifier);
        Map<String, Object> token = postForm("https://oauth2.googleapis.com/token", form);
        String accessToken = text(token.get("access_token"));
        if (accessToken == null) throw new OAuthException("Google did not return an access token.");
        Map<String, Object> profile = getJson("https://www.googleapis.com/oauth2/v2/userinfo", accessToken);
        String subject = text(profile.get("id"));
        String email = text(profile.get("email"));
        String name = firstNonBlank(text(profile.get("name")), text(profile.get("given_name")), email);
        boolean verified = Boolean.TRUE.equals(profile.get("verified_email"));
        return requireProfile("Google", subject, email, name, verified);
    }

    private Profile githubProfile(String code, String verifier) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId("github"));
        form.put("client_secret", clientSecret("github"));
        form.put("code", code);
        form.put("redirect_uri", redirectUri("github"));
        form.put("code_verifier", verifier);
        Map<String, Object> token = postForm("https://github.com/login/oauth/access_token", form);
        String accessToken = text(token.get("access_token"));
        if (accessToken == null) throw new OAuthException("GitHub did not return an access token.");
        Map<String, Object> profile = getJson("https://api.github.com/user", accessToken);
        String subject = profile.get("id") == null ? null : String.valueOf(profile.get("id"));
        String publicEmail = text(profile.get("email"));
        String email = publicEmail;
        boolean verified = false;
        // Always consult the verified-email endpoint. Never trust a public profile
        // email for account linking without a verified flag.
        List<Object> emails = getJsonArray("https://api.github.com/user/emails", accessToken);
        for (Object item : emails) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) raw;
            String candidate = text(entry.get("email"));
            if (candidate == null || !Boolean.TRUE.equals(entry.get("verified"))) continue;
            boolean primary = Boolean.TRUE.equals(entry.get("primary"));
            if (email == null || candidate.equalsIgnoreCase(email) || primary) {
                email = candidate;
                verified = true;
            }
            if (primary) break;
        }
        String name = firstNonBlank(text(profile.get("name")), text(profile.get("login")), email);
        return requireProfile("GitHub", subject, email, name, verified);
    }

    private Profile discordProfile(String code, String verifier) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId("discord"));
        form.put("client_secret", clientSecret("discord"));
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", redirectUri("discord"));
        form.put("code_verifier", verifier);
        Map<String, Object> token = postForm("https://discord.com/api/oauth2/token", form);
        String accessToken = text(token.get("access_token"));
        if (accessToken == null) throw new OAuthException("Discord did not return an access token.");
        Map<String, Object> profile = getJson("https://discord.com/api/users/@me", accessToken);
        String subject = text(profile.get("id"));
        String email = text(profile.get("email"));
        boolean verified = Boolean.TRUE.equals(profile.get("verified"));
        String name = firstNonBlank(text(profile.get("global_name")), text(profile.get("username")), email);
        return requireProfile("Discord", subject, email, name, verified);
    }

    private Profile requireProfile(String provider, String subject, String email, String name, boolean verified) {
        if (subject == null || subject.isBlank()) throw new OAuthException(provider + " did not return an account identifier.");
        return new Profile(provider.toLowerCase(Locale.ROOT), subject, email, name, verified);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(String url, Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OAuthException("The identity provider rejected the sign-in request.");
            }
            return Json.parseObject(response.body());
        } catch (OAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OAuthException("Could not reach the identity provider.");
        }
    }

    private Map<String, Object> getJson(String url, String accessToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET();
        if (accessToken != null) builder.header("Authorization", "Bearer " + accessToken);
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OAuthException("The identity provider profile request failed.");
            }
            return Json.parseObject(response.body());
        } catch (OAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OAuthException("Could not read the identity provider profile.");
        }
    }

    private List<Object> getJsonArray(String url, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OAuthException("The identity provider email request failed.");
            }
            Object parsed = Json.parse(response.body());
            if (!(parsed instanceof List<?> list)) return List.of();
            return new ArrayList<>(list);
        } catch (OAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OAuthException("Could not read the identity provider email list.");
        }
    }

    private void cleanupStates() {
        Instant cutoff = Instant.now().minus(STATE_LIFETIME);
        states.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    private String redirectUri(String provider) {
        return publicBase + "/api/auth/oauth/" + provider + "/callback";
    }

    private String clientId(String provider) {
        return env(provider.toUpperCase(Locale.ROOT) + "_CLIENT_ID", "");
    }

    private String clientSecret(String provider) {
        return env(provider.toUpperCase(Locale.ROOT) + "_CLIENT_SECRET", "");
    }

    private static boolean both(String first, String second) {
        return !env(first, "").isBlank() && !env(second, "").isBlank();
    }

    private static String pkceChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("PKCE is unavailable.", exception);
        }
    }

    private static String encodeForm(Map<String, String> form) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!builder.isEmpty()) builder.append('&');
            builder.append(HttpUtil.encode(entry.getKey())).append('=').append(HttpUtil.encode(entry.getValue()));
        }
        return builder.toString();
    }

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String enc(String value) {
        return HttpUtil.encode(value);
    }

    private static String text(Object value) {
        return value instanceof String string ? string : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static final class OAuthException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public OAuthException(String message) {
            super(message);
        }
    }
}
