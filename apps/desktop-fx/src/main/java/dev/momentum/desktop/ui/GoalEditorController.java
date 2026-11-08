package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Goal creation and editing form. Validates input (title required), reads and
 * writes goals through the shared SQLite repository, and records an activity
 * for every create or edit.
 */
public class GoalEditorController implements PageView {

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final AtomicReference<Map<String, Object>> goal = new AtomicReference<>(null);

    private final TextField title = new TextField();
    private final TextArea description = new TextArea();
    private final TextArea notes = new TextArea();
    private final ComboBox<Map<String, Object>> category = new ComboBox<>();
    private final ComboBox<String> priority = new ComboBox<>();
    private final ComboBox<String> kind = new ComboBox<>();
    private final DatePicker startDate = new DatePicker();
    private final DatePicker deadline = new DatePicker();
    private final Label error = new Label();

    public GoalEditorController(ServiceHost host, ThemeManager themes, ToastHost toasts,
                                ModalOverlay modals, NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), form(), actions());

        title.getStyleClass().add("form-field");
        description.getStyleClass().add("form-field");
        notes.getStyleClass().add("form-field");
        description.setPrefRowCount(4);
        notes.setPrefRowCount(3);
        error.getStyleClass().add("field-error");
        error.setVisible(false);

        priority.getItems().addAll("low", "medium", "high", "critical");
        priority.setValue("medium");
        kind.getItems().addAll("one-time", "recurring");
        kind.setValue("one-time");

        category.setPromptText("No category");
        category.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Map<String, Object> c) {
                return c == null ? "" : String.valueOf(c.get("name"));
            }
            @Override public Map<String, Object> fromString(String s) {
                return null;
            }
        });
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        String goalId = parameter == null ? null : String.valueOf(parameter);
        if (goalId == null) {
            goal.set(newGoalMap());
        } else {
            loadGoal(goalId);
        }
        refreshUI();
    }

    private void loadGoal(String goalId) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        Map<String, Object> g = repository.getGoal(goalId).orElse(null);
                        if (g == null) {
                            throw new IllegalStateException("Goal not found.");
                        }
                        goal.set(g);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(this::refreshUI));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load goal: " + task.getException().getMessage(),
                        ToastHost.Tone.ERROR)));
        new Thread(task, "goal-loader").start();
    }

    /* ---------------------------------------------------------------- layout */

    private Node header() {
        Label titleLabel = new Label(goal.get() == null ? "New goal" : "Edit goal");
        titleLabel.getStyleClass().add("page-title");
        Button back = new Button("Back to goals");
        back.getStyleClass().add("btn-ghost");
        back.setOnAction(event -> navigation.select(NavDestination.GOALS));
        HBox header = new HBox(10, titleLabel, back);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node form() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.getStyleClass().add("form-grid");

        grid.add(field("Title", title, true), 0, 0);
        GridPane.setColumnSpan(title, 2);
        grid.add(field("Description", description, false), 0, 1);
        GridPane.setColumnSpan(description, 2);
        grid.add(field("Notes", notes, false), 0, 2);
        GridPane.setColumnSpan(notes, 2);
        grid.add(field("Category", category, false), 0, 3);
        grid.add(field("Priority", priority, false), 1, 3);
        grid.add(field("Kind", kind, false), 0, 4);
        grid.add(field("Start date", startDate, false), 1, 4);
        grid.add(field("Deadline", deadline, false), 0, 5);
        GridPane.setColumnSpan(deadline, 2);

        grid.add(error, 0, 6);
        GridPane.setColumnSpan(error, 2);
        return grid;
    }

    private static Node field(String labelText, Node control, boolean required) {
        Label label = new Label(labelText + (required ? " *" : ""));
        label.getStyleClass().add("field-label");
        VBox box = new VBox(6, label, control);
        box.getStyleClass().add("form-field-box");
        return box;
    }

    private Node actions() {
        Button save = new Button("Save goal");
        save.getStyleClass().addAll("btn", "btn-primary");
        save.setOnAction(event -> saveGoal());

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-ghost");
        cancel.setOnAction(event -> navigation.select(NavDestination.GOALS));

        Button delete = new Button("Delete goal");
        delete.getStyleClass().addAll("btn", "btn-ghost", "btn-danger");
        delete.setOnAction(event -> deleteGoal());
        delete.setVisible(goal.get() != null && goal.get().get("deletedAt") == null);

        HBox bar = new HBox(10, save, cancel, delete);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.getStyleClass().add("action-bar");
        return bar;
    }

    private void refreshUI() {
        Map<String, Object> g = goal.get();
        boolean editing = g != null && g.get("id") != null;
        title.setText(valueOr(g, "title", ""));
        description.setText(valueOr(g, "description", ""));
        notes.setText(valueOr(g, "notes", ""));
        category.setValue(null);
        Object catId = g == null ? null : g.get("categoryId");
        // category selection re-applied asynchronously once categories load
        priority.setValue(valueOr(g, "priority", "medium"));
        kind.setValue(valueOr(g, "kind", "one-time"));
        startDate.setValue(parseDate(valueOr(g, "startDate", "")));
        deadline.setValue(parseDate(valueOr(g, "deadline", "")));
        error.setVisible(false);

        loadCategories(editing ? catId : null);
    }

    private void loadCategories(Object preselectId) {
        final AtomicReference<List<Map<String, Object>>> categories = new AtomicReference<>(new ArrayList<>());
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        categories.set(repository.listCategories());
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> {
            category.getItems().clear();
            for (Map<String, Object> c : categories.get()) {
                category.getItems().add(c);
            }
            if (preselectId != null) {
                category.getItems().filtered(c -> preselectId.equals(String.valueOf(c.get("id"))))
                        .stream().findFirst().ifPresent(category::setValue);
            }
        }));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load categories: " + task.getException().getMessage(),
                        ToastHost.Tone.ERROR)));
        new Thread(task, "category-loader").start();
    }

    private static LocalDate parseDate(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return LocalDate.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------------------------------------------------------------- save */

    private void saveGoal() {
        error.setVisible(false);
        String titleText = title.getText();
        if (titleText == null || titleText.isBlank()) {
            error.setText("Title is required.");
            error.setVisible(true);
            title.requestFocus();
            return;
        }

        Map<String, Object> g = new LinkedHashMap<>(goal.get() == null ? newGoalMap() : goal.get());
        boolean creating = g.get("id") == null;
        g.put("title", titleText);
        g.put("description", description.getText());
        g.put("notes", notes.getText());
        g.put("categoryId", category.getValue() == null ? null : String.valueOf(category.getValue().get("id")));
        g.put("priority", priority.getValue());
        g.put("kind", kind.getValue());
        g.put("startDate", startDate.getValue() == null ? null : startDate.getValue().toString());
        g.put("deadline", deadline.getValue() == null ? null : deadline.getValue().toString());
        g.put("updatedAt", Instant.now().toString());
        if (g.get("createdAt") == null) {
            g.put("createdAt", Instant.now().toString());
        }

        try {
            host.withRepository(repository -> {
                try {
                    if (creating) {
                        g.put("id", UUID.randomUUID().toString());
                    }
                    repository.upsertGoal(g);
                    repository.recordActivity(creating ? "goal-created" : "goal-edited",
                            String.valueOf(g.get("id")), null,
                            creating ? "Created goal" : "Edited goal");
                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            toasts.show("Could not save goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
            return;
        }

        toasts.show("Goal saved", ToastHost.Tone.SUCCESS);
        navigation.select(NavDestination.GOALS);
    }

    /* ---------------------------------------------------------------- delete */

    private void deleteGoal() {
        Map<String, Object> g = goal.get();
        if (g == null) return;
        String id = String.valueOf(g.get("id"));
        modals.show("Delete goal?",
                new Label("Move \u201c" + String.valueOf(g.get("title")) + "\u201d to the trash? "
                        + "It can be restored or permanently deleted from the Trash screen."),
                new ModalOverlay.Action("Delete", ModalOverlay.Tone.DANGER, () -> {
                    try {
                        host.withRepository(repository -> {
                            try {
                                repository.trashGoal(id);
                                repository.recordActivity("goal-trashed", id, null, "Moved goal to trash");
                                return null;
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        toasts.show("Goal moved to trash", ToastHost.Tone.SUCCESS);
                        navigation.select(NavDestination.GOALS);
                    } catch (RuntimeException e) {
                        toasts.show("Could not delete goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }

    private static Map<String, Object> newGoalMap() {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("status", "active");
        g.put("kind", "one-time");
        g.put("priority", "medium");
        g.put("aiGenerated", false);
        return g;
    }

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
