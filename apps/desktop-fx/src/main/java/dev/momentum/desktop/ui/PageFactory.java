package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.Text;
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
            case SETTINGS -> new SettingsController(host, themes, toasts, modals);
            case GOALS -> new GoalsListController(host, themes, toasts, modals, navigation);
            case CATEGORIES -> new CategoriesController(host, toasts, modals, navigation);
            case TASKS -> new TasksController(host, toasts, modals, navigation);
            case CALENDAR -> new CalendarController(host, toasts, modals, navigation);
            case FINANCE -> new FinanceController(host, toasts, modals, navigation);
            case GLOBAL_SEARCH -> new GlobalSearchController(host, toasts, modals, navigation);
            case ACTIVITY -> new ActivityTimelineController(host, toasts, modals, navigation);
            case ARCHIVE -> new ArchiveController(host, toasts, modals, navigation);
            case TRASH -> new TrashController(host, toasts, modals, navigation);
            case AUTH -> new AuthController(host, toasts);
            case ONBOARDING -> new OnboardingController(host, toasts, navigation);
            case GOAL_DETAIL -> new GoalDetailController(host, themes, toasts, modals, navigation);
            case GOAL_EDIT -> new GoalEditorController(host, themes, toasts, modals, navigation);
            default -> heroPage(destination);
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
