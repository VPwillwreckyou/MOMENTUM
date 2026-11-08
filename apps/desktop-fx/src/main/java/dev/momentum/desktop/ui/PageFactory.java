package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 * Builds the framework-phase screens.
 *
 * Functional now (framework scope): Dashboard (live database statistics)
 * and Settings (theme switching + storage status).
 * All other destinations are intentional "next phase" heroes.
 */
public final class PageFactory {

    private final ServiceHost host;
    private final ThemeManager themes;
    private final ToastHost toasts;
    private final ModalOverlay modals;

    public PageFactory(ServiceHost host, ThemeManager themes, ToastHost toasts, ModalOverlay modals) {
        this.host = host;
        this.themes = themes;
        this.toasts = toasts;
        this.modals = modals;
    }

    public Node page(NavDestination destination) {
        return switch (destination) {
            case DASHBOARD -> dashboard();
            case SETTINGS -> settings();
            case GOALS -> hero("Goals", "Full goal management lands in Phase 4:\ncreate, edit, complete, archive and organize goals.");
            case TASKS -> hero("Tasks", "The unified task board, including subtasks and due dates,\nships as a Phase 4 screen.");
            case CALENDAR -> hero("Calendar", "Month view with goal and task scheduling arrives in Phase 4.");
            case AI_PLANNER -> hero("AI Planner", "Groq-powered plan generation arrives in Phase 5,\ncalling MomentumService.aiChat() directly - no browser involved.");
            case FINANCE -> hero("Finance", "Finance tracking with PDF/CSV export arrives in Phase 5.");
            case ACTIVITY -> hero("Activity", "The activity timeline lands in Phase 4.");
            case ARCHIVE -> hero("Archive", "The archived goals view arrives with the Phase 4 goal screens.");
            case TRASH -> hero("Trash", "Restore and permanent-delete management arrives in Phase 4.");
        };
    }

    // ---------------------------------------------------------------- dashboard

    private Node dashboard() {
        VBox root = pageRoot();

        Label welcome = new Label("Welcome to MOMENTUM Desktop");
        welcome.getStyleClass().add("hero-title");
        Label welcomeSub = new Label("""
                This is the native JavaFX shell - the foundation every main screen will build on.
                Your indexed desktop database is live; the data screens follow in Phase 4.""");

        welcomeSub.getStyleClass().add("hero-sub");
        welcomeSub.setWrapText(true);

        Map<String, Long> counts = counts();
        GridPane stats = new GridPane();
        stats.setHgap(14);
        stats.setVgap(14);
        stats.getStyleClass().add("stat-grid");
        String metricLabels = "Goals in database";
        int column = 0;
        int row = 0;
        var metricValues = Map.of(
                "Goals in database", counts.getOrDefault("goals", 0L),
                "Tasks", counts.getOrDefault("tasks", 0L),
                "Milestones", counts.getOrDefault("milestones", 0L),
                "Categories", counts.getOrDefault("categories", 0L),
                "Attachments", counts.getOrDefault("attachments", 0L),
                "Activities", counts.getOrDefault("activities", 0L));
        for (var entry : metricValues.entrySet()) {
            Node statCard = statCard(entry.getKey(), entry.getValue());
            stats.add(statCard, column, row);
            column++;
            if (column == 3) {
                column = 0;
                row++;
            }
        }

        Label dbState = new Label(metricLabels + " are shown live from your local SQLite database.");
        dbState.getStyleClass().add("hero-sub");

        if (counts.getOrDefault("goals", 0L) == 0) {
            Label emptyHint = new Label("""
                Your database is fresh. To bring in data from the web app: export a backup
                (Settings > Export backup) and import it with the local service API.
                See TUTORIAL.md > Bringing your browser data to the desktop database.""");
            emptyHint.getStyleClass().add("hero-sub");
            emptyHint.setWrapText(true);
            root.getChildren().addAll(surroundingCard("welcome-card", welcome, welcomeSub),
                    statCard(stats), surroundingCard("hint-card", dbState, emptyHint));
        } else {
            root.getChildren().addAll(surroundingCard("welcome-card", welcome, welcomeSub),
                    statCard(stats));
        }
        ScrollPane scroll = wrapScroll(root);
        return scroll;
    }

    // ---------------------------------------------------------------- settings

    private Node settings() {
        VBox root = pageRoot();

        // Theme picker
        Label themeTitle = new Label("Color theme");
        themeTitle.getStyleClass().add("card-title");
        Label themeSub = new Label("Applies instantly. Stored in your local SQLite database.");
        themeSub.getStyleClass().add("hero-sub");

        FlowPane swatches = new FlowPane(12, 12);
        for (ThemeManager.Theme theme : ThemeManager.PRESETS) {
            Button swatch = new Button(theme.name());
            swatch.getStyleClass().add("swatch");
            swatch.setStyle("-swatch-accent:" + theme.accent() + ";");
            if (themes.current().id().equals(theme.id())) {
                swatch.getStyleClass().add("selected");
            }
            swatch.setOnAction(event -> {
                themes.apply(theme);
                toasts.show(theme.name() + " theme applied.", ToastHost.Tone.SUCCESS);
                // Update all swatch selected states
                for (Node node : swatches.getChildren()) {
                    boolean active = node == swatch;
                    node.getStyleClass().remove("selected");
                    if (active) node.getStyleClass().add("selected");
                }
            });
            swatches.getChildren().add(swatch);
        }

        VBox themeCard = card("theme-card", themeTitle, themeSub, swatches);

        // Storage status
        String storageLine = "Data directory: " + host.dataDirectory();
        String integrityLine = "Database integrity: unavailable";
        String migratedLine = "Database not initialized yet";
        try {
            var status = host.migrations().status();
            integrityLine = "Database integrity: " + status.get("integrityCheck")
                    + " / foreign key violations: " + status.get("foreignKeyViolations");
            migratedLine = "Last migration: " + (status.get("migratedFrom") == null
                    ? "not yet imported (fresh database)" : status.get("migratedFrom") + " on " + status.get("migrationDate"));
        } catch (java.io.IOException e) {
            // Storage card keeps the fallback text.
        }
        Label storageTitle = new Label("Local storage");
        storageTitle.getStyleClass().add("card-title");
        Text dataPath = new Text("Data directory: " + storageLine.substring("Data directory: ".length()));
        Text integrity = new Text(integrityLine);
        Text migrated = new Text(migratedLine);
        dataPath.getStyleClass().add("mono-text");
        integrity.getStyleClass().add("mono-text");
        migrated.getStyleClass().add("mono-text");
        VBox storageCard = card("storage-card", storageTitle, dataPath, integrity, migrated);

        // App info
        Label aboutTitle = new Label("About this build");
        aboutTitle.getStyleClass().add("card-title");
        Text infoLines = new Text("""
            UI engine: JavaFX (native rendering, no browser, no Chromium, no WebView)
            Data engine: SQLite + the MOMENTUM service layer shared with the HTTP server
            Framework phase: 3 of the 8-phase JavaFX migration""");
        infoLines.getStyleClass().add("mono-text");

        Button demoModalButton = new Button("Open a test modal");
        demoModalButton.getStyleClass().addAll("btn", "btn-primary");
        demoModalButton.setOnAction(event -> modals.show(
                "Framework diagnostic",
                new Label("Modals, toasts, navigation and themes are all wired up.\nScreens arrive from Phase 4 onward."),
                new ModalOverlay.Action("Got it", ModalOverlay.Tone.PRIMARY, modals::hide)));

        Button demoToastButton = new Button("Show toast");
        demoToastButton.getStyleClass().addAll("btn", "btn-ghost");
        demoToastButton.setOnAction(event -> toasts.show("Toast system operational.", ToastHost.Tone.INFO));

        HBox demoRow = new HBox(10, demoModalButton, demoToastButton);
        VBox aboutCard = card("about-card", aboutTitle, infoLines, demoRow);

        root.getChildren().addAll(themeCard, storageCard, aboutCard);
        return wrapScroll(root);
    }

    // ---------------------------------------------------------------- shared builders

    private VBox pageRoot() {
        VBox root = new VBox(16);
        root.getStyleClass().add("page");
        root.setFillWidth(true);
        return root;
    }

    private ScrollPane wrapScroll(VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("content-scroll");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPadding(new Insets(0));
        return scroll;
    }

    private VBox card(String styleClass, Node... children) {
        VBox card = new VBox(12);
        card.getStyleClass().addAll("card", styleClass);
        card.getChildren().addAll(children);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** Positions simple content inside a padded frame. */
    private HBox surroundingCard(String styleClass, Node... children) {
        StackPane frame = new StackPane(new VBox(10, children));
        frame.getStyleClass().add("card");
        frame.getStyleClass().add(styleClass);
        HBox.setHgrow(frame, Priority.ALWAYS);
        VBox.setVgrow(frame, Priority.ALWAYS);
        HBox wrapper = new HBox(frame);
        wrapper.getStyleClass().add("card-row");
        return wrapper;
    }

    private Node statCard(GridPane stats) {
        StackPane frame = new StackPane(stats);
        frame.getStyleClass().add("card");
        frame.getStyleClass().add("stat-frame");
        return frame;
    }

    private Node statCard(String label, long value) {
        Label valueLabel = new Label(String.valueOf(value));
        valueLabel.getStyleClass().add("stat-value");
        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("stat-name");
        VBox stat = new VBox(6, valueLabel, nameLabel);
        stat.getStyleClass().add("stat-card");
        return stat;
    }

    private Node hero(String title, String description) {
        Label heroTitle = new Label(title);
        heroTitle.getStyleClass().add("hero-title");
        Label glyph = new Label(glyphOf(title));
        glyph.getStyleClass().add("hero-glyph");
        HBox titleRow = new HBox(14, glyph, heroTitle);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label heroText = new Label(description);
        heroText.getStyleClass().add("hero-sub");
        heroText.setWrapText(true);
        Label staging = new Label("Screen framework ready - functionality lands in a later phase.");
        staging.getStyleClass().add("hero-note");
        VBox hero = new VBox(16, titleRow, heroText, staging);
        hero.getStyleClass().add("hero-card");
        HBox wrapper = new HBox(hero);
        wrapper.getStyleClass().add("card-row");
        VBox root = pageRoot();
        root.getChildren().add(wrapper);
        return wrapScroll(root);
    }

    private String glyphOf(String title) {
        for (NavDestination destination : NavDestination.values()) {
            if (destination.title.equals(title)) {
                return destination.glyph;
            }
        }
        return "\u25C9";
    }

    private Map<String, Long> counts() {
        Map<String, Object> status;
        try {
            status = host.migrations().status();
        } catch (java.io.IOException e) {
            return new LinkedHashMap<>();
        }
        Object countsObject = status.get("counts");
        if (!(countsObject instanceof Map<?, ?> counts)) {
            return new LinkedHashMap<>();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : counts.entrySet()) {
            if (entry.getValue() instanceof Number number) {
                result.put(String.valueOf(entry.getKey()), number.longValue());
            }
        }
        return result;
    }
}
