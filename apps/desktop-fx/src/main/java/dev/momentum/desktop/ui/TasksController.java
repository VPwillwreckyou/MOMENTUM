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
 * Tasks screen: every task across all goals, with completion toggle,
 * completed/active filter and search. Mutations write to SQLite.
 */
public class TasksController implements PageView {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final ListView<Map<String, Object>> list = new ListView<>();
    private final ToggleGroup statusGroup = new ToggleGroup();
    private final TextField search = new TextField();

    public TasksController(ServiceHost host, ToastHost toasts, ModalOverlay modals,
                           NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), buildToolbar(), listSection());

        list.getStyleClass().add("task-list-view");
        list.setPlaceholder(new Label("No tasks yet."));
        list.setCellFactory(l -> new TaskRowCell());
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
        Label title = new Label("Tasks");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(10, title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node buildToolbar() {
        RadioButton all = new RadioButton("All tasks");
        RadioButton active = new RadioButton("Active");
        RadioButton completed = new RadioButton("Completed");
        all.setToggleGroup(statusGroup);
        active.setToggleGroup(statusGroup);
        completed.setToggleGroup(statusGroup);
        all.setUserData(null);
        active.setUserData("active");
        completed.setUserData("completed");
        all.setSelected(true);
        statusGroup.getToggles().forEach(t -> t.selectedProperty().addListener((o, old, nw) -> load()));

        HBox statusBar = new HBox(10, all, active, completed);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText("Search tasks\u2026");
        search.textProperty().addListener((o, old, nw) -> load());
        search.setPrefWidth(260);

        HBox toolbar = new HBox(10, statusBar, search);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar");
        HBox.setHgrow(search, Priority.ALWAYS);
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
        String status = selectedStatus();
        String query = (search.getText() == null ? "" : search.getText().trim().toLowerCase());

        final AtomicReference<List<Map<String, Object>>> tasks = new AtomicReference<>(new ArrayList<>());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        List<Map<String, Object>> all = new ArrayList<>();
                        for (Map<String, Object> goal : repository.listGoals(null)) {
                            String goalId = String.valueOf(goal.get("id"));
                            List<Map<String, Object>> goalTasks = repository.listTasks(goalId, null);
                            for (Map<String, Object> t : goalTasks) {
                                t.put("goalId", goalId);
                                t.put("goalTitle", valueOr(goal, "title", "Untitled"));
                            }
                            all.addAll(goalTasks);
                        }
                        tasks.set(all.stream()
                                .filter(t -> statusMatches(t, status))
                                .filter(t -> matchesQuery(t, query))
                                .collect(Collectors.toList()));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> list.getItems().setAll(tasks.get())));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load tasks: " + task.getException().getMessage(),
                        ToastHost.Tone.ERROR)));
        new Thread(task, "tasks-loader").start();
    }

    private String selectedStatus() {
        Toggle selected = statusGroup.getSelectedToggle();
        Object data = selected == null ? null : selected.getUserData();
        return data == null ? null : String.valueOf(data);
    }

    private boolean statusMatches(Map<String, Object> t, String status) {
        if (status == null) return true;
        boolean completed = Boolean.TRUE.equals(t.get("completed"));
        return "active".equals(status) ? !completed : completed;
    }

    private boolean matchesQuery(Map<String, Object> t, String query) {
        if (query.isEmpty()) return true;
        return (valueOr(t, "title", "") + " " + valueOr(t, "notes", "") + " "
                + valueOr(t, "goalTitle", "")).toLowerCase().contains(query);
    }

    /* ---------------------------------------------------------------- cell */

    private final class TaskRowCell extends ListCell<Map<String, Object>> {
        private final CheckBox completed = new CheckBox();
        private final Label title = new Label();
        private final Label meta = new Label();
        private final Button delete = new Button("Delete");

        private TaskRowCell() {
            completed.getStyleClass().add("task-check");
            title.getStyleClass().add("task-title");
            meta.getStyleClass().add("task-meta");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");

            HBox row = new HBox(10, completed, title, meta, delete);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(title, Priority.ALWAYS);
            row.getStyleClass().add("task-row");

            completed.setOnAction(event -> toggleTask());
            delete.setOnAction(event -> deleteTask());
            setGraphic(row);
        }

        @Override
        protected void updateItem(Map<String, Object> t, boolean empty) {
            super.updateItem(t, empty);
            if (empty || t == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String id = String.valueOf(t.get("id"));
            completed.setSelected(Boolean.TRUE.equals(t.get("completed")));
            completed.setUserData(id);
            title.setText(valueOr(t, "title", "Untitled"));
            meta.setText(valueOr(t, "goalTitle", "") + "  \u2022  "
                    + (valueOr(t, "dueDate", "").isBlank() ? "\u2014"
                    : formatDate(valueOr(t, "dueDate", ""))));
            delete.setUserData(id);
            setOpacity(completed.isSelected() ? 0.55 : 1.0);
            setText(null);
            setGraphic(completed.isSelected() ? null : null); // row is the graphic
        }

        private void toggleTask() {
            String id = String.valueOf(completed.getUserData());
            Map<String, Object> t = findTask(id);
            if (t == null) return;
            boolean completing = !Boolean.TRUE.equals(t.get("completed"));
            try {
                host.withRepository(repository -> {
                    try {
                        Map<String, Object> patch = new LinkedHashMap<>(t);
                        patch.put("completed", completing);
                        patch.put("completedAt", completing ? Instant.now().toString() : null);
                        repository.upsertTask(patch);
                        repository.recordActivity(completing ? "task-completed" : "task-reopened",
                                String.valueOf(t.get("goalId")), id,
                                completing ? "Completed task" : "Reopened task");
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                toasts.show(completing ? "Task completed" : "Task reopened", ToastHost.Tone.SUCCESS);
                load();
            } catch (RuntimeException e) {
                toasts.show("Could not update task: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
            }
        }

        private void deleteTask() {
            String id = String.valueOf(delete.getUserData());
            Map<String, Object> t = findTask(id);
            if (t == null) return;
            modals.show("Delete task?",
                    new Label("Delete task \u201c" + valueOr(t, "title", "this task") + "\u201d?"),
                    new ModalOverlay.Action("Delete", ModalOverlay.Tone.DANGER, () -> {
                        try {
                            host.withRepository(repository -> {
                                try {
                                    repository.deleteTask(id);
                                    repository.recordActivity(null, String.valueOf(t.get("goalId")), id,
                                            "Deleted task");
                                    return null;
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            toasts.show("Task deleted", ToastHost.Tone.SUCCESS);
                            load();
                        } catch (RuntimeException e) {
                            toasts.show("Could not delete task: " + e.getCause().getMessage(),
                                    ToastHost.Tone.ERROR);
                        }
                    }),
                    new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
        }

        private Map<String, Object> findTask(String id) {
            for (Map<String, Object> t : list.getItems()) {
                if (id.equals(String.valueOf(t.get("id")))) return t;
            }
            return null;
        }

        private String formatDate(String iso) {
            try {
                return java.time.Instant.parse(iso)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DAY);
            } catch (Exception e) {
                return iso;
            }
        }
    }

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
