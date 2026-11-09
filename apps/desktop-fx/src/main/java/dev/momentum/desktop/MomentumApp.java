package dev.momentum.desktop;

import dev.momentum.desktop.host.ServiceHost;
import dev.momentum.desktop.ui.NavDestination;
import dev.momentum.desktop.ui.ShellController;
import dev.momentum.desktop.util.Logger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * MOMENTUM native desktop application (Phase 3: framework).
 *
 * Renders 100% through JavaFX — no WebView, no Chromium, no Electron, no
 * browser of any kind. The window is undecorated with a custom title bar and
 * lives permanently on JavaFX's own rendering pipeline.
 */
public class MomentumApp extends Application {

    public static final String VERSION = "1.0.0";

    private static volatile ServiceHost sharedHost;
    private ServiceHost host;

    /** The host, set during init() so FXML controllers can reach the backend. */
    public static ServiceHost host() {
        return sharedHost;
    }

    /** Access the shell controller (used by the smoke test). */
    public static ShellController activeController;

    @Override
    public void init() throws IOException {
        this.host = new ServiceHost(ServiceHost.defaultDataDirectory());
        momentumHost(host);
        Logger.init(host.dataDirectory().resolve("logs"));
        Logger.info("MOMENTUM desktop shell starting. Data: " + host.dataDirectory());
    }

    /** Publishes a host instance for FXML controllers (also used by the smoke test). */
    public static void momentumHost(ServiceHost host) {
        sharedHost = host;
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(MomentumApp.class
                .getResource("/dev/momentum/desktop/fxml/MainShell.fxml"));
        Parent root = loader.load();
        activeController = loader.getController();

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(MomentumApp.class
                .getResource("/dev/momentum/desktop/css/theme.css").toExternalForm());

        stage.initStyle(StageStyle.UNDECORATED);
        stage.titleProperty().setValue("MOMENTUM");
        stage.minWidthProperty().setValue(1080);
        stage.minHeightProperty().setValue(680);
        stage.setScene(scene);
        stage.setResizable(true);

        // Restore window state from settings (Phase 6.10: Window Management)
        restoreWindowState(stage);

        // Save window state whenever it changes
        stage.widthProperty().addListener((obs, old, neww) -> saveWindowState(stage));
        stage.heightProperty().addListener((obs, old, newh) -> saveWindowState(stage));
        stage.xProperty().addListener((obs, old, neww) -> saveWindowState(stage));
        stage.yProperty().addListener((obs, old, newh) -> saveWindowState(stage));
        stage.maximizedProperty().addListener((obs, old, neww) -> saveWindowState(stage));

        stage.setOnCloseRequest(event -> saveWindowState(stage));

        stage.show();

        // Add application icon to the window frame and taskbar.
        try {
            stage.getIcons().add(new javafx.scene.image.Image(
                    MomentumApp.class.getResourceAsStream(
                            "/dev/momentum/desktop/icon.ico")));
        } catch (Exception e) {
            System.err.println("MOMENTUM: Could not load application icon: " + e.getMessage());
        }

        // One-shot interstitial: the shell is alive.
        if (activeController.toasts != null) {
            javafx.animation.PauseTransition welcome = new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
            welcome.setOnFinished(event -> activeController.toasts.show(
                    "MOMENTUM desktop shell is running. Database connected.", dev.momentum.desktop.ui.ToastHost.Tone.SUCCESS));
            welcome.play();
        }

        // Auto-show onboarding on first launch.
        boolean showOnboarding = true;
        try {
            String json = host.settingsGet("onboarding").orElse(null);
            if (json != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cfg = dev.momentum.core.Json.parseObject(json);
                showOnboarding = !Boolean.TRUE.equals(cfg.get("complete"));
            }
        } catch (Exception ignored) {
        }
        if (showOnboarding) {
            activeController.navigate(NavDestination.ONBOARDING);
        }
    }

    /** Restore window size, position, and maximized state from persisted settings. */
    private void restoreWindowState(Stage stage) {
        try {
            String json = host.settingsGet("window_state").orElse(null);
            if (json == null) {
                stage.centerOnScreen();
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> state = dev.momentum.core.Json.parseObject(json);
            double width = asDouble(state.get("width"), 1280);
            double height = asDouble(state.get("height"), 800);
            double x = asDouble(state.get("x"), -1);
            double y = asDouble(state.get("y"), -1);
            boolean maximized = Boolean.TRUE.equals(state.get("maximized"));

            // Validate against screen bounds (Phase 6.10: screen-boundary validation)
            Screen screen = Screen.getPrimary();
            double screenW = screen.getVisualBounds().getWidth();
            double screenH = screen.getVisualBounds().getHeight();

            stage.setWidth(Math.max(stage.getMinWidth(), Math.min(width, screenW)));
            stage.setHeight(Math.max(stage.getMinHeight(), Math.min(height, screenH)));

            if (x >= 0 && y >= 0 && x + width <= screenW && y + height <= screenH) {
                stage.setX(x);
                stage.setY(y);
            } else {
                stage.centerOnScreen();
            }

            if (maximized) {
                stage.setMaximized(true);
            }
        } catch (Exception e) {
            Logger.warn("Could not restore window state: " + e.getMessage());
            stage.centerOnScreen();
        }
    }

    /** Save current window state to settings (Phase 6.10: Window Management). */
    private void saveWindowState(Stage stage) {
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("width", stage.getWidth());
            state.put("height", stage.getHeight());
            state.put("x", stage.getX());
            state.put("y", stage.getY());
            state.put("maximized", stage.isMaximized());
            host.settingsPut("window_state", dev.momentum.core.Json.stringify(state));
        } catch (Exception e) {
            Logger.warn("Could not save window state: " + e.getMessage());
        }
    }

    private static double asDouble(Object v, double def) {
        if (v == null) return def;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public void stop() {
        if (host != null) {
            try {
                host.checkpointWAL();
            } catch (Exception e) {
                Logger.error("WAL checkpoint on shutdown failed: " + e.getMessage(), e);
            }
            host.close();
        }
        Logger.info("MOMENTUM desktop shell stopped.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
