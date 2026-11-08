package dev.momentum.desktop.ui;

import dev.momentum.core.Json;
import dev.momentum.desktop.host.ServiceHost;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Onboarding: first-run welcome screen. Completing it stores the onboarding
 * flag in SQLite so it is not shown again.
 */
public class OnboardingController implements PageView {

    private final ServiceHost host;
    private final ToastHost toasts;
    private final NavigationManager navigation;

    private final VBox root = new VBox(24);

    public OnboardingController(ServiceHost host, ToastHost toasts, NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.setAlignment(Pos.CENTER);
        root.getChildren().addAll(header(), card());
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        // Nothing to refresh.
    }

    private Node header() {
        Label title = new Label("Welcome to MOMENTUM");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Your native desktop goal app. Your data stays on your PC in SQLite.");
        sub.getStyleClass().add("hero-sub");
        VBox box = new VBox(8, title, sub);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Node card() {
        Label steps = new Label("""
                What's next:
                  1. Create your first goal on the Dashboard.
                  2. Organize goals into Categories.
                  3. Break goals into Milestones and Tasks.
                  4. Use the AI Planner to turn ideas into plans.
                """);
        steps.getStyleClass().add("hero-sub");
        steps.setWrapText(true);
        steps.setMaxWidth(520);

        Button done = new Button("Get started");
        done.getStyleClass().addAll("btn", "btn-primary");
        done.setOnAction(event -> completeOnboarding());

        VBox card = new VBox(18, steps, done);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("card");
        card.setMaxWidth(600);
        return card;
    }

    private void completeOnboarding() {
        try {
            host.settingsPut("onboarding", Json.stringify(Map.of(
                    "complete", true,
                    "completedAt", Instant.now().toString())));
            toasts.show("Onboarding complete.", ToastHost.Tone.SUCCESS);
            navigation.select(NavDestination.DASHBOARD);
        } catch (Exception e) {
            toasts.show("Could not save onboarding state: " + e.getMessage(), ToastHost.Tone.ERROR);
        }
    }
}
