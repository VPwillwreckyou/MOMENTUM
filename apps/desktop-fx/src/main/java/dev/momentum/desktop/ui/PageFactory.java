package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.Text;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.EnumMap;
import java.util.Map;

/**
 * Builds the shell pages. Pages are controller instances (PageView) cached per
 * destination and refreshed via onShown() whenever navigation lands on them.
 * Destinations not yet implemented return an intentional hero card.
 */
public final class PageFactory {

    private final ServiceHost host;
    private final ThemeManager themes;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;
    private final ShellController shell;
    private final Map<NavDestination, PageView> views = new EnumMap<>(NavDestination.class);

    public PageFactory(ServiceHost host, ThemeManager themes, ToastHost toasts, ModalOverlay modals,
                       NavigationManager navigation, ShellController shell) {
        this.host = host;
        this.themes = themes;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;
        this.shell = shell;
    }

    public Node page(NavDestination destination, Object parameter) {
        PageView view = views.computeIfAbsent(destination, this::create);
        view.onShown(parameter);
        return view.root();
    }

    private PageView create(NavDestination destination) {
        return switch (destination) {
            case DASHBOARD -> new DashboardController(host, themes, toasts, modals, navigation);
            case SETTINGS -> settings();
            case GOALS -> new GoalsListController(host, themes, toasts, modals, navigation);
            case GOAL_DETAIL -> new GoalDetailController(host, themes, toasts, modals, navigation);
            case GOAL_EDIT -> new GoalEditorController(host, themes, toasts, modals, navigation);
            default -> heroPage(destination);
        };
    }

    /** Wraps the inline settings builder as a page until Settings gets its own controller. */
    private PageView settings() {
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
        Label storageTitle = new Label("Local storage");
        storageTitle.getStyleClass().add("card-title");
        try {
            var status = host.migrations().status();
            Text dataPath = new Text("Data directory: " + status.get("dataDirectory"));
            Text integrity = new Text("Database integrity: " + status.get("integrityCheck")
                    + " / foreign key violations: " + status.get("foreignKeyViolations"));
            Text migrated = new Text("Last migration: " + (status.get("migratedFrom") == null
                    ? "not yet imported (fresh database)"
                    : status.get("migratedFrom") + " on " + status.get("migrationDate")));
            dataPath.getStyleClass().add("mono-text");
            integrity.getStyleClass().add("mono-text");
            migrated.getStyleClass().add("mono-text");
            VBox storageCard = card("storage-card", storageTitle, dataPath, integrity, migrated);
            root.getChildren().addAll(themeCard, storageCard);
        } catch (java.io.IOException e) {
            root.getChildren().add(themeCard);
        }

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
        root.getChildren().add(aboutCard);
        return new PageView() {
            private final ScrollPane scroll = wrapScroll(root);
            @Override public Node root() { return scroll; }
            @Override public void onShown(Object parameter) {}
        };
    }

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

    private PageView heroPage(NavDestination destination) {
        Node root = hero(destination.title, heroDescription(destination));
        return new PageView() {
            @Override public Node root() { return root; }
            @Override public void onShown(Object parameter) {}
        };
    }

    private String heroDescription(NavDestination destination) {
        return switch (destination) {
            case CALENDAR -> "Month view with goal and task scheduling arrives in Phase 4.";
            case AI_PLANNER -> "Groq-powered plan generation arrives in Phase 5, "
                    + "calling MomentumService.aiChat() directly - no browser involved.";
            case FINANCE -> "Finance tracking with PDF/CSV export arrives in Phase 5.";
            case ACTIVITY -> "The activity timeline lands in Phase 4.";
            case ARCHIVE -> "The archived goals view arrives with the Phase 4 goal screens.";
            case TRASH -> "Restore and permanent-delete management arrives in Phase 4.";
            case TASKS -> "The unified task board, including subtasks and due dates, "
                    + "ships as a Phase 4 screen.";
            default -> "Framework placeholder screen.";
        };
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
}
