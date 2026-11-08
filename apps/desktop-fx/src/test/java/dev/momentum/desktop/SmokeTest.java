package dev.momentum.desktop;

import dev.momentum.desktop.ui.ModalOverlay;
import dev.momentum.desktop.ui.NavDestination;
import dev.momentum.desktop.ui.ShellController;
import dev.momentum.desktop.ui.ThemeManager;
import dev.momentum.desktop.ui.ToastHost;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/**
 * Phase 3 framework smoke test.
 *
 * Verifies inside a real JavaFX stage:
 *   1. the shell boots standalone on the JavaFX pipeline,
 *   2. every navigation destination switches content,
 *   3. modals open and close,
 *   4. toasts appear,
 *   5. theme switching works and persists to SQLite,
 *   6. the SQLite connection is live,
 *   7. rendering produces real pixels (PNG snapshot per page on disk).
 *
 * The FX thread is never blocked: each step is a separate Platform.runLater
 * pulse with sleeps on the caller thread in between, so animations actually run.
 * Exits 0 printing "SMOKE-OK", exits 1 with the failure reason otherwise.
 */
public final class SmokeTest {

    private static final StringBuilder REPORT = new StringBuilder();
    private static final Path SNAPSHOT_DIR = Path.of("build", "smoke");
    private static Throwable failure;
    private static Stage stage;
    private static Stage stage2;

    public static void main(String[] args) throws Exception {
        System.setProperty("prism.order", "sw");
        System.setProperty("momentum.data.dir",
                System.getProperty("momentum.data.dir", "build/smoke-data"));
        Platform.startup(() -> { });

        Throwable[] error = {null};
        try {
            runChecks();
        } catch (Throwable throwable) {
            error[0] = throwable;
        }
        System.out.println(REPORT);
        if (error[0] != null) {
            System.out.println("SMOKE-FAIL reason: " + error[0]);
            error[0].printStackTrace(System.out);
            Platform.runLater(Platform::exit);
            System.exit(1);
        }
        System.out.println("SMOKE-OK");
        Platform.runLater(Platform::exit);
        System.exit(0);
    }

    /** Runs a unit of work on the FX thread and waits for it on the caller thread. */
    private static void fx(FxWork work) throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                work.run();
            } catch (Throwable throwable) {
                thrown.set(throwable);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(60, TimeUnit.SECONDS)) {
            throw new AssertionError("FX step timed out.");
        }
        if (thrown.get() != null) {
            if (thrown.get() instanceof Exception exception) throw exception;
            throw new RuntimeException(thrown.get());
        }
    }

    private interface FxWork {
        void run() throws Exception;
    }

    private static void settle(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    // ------------------------------------------------------------------ checks

    private static void runChecks() throws Exception {
        MomentumApp app = new MomentumApp();

        fx(() -> {
            app.init();
            stage = new Stage();
            app.start(stage);
        });
        // Let the first layout + welcome toast animation settle.
        settle(900);

        ShellController shell = MomentumApp.activeController;
        check(shell != null, "shell controller present");

        fx(() -> check(stage.isShowing(), "window is showing"));
        fx(() -> check(!stage.isMaximized(), "window starts normal"));

        // 1. Navigation: every destination switches content and renders.
        for (NavDestination destination : NavDestination.values()) {
            fx(() -> {
                shell.navigate(destination);
                check(shell.currentDestination() == destination,
                        "navigated to " + destination);
            });
            settle(420); // fade/slide transition completes on live pulses

            final String name = destination.name().toLowerCase();
            boolean fadedIn = false;
            for (int attempt = 0; attempt < 6 && !fadedIn; attempt++) {
                settle(200);
                final boolean[] verdict = {false};
                fx(() -> verdict[0] = shell.contentPane() != null
                        && shell.contentPane().isVisible()
                        && shell.contentPane().getOpacity() > 0.99);
                fadedIn = verdict[0];
            }
            final boolean fadedOK = fadedIn;
            fx(() -> {
                check(shell.contentChildren() > 0, "content rendered for " + destination);
                check(fadedOK, "page fully faded in for " + destination);
                snapshot(stage, "nav-" + name);
            });
            settle(60);
        }

        // 2. Modal system.
        fx(() -> {
            shell.modals.show("Smoke test dialog",
                    new Label("Modal system verified."),
                    new ModalOverlay.Action("Close", ModalOverlay.Tone.PRIMARY, shell.modals::hide));
            check(shell.modals.isShown(), "modal visible");
        });
        settle(300);
        fx(() -> snapshot(stage, "modal"));
        fx(shell.modals::hide);
        settle(150);
        fx(() -> check(!shell.modals.isShown(), "modal hidden"));

        // 3. Toast system.
        fx(() -> {
            shell.toasts.show("Smoke: toast fired.", ToastHost.Tone.INFO);
            check(shell.toastNodes() > 0, "toast visible");
        });
        settle(350);
        fx(() -> snapshot(stage, "toast"));

        // 4. Theme switching + persistence.
        fx(() -> {
            ThemeManager themeManager = shell.themes;
            themeManager.applyById("violet");
            check(shell.rootStyle().contains("#8b5cf6"), "root style carries violet accent");
        });
        settle(250);
        fx(() -> {
            shell.navigate(NavDestination.SETTINGS);
            check(shell.currentDestination() == NavDestination.SETTINGS, "settings page shown");
        });
        settle(420);
        fx(() -> snapshot(stage, "theme-violet"));
        fx(() -> {
            shell.themes.applyById("cyan");
            check(shell.rootStyle().contains("#22d3ee"), "root style carries cyan accent");
            check(shell.hostSettingsJson("theme").contains("\"theme\":\"cyan\""),
                    "theme persisted to SQLite");
            check(shell.hostSettingsJson("theme") != null, "settings table readable");
        });
        settle(200);

        // Navigation sanity: goals list renders (empty state on fresh DB).
        fx(() -> shell.navigate(NavDestination.GOALS));
        settle(420);
        fx(() -> {
            check(shell.contentChildren() > 0, "goals list rendered");
            snapshot(stage, "nav-goals");
        });

        // CRUD persistence check: create a goal, restart the app, confirm it survives.
        fx(() -> {
            String createdId = MomentumApp.host().withRepository(repository -> {
                try {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("id", "smoke-persist-goal");
                    g.put("title", "Smoke persistence goal");
                    g.put("status", "active");
                    repository.upsertGoal(g);
                    repository.recordActivity("goal-created", "smoke-persist-goal", null, "Created smoke persistence goal");
                    return String.valueOf(g.get("id"));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            check(createdId != null, "goal creatable via repository");
        });
        settle(300);
        fx(() -> shell.navigate(NavDestination.GOALS));
        settle(420);
        fx(() -> snapshot(stage, "nav-goals"));

        // Goal editor renders (create mode, empty form).
        fx(() -> shell.navigate(NavDestination.GOAL_EDIT));
        settle(1200);
        boolean editorVisible = false;
        for (int attempt = 0; attempt < 5 && !editorVisible; attempt++) {
            settle(200);
            final boolean[] verdict = {false};
            fx(() -> verdict[0] = shell.contentPane() != null
                    && shell.contentPane().isVisible()
                    && shell.contentPane().getOpacity() > 0.99);
            editorVisible = verdict[0];
        }
        check(editorVisible, "goal editor fully faded in");
        fx(() -> snapshot(stage, "nav-goal_edit"));

        app.stop();

        MomentumApp app2 = new MomentumApp();
        fx(() -> {
            app2.init();
            stage2 = new Stage();
            app2.start(stage2);
        });
        settle(600);
        fx(() -> {
            List<Map<String, Object>> goals = MomentumApp.host().withRepository(repository -> {
                try {
                    return repository.listGoals(null);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            check(goals.stream().anyMatch(g -> "Smoke persistence goal".equals(String.valueOf(g.get("title")))),
                    "goal survives application restart");
        });
        fx(app2::stop);

        // 5. Snapshot sanity: real rendered pixels, dark cyber background.
        fx(() -> snapshot(stage, "final"));
        Path shot = SNAPSHOT_DIR.resolve("final.png");
        BufferedImage image = ImageIO.read(shot.toFile());
        check(image.getWidth() >= 1000, "snapshot width " + image.getWidth());
        check(image.getHeight() >= 640, "snapshot height " + image.getHeight());
        Set<Integer> distinct = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 3) {
            for (int x = 0; x < image.getWidth(); x += 3) {
                distinct.add(image.getRGB(x, y) & 0xFFFFFF);
            }
        }
        check(distinct.size() > 60, "scene has varied pixels: " + distinct.size());
        int corner = image.getRGB(6, 6) & 0xFFFFFF;
        check((corner >> 16 & 0xFF) < 40 && (corner >> 8 & 0xFF) < 40 && (corner & 0xFF) < 40,
                "background is dark: " + Integer.toHexString(corner));

        // Database file exists on disk (real SQLite persistence).
        Path smokeData = Path.of(System.getProperty("momentum.data.dir"));
        check(Files.isRegularFile(smokeData.resolve("momentum.db")), "momentum.db exists on disk");

        fx(app::stop);
    }

    /* helpers */

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
        REPORT.append("PASS ").append(message).append('\n');
    }

    private static void snapshot(Stage stage, String name) {
        WritableImage image = stage.getScene().snapshot(null);
        try {
            Files.createDirectories(SNAPSHOT_DIR);
            Path target = SNAPSHOT_DIR.resolve(name + ".png");
            BufferedImage buffered = new BufferedImage(
                    (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < buffered.getHeight(); y++) {
                for (int x = 0; x < buffered.getWidth(); x++) {
                    buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            ImageIO.write(buffered, "png", target.toFile());
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot write failed: " + name, e);
        }
    }
}
