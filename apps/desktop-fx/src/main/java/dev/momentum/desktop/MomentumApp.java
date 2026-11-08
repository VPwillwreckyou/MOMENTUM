package dev.momentum.desktop;

import dev.momentum.desktop.host.ServiceHost;
import dev.momentum.desktop.ui.ShellController;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
        System.out.println("MOMENTUM desktop shell starting. Data: " + host.dataDirectory());
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
        stage.centerOnScreen();
        stage.show();

        // One-shot interstitial: the shell is alive.
        if (activeController.toasts != null) {
            javafx.animation.PauseTransition welcome = new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
            welcome.setOnFinished(event -> activeController.toasts.show(
                    "MOMENTUM desktop shell is running. Database connected.", dev.momentum.desktop.ui.ToastHost.Tone.SUCCESS));
            welcome.play();
        }
    }

    @Override
    public void stop() {
        if (host != null) {
            host.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
