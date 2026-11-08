package dev.momentum.desktop.ui;

import java.util.EnumMap;
import java.util.Map;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Centralized navigation manager: owns the active destination and how the
 * content area transitions between screens. Sidebar buttons are registered
 * once and highlight state is kept here (single source of truth).
 */
public final class NavigationManager {

    /** Page builders provided by the shell. A destination may carry a parameter (e.g. a goal id). */
    public interface PageBuilder {
        Node build(NavDestination destination, Object parameter);
    }

    /** Notify the shell when the destination changes (header, document title...). */
    public interface Listener {
        void onNavigated(NavDestination destination, Object parameter);
    }

    private final StackPane contentHost;
    private final Map<NavDestination, Button> buttons = new EnumMap<>(NavDestination.class);
    private PageBuilder builder;
    private Listener listener;
    private NavDestination current;
    private Object currentParameter;

    public NavigationManager(StackPane contentHost) {
        this.contentHost = contentHost;
    }

    public void paginate(PageBuilder builder, Listener listener) {
        this.builder = builder;
        this.listener = listener;
    }

    /** Registers a sidebar button (highlight state managed here). */
    public void register(NavDestination destination, Button button) {
        button.getStyleClass().add("nav-item");
        button.getStyleClass().add("nav-" + destination.name().toLowerCase().replace('_', '-'));
        button.setOnAction((ActionEvent event) -> select(destination));
        buttons.put(destination, button);
    }

    public NavDestination current() {
        return current;
    }

    public Object currentParameter() {
        return currentParameter;
    }

    /** Switches to a destination, optionally carrying a parameter (e.g. a goal id). */
    public void select(NavDestination destination, Object parameter) {
        if (destination == null) return;
        if (destination == current && java.util.Objects.equals(parameter, currentParameter)) {
            return;
        }
        current = destination;
        currentParameter = parameter;
        for (Map.Entry<NavDestination, Button> entry : buttons.entrySet()) {
            boolean active = entry.getKey() == destination;
            entry.getValue().getStyleClass().remove("active");
            if (active) entry.getValue().getStyleClass().add("active");
        }
        Node page = builder.build(destination, parameter);
        page.setTranslateY(10);
        page.setOpacity(0);
        contentHost.getChildren().setAll(page);
        FadeTransition fade = new FadeTransition(Duration.millis(200), page);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), page);
        slide.setToY(0);
        fade.setToValue(1);
        fade.play();
        slide.play();
        // Guarantee the final visual state even if the transition is interrupted
        // (interrupted fades previously left pages at opacity 0).
        fade.setOnFinished(event -> page.setOpacity(1));
        slide.setOnFinished(event -> page.setTranslateY(0));
        if (listener != null) {
            listener.onNavigated(destination, parameter);
        }
    }

    /** Switches to a destination without a parameter. */
    public void select(NavDestination destination) {
        select(destination, null);
    }

    /** Creates a nav button; shell adds it to the sidebar visual list. */
    public static Button navButton(String glyph, String title) {
        javafx.scene.control.Label iconLabel = new javafx.scene.control.Label(glyph);
        iconLabel.getStyleClass().add("nav-icon");
        javafx.scene.control.Label textLabel = new javafx.scene.control.Label(title);
        textLabel.getStyleClass().add("nav-text");
        javafx.scene.layout.HBox content = new javafx.scene.layout.HBox(12, iconLabel, textLabel);
        content.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Button button = new Button();
        button.getStyleClass().add("nav-glyph");
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return button;
    }

    public Region contentPlaceholder() {
        Region region = new Region();
        region.getStyleClass().add("nav-spacer");
        return region;
    }
}
