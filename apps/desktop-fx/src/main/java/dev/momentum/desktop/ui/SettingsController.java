package dev.momentum.desktop.ui;

import dev.momentum.auth.AccountStore;
import dev.momentum.desktop.host.ServiceHost;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

/**
 * Settings screen: theme, account (login/logout via MomentumService), AI
 * provider configuration and sync. All values are read from / written to the
 * real SQLite settings table.
 */
public class SettingsController implements PageView {

    private final ServiceHost host;
    private final ThemeManager themes;
    private final ToastHost toasts;
    private final ModalOverlay modals;

    private final VBox root = new VBox(16);

    // Session token lives on the shared ServiceHost so Settings and Auth stay in sync.

    private final ComboBox<ThemeManager.Theme> themeCombo = new ComboBox<>();
    private final Label dbPath = new Label();
    private final Label dbIntegrity = new Label();
    private final Label dbMigration = new Label();

    private final TextField loginEmail = new TextField();
    private final PasswordField loginPassword = new PasswordField();
    private final Button loginButton = new Button("Sign in");
    private final Button logoutButton = new Button("Sign out");
    private final Label currentUserLabel = new Label("Not signed in.");

    private final ComboBox<String> aiProvider = new ComboBox<>();
    private final TextField aiModel = new TextField();
    private final TextField aiBaseUrl = new TextField();
    private final PasswordField aiApiKey = new PasswordField();
    private final Button aiTest = new Button("Test connection");
    private final Label aiStatus = new Label();

    private final CheckBox syncEnabled = new CheckBox("Enable sync");

    public SettingsController(ServiceHost host, ThemeManager themes, ToastHost toasts, ModalOverlay modals) {
        this.host = host;
        this.themes = themes;
        this.toasts = toasts;
        this.modals = modals;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), themeCard(), accountCard(), aiCard(), syncCard(), storageCard(), aboutCard());

        themeCombo.getItems().addAll(ThemeManager.PRESETS);
        themeCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ThemeManager.Theme t) { return t == null ? "" : t.name(); }
            @Override public ThemeManager.Theme fromString(String s) { return null; }
        });
        themeCombo.setValue(themes.current());
        themeCombo.setOnAction(event -> {
            ThemeManager.Theme t = themeCombo.getValue();
            if (t != null) themes.apply(t);
        });

        loginButton.getStyleClass().addAll("btn", "btn-primary");
        loginButton.setOnAction(event -> doLogin());
        logoutButton.getStyleClass().addAll("btn", "btn-ghost");
        logoutButton.setOnAction(event -> doLogout());
        logoutButton.setVisible(false);

        aiProvider.getItems().addAll("groq", "openai", "deepseek", "gemini", "ollama", "custom");
        aiProvider.setValue("groq");
        aiModel.setPromptText("Model (e.g. openai/gpt-oss-120b)");
        aiBaseUrl.setPromptText("Base URL (e.g. https://api.groq.com/openai/v1)");
        aiApiKey.setPromptText("API key");
        aiTest.getStyleClass().add("btn-ghost");
        aiTest.setOnAction(event -> testAi());

        syncEnabled.setOnAction(event -> saveSetting("sync", Map.of("enabled", syncEnabled.isSelected())));

        refreshStorage();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        refreshStorage();
        refreshAuthUI();
        loadAiConfig();
    }

    /* ---------------------------------------------------------------- layout */

    private Node header() {
        Label title = new Label("Settings");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(10, title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node themeCard() {
        Label title = new Label("Color theme");
        title.getStyleClass().add("card-title");
        Label sub = new Label("Applies instantly. Stored in your local SQLite database.");
        sub.getStyleClass().add("hero-sub");
        themeCombo.setPrefWidth(220);
        HBox row = new HBox(10, new Label("Theme:"), themeCombo);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(10, title, sub, row);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node accountCard() {
        Label title = new Label("Account");
        title.getStyleClass().add("card-title");
        Label sub = new Label("Local email/password account for this desktop installation.");
        sub.getStyleClass().add("hero-sub");

        loginEmail.setPromptText("Email");
        loginPassword.setPromptText("Password");
        loginEmail.setPrefWidth(260);
        loginPassword.setPrefWidth(220);

        HBox loginRow = new HBox(10, loginEmail, loginPassword, loginButton, logoutButton);
        loginRow.setAlignment(Pos.CENTER_LEFT);
        loginRow.getStyleClass().add("auth-row");

        currentUserLabel.getStyleClass().add("auth-user");

        VBox card = new VBox(10, title, sub, loginRow, currentUserLabel);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node aiCard() {
        Label title = new Label("AI provider (Groq)");
        title.getStyleClass().add("card-title");
        Label sub = new Label("Configure the AI provider used by the AI Planner. Keys are stored locally.");
        sub.getStyleClass().add("hero-sub");

        aiProvider.setPrefWidth(150);
        aiModel.setPrefWidth(200);
        aiBaseUrl.setPrefWidth(260);
        aiApiKey.setPrefWidth(220);

        HBox row1 = new HBox(10, new Label("Provider:"), aiProvider, new Label("Model:"), aiModel);
        row1.setAlignment(Pos.CENTER_LEFT);
        HBox row2 = new HBox(10, new Label("Base URL:"), aiBaseUrl, new Label("API key:"), aiApiKey, aiTest);
        row2.setAlignment(Pos.CENTER_LEFT);
        aiStatus.getStyleClass().add("auth-user");

        VBox card = new VBox(10, title, sub, row1, row2, aiStatus);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node syncCard() {
        Label title = new Label("Sync");
        title.getStyleClass().add("card-title");
        Label sub = new Label("Sync your goals and tasks across devices (Phase 5 capability).");
        sub.getStyleClass().add("hero-sub");
        VBox card = new VBox(10, title, sub, syncEnabled);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node storageCard() {
        Label title = new Label("Local storage");
        title.getStyleClass().add("card-title");
        dbPath.getStyleClass().add("mono-text");
        dbIntegrity.getStyleClass().add("mono-text");
        dbMigration.getStyleClass().add("mono-text");
        VBox card = new VBox(10, title, dbPath, dbIntegrity, dbMigration);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node aboutCard() {
        Label title = new Label("About this build");
        title.getStyleClass().add("card-title");
        Text info = new Text("""
            MOMENTUM Desktop v%s
            UI engine: JavaFX (native rendering, no browser, no Chromium, no WebView)
            Data engine: SQLite + the MOMENTUM service layer shared with the HTTP server
            Framework phase: 4 of the 8-phase JavaFX migration""".formatted(
                    dev.momentum.desktop.MomentumApp.VERSION));
        info.getStyleClass().add("mono-text");
        VBox card = new VBox(10, title, info);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /* ---------------------------------------------------------------- storage refresh */

    private void refreshStorage() {
        try {
            var status = host.migrations().status();
            dbPath.setText("Data directory: " + status.get("dataDirectory"));
            dbIntegrity.setText("Database integrity: " + status.get("integrityCheck")
                    + " / foreign key violations: " + status.get("foreignKeyViolations"));
            dbMigration.setText("Last migration: " + (status.get("migratedFrom") == null
                    ? "not yet imported (fresh database)"
                    : status.get("migratedFrom") + " on " + status.get("migrationDate")));
        } catch (Exception e) {
            dbPath.setText("Data directory: " + host.dataDirectory());
            dbIntegrity.setText("Database integrity: unavailable");
            dbMigration.setText("Last migration: unavailable");
        }
    }

    /* ---------------------------------------------------------------- auth */

    private void refreshAuthUI() {
        String token = host.getCurrentToken();
        boolean signedIn = token != null;
        loginEmail.setVisible(!signedIn);
        loginPassword.setVisible(!signedIn);
        loginButton.setVisible(!signedIn);
        logoutButton.setVisible(signedIn);
        if (signedIn) {
            currentUserLabel.setText("Signed in (token ends " + token.substring(Math.max(0, token.length() - 4)) + ")");
        } else {
            currentUserLabel.setText("Not signed in.");
        }
    }

    private void doLogin() {
        String email = loginEmail.getText();
        String password = loginPassword.getText();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            toasts.show("Email and password are required.", ToastHost.Tone.ERROR);
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AccountStore.AuthResult res = host.service().login(email, password);
                host.setCurrentToken(res.token());
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> {
            toasts.show("Signed in.", ToastHost.Tone.SUCCESS);
            refreshAuthUI();
        }));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Sign in failed: " + task.getException().getMessage(), ToastHost.Tone.ERROR)));
        new Thread(task, "auth-login").start();
    }

    private void doLogout() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String token = host.getCurrentToken();
                if (token != null) host.service().logout(token);
                host.clearCurrentToken();
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> {
            toasts.show("Signed out.", ToastHost.Tone.INFO);
            refreshAuthUI();
        }));
        task.setOnFailed(event -> Platform.runLater(() -> {
            refreshAuthUI();
        }));
        new Thread(task, "auth-logout").start();
    }

    /* ---------------------------------------------------------------- AI config */

    private void loadAiConfig() {
        host.settingsGet("ai_config").ifPresent(json -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cfg = dev.momentum.core.Json.parseObject(json);
                aiProvider.setValue(String.valueOf(cfg.getOrDefault("provider", "groq")));
                aiModel.setText(String.valueOf(cfg.getOrDefault("model", "")));
                aiBaseUrl.setText(String.valueOf(cfg.getOrDefault("baseUrl", "")));
                aiApiKey.setPromptText(Boolean.TRUE.equals(cfg.get("hasKey")) ? "••••••••" : "API key");
            } catch (Exception ignored) {
            }
        });
    }

    private void saveSetting(String key, Map<String, Object> value) {
        try {
            host.settingsPut(key, dev.momentum.core.Json.stringify(value));
        } catch (Exception e) {
            toasts.show("Could not save setting: " + e.getMessage(), ToastHost.Tone.ERROR);
        }
    }

    private void testAi() {
        String provider = aiProvider.getValue();
        String model = aiModel.getText();
        String baseUrl = aiBaseUrl.getText();
        String key = aiApiKey.getText();

        // If the key field is empty, try to use the stored (decrypted) key so
        // that changing only the model or base URL does not lose the key.
        if (key == null || key.isBlank()) {
            String storedJson = host.settingsGetDecrypted("ai_config", "apiKey")
                .orElse(null);
            if (storedJson != null) {
                try {
                    Map<String, Object> storedCfg = dev.momentum.core.Json.parseObject(storedJson);
                    key = String.valueOf(storedCfg.getOrDefault("apiKey", ""));
                    if (provider == null || provider.isBlank()) {
                        provider = String.valueOf(storedCfg.getOrDefault("provider", "groq"));
                        aiProvider.setValue(provider);
                    }
                    if (model == null || model.isBlank()) {
                        model = String.valueOf(storedCfg.getOrDefault("model", ""));
                        aiModel.setText(model);
                    }
                    if (baseUrl == null || baseUrl.isBlank()) {
                        baseUrl = String.valueOf(storedCfg.getOrDefault("baseUrl", ""));
                        aiBaseUrl.setText(baseUrl);
                    }
                } catch (Exception ignored) {
                    // Ignore parse errors; key remains blank
                }
            }
        }

        if (key == null || key.isBlank()) {
            aiStatus.setText("Please enter an API key");
            aiTest.setDisable(false);
            return;
        }

        // Effectively-final copies for use inside the background task
        final String finalProvider = provider;
        final String finalModel = model;
        final String finalBaseUrl = baseUrl;
        final String finalKey = key;

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("provider", finalProvider);
        cfg.put("model", finalModel);
        cfg.put("baseUrl", finalBaseUrl);
        cfg.put("apiKey", finalKey);
        cfg.put("hasKey", finalKey != null && !finalKey.isBlank());

        aiTest.setDisable(true);
        aiStatus.setText("Testing\u2026");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                dev.momentum.ai.AiProxy.ChatResponse resp = host.service().aiChat(
                        new dev.momentum.ai.AiProxy.ChatRequest(
                                finalProvider, finalKey, finalModel, finalBaseUrl,
                                List.of(Map.of("role", "user", "content", "Say 'connection ok'.")),
                                false),
                        false);
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> {
            aiTest.setDisable(false);
            aiStatus.setText("AI OK: " + task.getValue());
            // Encrypt the API key before persisting
            host.settingsPutEncrypted("ai_config", dev.momentum.core.Json.stringify(cfg), "apiKey");
            toasts.show("AI connection tested.", ToastHost.Tone.SUCCESS);
        }));
        task.setOnFailed(event -> Platform.runLater(() -> {
            aiTest.setDisable(false);
            aiStatus.setText("AI test failed: " + task.getException().getMessage());
            toasts.show("AI test failed.", ToastHost.Tone.ERROR);
        }));
        new Thread(task, "ai-test").start();
    }
}
