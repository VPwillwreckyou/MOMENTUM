package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Trash screen: lists soft-deleted goals and offers restore or permanent
 * delete (purge) after confirmation. Never silently deletes user data.
 */
public class TrashController implements PageView {

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final ListView<Map<String, Object>> list = new ListView<>();

    public TrashController(ServiceHost host, ToastHost toasts, ModalOverlay modals,
                           NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), listSection());

        list.getStyleClass().add("goal-list");
        list.setPlaceholder(new Label("Your trash is empty."));
        list.setCellFactory(l -> new TrashCell());
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        load();
    }

    private Node header() {
        Label title = new Label("Trash");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(10, title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node listSection() {
        VBox section = new VBox(10, list);
        section.getStyleClass().add("card");
        VBox.setVgrow(list, Priority.ALWAYS);
        return section;
    }

    private void load() {
        final AtomicReference<List<Map<String, Object>>> goals = new AtomicReference<>(new ArrayList<>());
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        goals.set(repository.listTrashedGoals());
                    } catch (java.sql.SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> list.getItems().setAll(goals.get())));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load trash: " + task.getException().getMessage(), ToastHost.Tone.ERROR)));
        new Thread(task, "trash-loader").start();
    }

    private final class TrashCell extends ListCell<Map<String, Object>> {
        private final GoalCard card = new GoalCard(host, toasts, modals);

        private TrashCell() {
            getStyleClass().add("goal-list-cell");
        }

        @Override
        protected void updateItem(Map<String, Object> goal, boolean empty) {
            super.updateItem(goal, empty);
            if (empty || goal == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String id = String.valueOf(goal.get("id"));
            card.bind(goal,
                    () -> navigation.select(NavDestination.GOAL_DETAIL, id),
                    () -> navigation.select(NavDestination.GOAL_EDIT, id),
                    () -> load(),
                    () -> load());
            setText(null);
            setGraphic(card.node());
        }
    }
}
