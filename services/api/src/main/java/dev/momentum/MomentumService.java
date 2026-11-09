package dev.momentum;

import dev.momentum.ai.AiProxy;
import dev.momentum.auth.AccountStore;
import dev.momentum.auth.OAuthService;
import dev.momentum.sync.SyncStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service layer extracted from MomentumServer.
 *
 * Exposes all application business logic as plain Java method calls,
 * decoupled from HTTP. Both the HTTP server (MomentumServer) and the
 * JavaFX desktop client call this service directly.
 *
 * HTTP-specific concerns (routing, CORS, rate limiting, body parsing,
 * cookie handling) remain in MomentumServer.
 */
public final class MomentumService {

    private final AccountStore accounts;
    private final OAuthService oauth;
    private final SyncStore sync;
    private final AiProxy ai;

    private final String publicBase;
    private final String appUrl;
    private final boolean secureCookies;
    private final boolean allowLocalAiKey;

    public MomentumService(AccountStore accounts, OAuthService oauth, SyncStore sync, AiProxy ai,
                           String publicBase, String appUrl, boolean secureCookies, boolean allowLocalAiKey) {
        this.accounts = accounts;
        this.oauth = oauth;
        this.sync = sync;
        this.ai = ai;
        this.publicBase = stripTrailingSlash(publicBase);
        this.appUrl = stripTrailingSlash(appUrl.isBlank() ? publicBase : appUrl);
        this.secureCookies = secureCookies;
        this.allowLocalAiKey = allowLocalAiKey;
    }

    // --- Configuration ---

    public String publicBase() { return publicBase; }
    public String appUrl() { return appUrl; }
    public boolean secureCookies() { return secureCookies; }
    public boolean allowLocalAiKey() { return allowLocalAiKey; }

    public AccountStore accounts() { return accounts; }
    public OAuthService oauth() { return oauth; }
    public SyncStore sync() { return sync; }
    public AiProxy ai() { return ai; }

    // --- Health ---

    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "ok");
        health.put("version", MomentumServer.version());
        health.put("time", java.time.Instant.now().toString());
        health.put("aiProviders", List.of("deepseek", "openai", "gemini", "groq", "ollama", "custom"));
        return health;
    }

    // --- Auth: Email/Password ---

    public AccountStore.AuthResult register(String email, String password, String name) {
        return accounts.register(email, password, name);
    }

    public AccountStore.AuthResult login(String email, String password) {
        return accounts.login(email, password);
    }

    public void logout(String token) {
        accounts.logout(token);
    }

    public Optional<AccountStore.User> currentUser(String token) {
        return accounts.sessionUser(token);
    }

    public void deleteAccount(String token, String password) {
        AccountStore.User user = accounts.sessionUser(token)
                .orElseThrow(() -> new AccountStore.AuthException("Sign in to use this feature.", 401));
        if (user.passwordHash() != null) {
            if (password == null || !dev.momentum.core.PasswordHasher.verify(password, user.passwordHash())) {
                throw new AccountStore.AuthException("Password is required to delete this account.", 401);
            }
        }
        sync.delete(user.id());
        accounts.deleteUser(user.id());
    }

    /** Change the signed-in user's password. */
    public void changePassword(String token, String oldPassword, String newPassword) {
        AccountStore.User user = accounts.sessionUser(token)
                .orElseThrow(() -> new AccountStore.AuthException("Sign in to use this feature.", 401));
        accounts.changePassword(user.id(), oldPassword, newPassword);
    }

    /** Sign out all other devices for the signed-in user. */
    public void signOutAllDevices(String token) {
        AccountStore.User user = accounts.sessionUser(token)
                .orElseThrow(() -> new AccountStore.AuthException("Sign in to use this feature.", 401));
        accounts.signOutAllDevices(user.id());
    }

    // --- Auth: OAuth ---

    public List<Map<String, Object>> oauthProviders() {
        return oauth.providerStatuses();
    }

    public boolean oauthConfigured(String provider) {
        return oauth.configured(provider);
    }

    public String oauthAuthorizationUrl(String provider) {
        return oauth.authorizationUrl(provider);
    }

    public AccountStore.AuthResult oauthFinish(String provider, Map<String, String> params) {
        return oauth.finish(provider, params);
    }

    // --- AI ---

    public AiProxy.ChatResponse aiChat(AiProxy.ChatRequest request, boolean serverKeyAllowed) {
        return ai.chat(request, serverKeyAllowed);
    }

    // --- Sync ---

    public Optional<SyncStore.Snapshot> syncGet(String token) {
        AccountStore.User user = accounts.sessionUser(token)
                .orElseThrow(() -> new AccountStore.AuthException("Sign in to use this feature.", 401));
        return sync.get(user.id());
    }

    public SyncStore.Snapshot syncPut(String token, String envelope, long baseRevision) {
        AccountStore.User user = accounts.sessionUser(token)
                .orElseThrow(() -> new AccountStore.AuthException("Sign in to use this feature.", 401));
        return sync.put(user.id(), envelope, baseRevision);
    }

    public void syncDelete(String token) {
        AccountStore.User user = accounts.sessionUser(token)
                .orElseThrow(() -> new AccountStore.AuthException("Sign in to use this feature.", 401));
        sync.delete(user.id());
    }

    // --- Helpers ---

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
