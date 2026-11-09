package dev.momentum.desktop.ui;

import dev.momentum.desktop.MomentumApp;
import dev.momentum.desktop.host.ServiceHost;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Main application shell controller:
 *
 *   - Custom undecorated desktop window (drag, maximize, minimize, close)
 *   - Left navigation sidebar + content host
 *   - Top titlebar with page breadcrumb and user profile section
 *   - Modal + toast overlays
 *   - Database status indicator
 */
public class ShellController implements Initializable {

    @FXML private StackPane root;
    @FXML private VBox windowFrame;
    @FXML private HBox titlebar;
    @FXML private Label pageTitle;
    @FXML private StackPane contentHost;
    @FXML private VBox sidebar;
    @FXML private VBox navBox;
    @FXML private Region navSpacer;
    @FXML private StackPane footerStatus;
    @FXML private StackPane modalLayerHost;
    @FXML private StackPane toastLayerHost;
    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;

    private NavigationManager navigation;
    public ToastHost toasts;
    public ModalOverlay modals;
    public ThemeManager themes;
    public PageFactory pages;

    private double dragOffsetX;
    private double dragOffsetY;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ServiceHost host = MomentumApp.host();
        if (host == null) {
            throw new IllegalStateException("ServiceHost has not been provided to the shell.");
        }

        toasts = new ToastHost();
        modals = new ModalOverlay();
        toastLayerHost.getChildren().add(toasts.layer());
        modalLayerHost.getChildren().add(modals.layer());
        // The host layers must always be present in the scene tree — visibility
        // of individual overlays is controlled inside ToastHost/ModalOverlay.
        modalLayerHost.setVisible(true);
        modalLayerHost.setManaged(true);
        toastLayerHost.setVisible(true);
        toastLayerHost.setManaged(true);

        navigation = new NavigationManager(contentHost);
        themes = new ThemeManager(host, root::setStyle);

        pages = new PageFactory(host, themes, toasts, modals, navigation, this);
        navigation.paginate(pages::page, (destination, parameter) -> pageTitle.setText(destination.title));

        for (NavDestination destination : List.of(
                NavDestination.DASHBOARD, NavDestination.GOALS, NavDestination.TASKS,
                NavDestination.CALENDAR, NavDestination.AI_PLANNER, NavDestination.FINANCE,
                NavDestination.GLOBAL_SEARCH, NavDestination.IMPORT_EXPORT,
                NavDestination.ACTIVITY, NavDestination.ARCHIVE, NavDestination.TRASH,
                NavDestination.CATEGORIES, NavDestination.AUTH,
                NavDestination.ONBOARDING, NavDestination.SETTINGS)) {
            Button button = NavigationManager.navButton(destination.glyph, destination.title);
            navigation.register(destination, button);
            navBox.getChildren().add(button);
        }
        VBox.setVgrow(navSpacer, Priority.ALWAYS);
        navigation.select(NavDestination.DASHBOARD);

        // Window chrome wiring
        minimizeButton.setOnAction(event -> stage().setIconified(true));
        maximizeButton.setOnAction(event -> toggleMaximize());
        closeButton.setOnAction(event -> stage().close());
        titlebar.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onWindowDragStart);
        titlebar.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onWindowDrag);
        titlebar.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2 && event.getTarget() == titlebar) {
                toggleMaximize();
            }
        });

        // Database badge: updated from the existing service layer
        String badge = "DATABASE READY";
        try {
            var status = host.migrations().status();
            if (Boolean.TRUE.equals(status.get("databaseExists"))) {
                badge = "DATABASE LIVE";
            }
        } catch (java.io.IOException e) {
            badge = "DATABASE OFFLINE";
        }
        Label dbName = new Label(badge);
        dbName.getStyleClass().add("db-label");
        Label dbDot = new Label("\u25CF");
        dbDot.getStyleClass().add("db-dot");
        HBox footer = new HBox(8, dbDot, dbName);
        footer.setAlignment(Pos.CENTER_LEFT);
        footerStatus.getChildren().add(footer);
    }

    /* -------------------------------------------------------------- smoke accessors */

    public StackPane rootNode() { return root; }

    public int contentChildren() { return contentHost.getChildren().size(); }

    public int toastNodes() { return toasts == null ? 0 : toasts.visibleCount(); }

    public String rootStyle() { return root.getStyle(); }

    public String hostSettingsJson(String key) {
        return MomentumApp.host().settingsGet(key).orElse(null);
    }

    /** The live content page root (for diagnostics/tests). */
    public javafx.scene.Node contentPane() {
        return contentHost.getChildren().isEmpty()
                ? null : contentHost.getChildren().get(0);
    }

    /* -------------------------------------------------------------- navigation */

    /** Programmatic navigation (used by smoke tests and future shortcuts). */
    public void navigate(NavDestination destination) {
        navigation.select(destination);
    }

    /** Programmatic navigation carrying a parameter (e.g. a goal id). */
    public void navigate(NavDestination destination, Object parameter) {
        navigation.select(destination, parameter);
    }

    public NavDestination currentDestination() {
        return navigation.current();
    }

    /* -------------------------------------------------------------- window chrome */

    private Stage stage() {
        return (Stage) root.getScene().getWindow();
    }

    private void onWindowDragStart(MouseEvent event) {
        Stage stage = stage();
        if (stage.isMaximized()) return;
        dragOffsetX = stage.getX() - event.getScreenX();
        dragOffsetY = stage.getY() - event.getScreenY();
    }

    private void onWindowDrag(MouseEvent event) {
        Stage stage = stage();
        if (stage.isMaximized()) return;
        stage.setX(event.getScreenX() + dragOffsetX);
        stage.setY(event.getScreenY() + dragOffsetY);
    }

    private void toggleMaximize() {
        Stage stage = stage();
        if (stage.isMaximized()) {
            stage.setMaximized(false);
        } else {
            stage.setMaximized(true);
        }
    }
}
