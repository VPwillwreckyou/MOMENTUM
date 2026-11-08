package dev.momentum.desktop.host;

import dev.momentum.ai.AiProxy;
import dev.momentum.auth.AccountStore;
import dev.momentum.auth.OAuthService;
import dev.momentum.core.Json;
import dev.momentum.data.MigrationManager;
import dev.momentum.data.SqliteRepository;
import dev.momentum.data.Database;
import dev.momentum.desktop.security.EncryptionService;
import dev.momentum.desktop.security.EncryptionServiceProvider;
import dev.momentum.sync.SyncStore;
import dev.momentum.MomentumService;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Bridges the JavaFX desktop UI directly to the existing MOMENTUM backend:
 *
 *   MomentumService    — auth, OAuth (google/github/discord), AI proxy, sync
 *   MigrationManager   — SQLite status, IndexedDB backup import, backups
 *   SqliteRepository   — goals / tasks / milestones / categories / attachments
 *
 * Business logic lives ONLY in the service layer; this class never duplicates it.
 */
public final class ServiceHost implements AutoCloseable {

    /** Default location of all desktop data: %LOCALAPPDATA%\MOMENTUM\data on Windows. */
    public static Path defaultDataDirectory() {
        String override = System.getProperty("momentum.data.dir");
        if (override == null || override.isBlank()) {
            override = System.getenv("MOMENTUM_DATA_DIR");
        }
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "MOMENTUM", "data");
        }
        return Path.of(System.getProperty("user.home"), ".momentum", "data");
    }

    private final Path dataDirectory;
    private final AccountStore accounts;
    private final OAuthService oauth;
    private final SyncStore sync;
    private final AiProxy ai;
    private final MomentumService service;
    private final MigrationManager migrations;

    /** Current desktop session token (set after login/register, cleared on logout). */
    private volatile String currentToken;

    /** Device-bound encryption service for sensitive settings values. */
    private final EncryptionService encryptionService;

    public String getCurrentToken() {
        return currentToken;
    }

    public void setCurrentToken(String currentToken) {
        this.currentToken = currentToken;
    }

    public void clearCurrentToken() {
        this.currentToken = null;
    }

    /** Returns the encryption service used for sensitive settings values. */
    public EncryptionService encryptionService() {
        return encryptionService;
    }

    public ServiceHost(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.accounts = new AccountStore(this.dataDirectory);
        this.oauth = new OAuthService(accounts);
        this.sync = new SyncStore(this.dataDirectory);
        this.ai = new AiProxy();
        this.encryptionService = EncryptionServiceProvider.get();
        // Same configuration the HTTP server passes: desktop runs the service headlessly.
        this.service = new MomentumService(
                accounts, oauth, sync, ai,
                "http://127.0.0.1:8787", oauth.appUrl(), false, false);
        this.migrations = new MigrationManager(this.dataDirectory);
        // One automatic database backup per calendar day.
        String backup = migrations.ensureDailyBackup();
        if (backup != null) {
            System.out.println("MOMENTUM desktop: automatic database backup created: " + backup);
        }
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public MomentumService service() {
        return service;
    }

    public MigrationManager migrations() {
        return migrations;
    }

    /**
     * Runs code against a fresh SqliteRepository bound to this host's data
     * directory, commits on success and rolls back on failure. Each call opens
     * its own connection so database work is safe to run on background threads.
     */
    public <T> T withRepository(Function<SqliteRepository, T> action) {
        try (Database database = new Database(dataDirectory.toString());
             SqliteRepository repository = new SqliteRepository(database)) {
            T result = action.apply(repository);
            repository.commit();
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Database operation failed", e);
        }
    }

    /** Read a JSON settings value from the local SQLite database. */
    public Optional<String> settingsGet(String key) {
        try (Database database = new Database(dataDirectory.toString())) {
            SqliteRepository repository = new SqliteRepository(database);
            return repository.setting(key);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /** Write a JSON settings value to the local SQLite database. */
    public boolean settingsPut(String key, String json) {
        try (Database database = new Database(dataDirectory.toString())) {
            SqliteRepository repository = new SqliteRepository(database);
            repository.insertSetting(key, json);
            repository.commit();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Stores a JSON settings value with one sensitive field encrypted using
     * the device-bound encryption service. The sensitive field is encrypted
     * before the JSON is persisted, so the raw value never touches disk.
     *
     * @param key          the settings key
     * @param jsonValue    the full JSON value as a string
     * @param sensitiveField the JSON field name whose value should be encrypted
     */
    public boolean settingsPutEncrypted(String key, String jsonValue, String sensitiveField) {
        try {
            Map<String, Object> cfg = Json.parseObject(jsonValue);
            Object sensitiveValue = cfg.get(sensitiveField);
            if (sensitiveValue != null && !String.valueOf(sensitiveValue).isEmpty()) {
                String encrypted = encryptionService.encrypt(String.valueOf(sensitiveValue));
                cfg.put(sensitiveField, encrypted);
            }
            return settingsPut(key, Json.stringify(cfg));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reads a JSON settings value with one sensitive field decrypted using the
     * device-bound encryption service. If the field cannot be decrypted (e.g.
     * the database was copied to another machine), the raw stored value is
     * returned unchanged so the caller can decide how to handle it.
     *
     * @param key          the settings key
     * @param sensitiveField the JSON field name whose value should be decrypted
     * @return the decrypted JSON value, or empty if no row exists
     */
    public Optional<String> settingsGetDecrypted(String key, String sensitiveField) {
        return settingsGet(key).map(jsonValue -> {
            try {
                Map<String, Object> cfg = Json.parseObject(jsonValue);
                Object sensitiveValue = cfg.get(sensitiveField);
                if (sensitiveValue != null && !String.valueOf(sensitiveValue).isEmpty()) {
                    String decrypted = encryptionService.decrypt(String.valueOf(sensitiveValue));
                    cfg.put(sensitiveField, decrypted);
                }
                return Json.stringify(cfg);
            } catch (Exception e) {
                // If decryption fails (e.g. wrong machine), return raw value
                return jsonValue;
            }
        });
    }

    @Override
    public void close() {
        // Stateless; MigrationManager keeps no live connection.
    }
}
