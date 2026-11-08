package dev.momentum.desktop.host;

import dev.momentum.ai.AiProxy;
import dev.momentum.auth.AccountStore;
import dev.momentum.auth.OAuthService;
import dev.momentum.data.MigrationManager;
import dev.momentum.data.SqliteRepository;
import dev.momentum.data.Database;
import dev.momentum.sync.SyncStore;
import dev.momentum.MomentumService;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

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
        String override = System.getenv("MOMENTUM_DATA_DIR");
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

    public ServiceHost(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.accounts = new AccountStore(this.dataDirectory);
        this.oauth = new OAuthService(accounts);
        this.sync = new SyncStore(this.dataDirectory);
        this.ai = new AiProxy();
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

    @Override
    public void close() {
        // Stateless; MigrationManager keeps no live connection.
    }
}
