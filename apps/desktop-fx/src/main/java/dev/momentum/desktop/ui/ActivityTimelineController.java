package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Activity Timeline: chronological feed of meaningful events across goals,
 * tasks, milestones, attachments, and finance. Reads from the activities
 * table in SQLite.
 */
public class ActivityTimelineController implements PageView {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final ListView<Map<String, Object>> list = new ListView<>();
    private final TextField search = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();

    public ActivityTimelineController(ServiceHost host, ToastHost toasts, ModalOverlay modals,
                                      NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), toolbar(), listSection());

        list.getStyleClass().add("activity-list");
        list.setCellFactory(l -> new ActivityCell());
        list.setPlaceholder(new Label("No activity yet."));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        load();
    }

    /* ---------------------------------------------------------------- layout */

    private Node header() {
        Label title = new Label("Activity Timeline");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(10, title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node toolbar() {
        search.setPromptText("Search activity\u2026");
        search.textProperty().addListener((o, old, nw) -> load());
        search.setPrefWidth(260);

        filter.getItems().addAll("All", "Goals", "Tasks", "Milestones", "Attachments", "Finance");
        filter.setValue("All");
        filter.setPrefWidth(150);
        filter.valueProperty().addListener((o, old, nw) -> load());

        HBox toolbar = new HBox(10, search, filter);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar");
        return toolbar;
    }

    private Node listSection() {
        VBox section = new VBox(10, list);
        section.getStyleClass().add("card");
        VBox.setVgrow(list, Priority.ALWAYS);
        return section;
    }

    /* ---------------------------------------------------------------- data */

    private void load() {
        String query = (search.getText() == null ? "" : search.getText().trim().toLowerCase());
        String typeFilter = filter.getValue();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        List<Map<String, Object>> all = repository.listActivities(null);
                        final AtomicReference<List<Map<String, Object>>> result = new AtomicReference<>(all);
                        Platform.runLater(() -> {
                            List<Map<String, Object>> filtered = all.stream()
                                    .filter(a -> matchesQuery(a, query))
                                    .filter(a -> matchesType(a, typeFilter))
                                    .collect(Collectors.toList());
                            list.getItems().setAll(filtered);
                        });
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                return null;
            }
        };
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load activity: " + task.getException().getMessage(),
                        ToastHost.Tone.ERROR)));
        new Thread(task, "activity-loader").start();
    }

    private boolean matchesQuery(Map<String, Object> a, String query) {
        if (query.isEmpty()) return true;
        return (valueOr(a, "message", "") + " " + valueOr(a, "type", "")).toLowerCase().contains(query);
    }

    private boolean matchesType(Map<String, Object> a, String filter) {
        if ("All".equals(filter)) return true;
        String type = valueOr(a, "type", "");
        return switch (filter) {
            case "Goals" -> type.startsWith("goal");
            case "Tasks" -> type.startsWith("task");
            case "Milestones" -> type.startsWith("milestone");
            case "Attachments" -> type.startsWith("attachment");
            case "Finance" -> type.startsWith("finance");
            default -> true;
        };
    }

    /* ---------------------------------------------------------------- cell */

    private final class ActivityCell extends ListCell<Map<String, Object>> {
        private final Label timeLabel = new Label();
        private final Label typeLabel = new Label();
        private final Label messageLabel = new Label();
        private final Button openButton = new Button("Open");
        private final VBox row = new VBox(6);

        private ActivityCell() {
            timeLabel.getStyleClass().add("activity-time");
            typeLabel.getStyleClass().add("activity-type");
            messageLabel.getStyleClass().add("activity-message");
            openButton.getStyleClass().addAll("btn", "btn-small", "btn-ghost");

            HBox top = new HBox(10, timeLabel, typeLabel, openButton);
            top.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(messageLabel, Priority.ALWAYS);

            row.getChildren().addAll(top, messageLabel);
            row.getStyleClass().add("activity-row");
            row.setMaxWidth(Double.MAX_VALUE);

            openButton.setOnAction(event -> openActivity(getItem()));
            setGraphic(row);
        }

        @Override
        protected void updateItem(Map<String, Object> a, boolean empty) {
            super.updateItem(a, empty);
            if (empty || a == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String createdAt = valueOr(a, "createdAt", "");
            String timeStr = formatDateTime(createdAt);
            timeLabel.setText(timeStr);

            String type = valueOr(a, "type", "");
            typeLabel.setText(formatType(type));
            typeLabel.getStyleClass().setAll("activity-type", "type-" + type.split("-")[0]);

            messageLabel.setText(valueOr(a, "message", ""));
            messageLabel.setWrapText(true);

            String goalId = valueOr(a, "goalId", "");
            String taskId = valueOr(a, "taskId", "");
            openButton.setUserData(goalId.isEmpty() ? taskId : goalId);
            openButton.setOnAction(event -> {
                String id = String.valueOf(openButton.getUserData());
                if (!id.isEmpty()) navigation.select(NavDestination.GOAL_DETAIL, id);
            });

            setText(null);
            setGraphic(row);
        }

        private void openActivity(Map<String, Object> a) {
            String goalId = valueOr(a, "goalId", "");
            String taskId = valueOr(a, "taskId", "");
            String id = goalId.isEmpty() ? taskId : goalId;
            if (!id.isEmpty()) navigation.select(NavDestination.GOAL_DETAIL, id);
        }

        private String formatDateTime(String iso) {
            try {
                Instant instant = Instant.parse(iso);
                java.time.ZoneId zone = java.time.ZoneId.systemDefault();
                java.time.LocalDateTime ldt = instant.atZone(zone).toLocalDateTime();
                return ldt.format(DAY) + " " + ldt.format(TIME);
            } catch (Exception e) {
                return iso;
            }
        }

        private String formatType(String type) {
            String[] parts = type.split("-");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (!p.isEmpty()) {
                    sb.append(Character.toUpperCase(p.charAt(0)));
                    if (p.length() > 1) sb.append(p.substring(1));
                    sb.append(" ");
                }
            }
            return sb.toString().trim();
        }
    }

    /* ---------------------------------------------------------------- helpers */

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
