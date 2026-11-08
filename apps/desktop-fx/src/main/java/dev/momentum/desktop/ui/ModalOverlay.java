package dev.momentum.desktop.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Reusable modal dialog system: a dimmed full-window backdrop with a centered
 * rounded card (title, content node, action buttons). ESC or a backdrop click
 * dismisses it unless the modal is marked blocking.
 */
public final class ModalOverlay {

    /** Modelled after the toast tone colors. */
    public enum Tone { PRIMARY, DANGER }

    /** One button row in the modal. */
    public record Action(String label, Tone tone, Runnable handler) {}

    private final StackPane layer;
    private final VBox card;
    private boolean blocking;

    public ModalOverlay() {
        this.card = new VBox(0);
        this.card.getStyleClass().add("modal-card");
        // Hug the content: StackPane would otherwise stretch the card to full size.
        this.card.setMaxWidth(640);
        this.card.setMaxHeight(Region.USE_PREF_SIZE);
        this.layer = new StackPane(card);
        this.layer.getStyleClass().add("modal-layer");
        this.layer.setManaged(false);
        this.layer.setVisible(false);
        this.layer.setPickOnBounds(true);
        this.card.setPickOnBounds(true);
        this.card.setMouseTransparent(false);
        this.layer.setMouseTransparent(false);
        this.layer.setAlignment(Pos.CENTER);
    }

    public StackPane layer() {
        return layer;
    }

    /** Shows a modal. Call with zero actions for a "file designer" later phases? No — auto add Close. */
    public void show(String title, Node content, Action... actions) {
        show(title, content, false, actions);
    }

    public void show(String title, Node content, boolean blocking, Action... actions) {
        this.blocking = blocking;
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("modal-title");
        Region closeSpacer = new Region();
        HBox.setHgrow(closeSpacer, Priority.ALWAYS);
        HBox header = new HBox(titleLabel, closeSpacer);
        header.getStyleClass().add("modal-header");

        List<Action> buttonActions = new ArrayList<>(List.of(actions));
        if (!blocking && (actions.length == 0)) {
            buttonActions.add(new Action("Close", Tone.PRIMARY, this::hide));
        }

        HBox buttonRow = new HBox(10);
        buttonRow.getStyleClass().add("modal-actions");
        for (Action action : buttonActions) {
            Button button = new Button(action.label());
            button.getStyleClass().add("btn");
            button.getStyleClass().add(switch (action.tone()) {
                case PRIMARY -> "btn-primary";
                case DANGER -> "btn-danger";
            });
            button.setOnAction(event -> action.handler().run());
            buttonRow.getChildren().add(button);
        }

        content.getStyleClass().add("modal-content");

        StackPane contentWrap = new StackPane(content);
        contentWrap.setPadding(new Insets(4, 0, 4, 0));

        card.getChildren().setAll(header, new Separator(), contentWrap, new Separator(), buttonRow);
        layer.setManaged(true);
        layer.setVisible(true);
        layer.toFront();
        layer.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && !blocking) {
                hide();
                event.consume();
            }
        });
        layer.setOnMouseClicked(event -> {
            if (event.getTarget() == layer && !blocking) {
                hide();
            }
        });
        // Ask the scene for focus routing for ESC.
        if (layer.getScene() != null) {
            layer.requestFocus();
        }
        FadeTransition fadeIn = new FadeTransition(Duration.millis(120), card);
        fadeIn.setFromValue(0.4);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }

    public boolean isShown() {
        return layer.isVisible();
    }

    public void hide() {
        this.blocking = false;
        layer.setVisible(false);
        layer.setManaged(false);
    }
}
