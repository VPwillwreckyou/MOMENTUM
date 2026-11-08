package dev.momentum.desktop.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Toast notification system: stacked cards in the top-right corner,
 * sliding in with a fade and auto-dismissing after ~3.5 seconds.
 */
public final class ToastHost {

    public enum Tone { SUCCESS, ERROR, INFO }

    private static final int MAX_VISIBLE = 3;

    private final VBox stack;
    private final StackPane layer;

    public ToastHost() {
        this.stack = new VBox(10);
        this.layer = new StackPane(stack);
        this.layer.getStyleClass().add("toast-layer");
        this.layer.setManaged(false);
        this.layer.setVisible(false);
        StackPane.setAlignment(stack, Pos.TOP_RIGHT);
        StackPane.setMargin(stack, new javafx.geometry.Insets(54, 18, 18, 18));
        stack.setPickOnBounds(false);
        layer.setPickOnBounds(false);
        layer.setMouseTransparent(false);
        stack.setMouseTransparent(false);
    }

    public StackPane layer() {
        return layer;
    }

    /** Number of toast cards currently visible (used by tests). */
    public int visibleCount() {
        return stack.getChildren().size();
    }

    public void show(String message, Tone tone) {
        show(message, tone, null, null);
    }

    /** A toast with an optional action button (e.g. "Undo"). */
    public void show(String message, Tone tone, String actionLabel, Runnable action) {
        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        text.setWrapText(true);
        text.setMaxWidth(300);

        Label glyph = new Label(switch (tone) {
            case SUCCESS -> "\u2714";
            case ERROR -> "!";
            case INFO -> "i";
        });
        glyph.getStyleClass().add("toast-glyph");

        HBox card = new HBox(10, glyph, text);
        card.getStyleClass().addAll("toast", "toast-" + tone.name().toLowerCase());
        card.setAlignment(Pos.CENTER_LEFT);

        if (actionLabel != null && action != null) {
            Node button = actionButton(actionLabel, () -> {
                action.run();
                dismiss(card);
            });
            card.getChildren().add(button);
        }

        // Click to dismiss any toast; especially useful for error toasts
        // which users may want to acknowledge immediately.
        card.setOnMouseClicked(event -> dismiss(card));

        stack.getChildren().add(card);
        while (stack.getChildren().size() > MAX_VISIBLE) {
            dismissImmediately(stack.getChildren().get(0));
        }
        layer.setManaged(true);
        layer.setVisible(true);

        card.setTranslateX(40);
        card.setOpacity(0);
        Timeline enter = new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(card.translateXProperty(), 0, Interpolator.EASE_OUT),
                new KeyValue(card.opacityProperty(), 1, Interpolator.EASE_OUT)));
        enter.setOnFinished(event -> scheduleDismiss(card));
        enter.play();
    }

    private void scheduleDismiss(HBox card) {
        Timeline leave = new Timeline(new javafx.animation.KeyFrame(Duration.millis(3600), event -> dismiss(card)));
        leave.play();
    }

    private void dismiss(HBox card) {
        if (card.getParent() == null) return;
        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), card);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> dismissImmediately(card));
        fadeOut.play();
    }

    private void dismissImmediately(Node card) {
        stack.getChildren().remove(card);
        if (stack.getChildren().isEmpty()) {
            layer.setManaged(false);
            layer.setVisible(false);
        }
    }

    private Node actionButton(String label, Runnable handler) {
        javafx.scene.control.Button button = new javafx.scene.control.Button(label);
        button.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        button.setOnAction(event -> handler.run());
        return button;
    }
}
