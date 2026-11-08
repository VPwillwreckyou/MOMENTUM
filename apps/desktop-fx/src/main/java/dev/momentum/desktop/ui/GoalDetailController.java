package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import dev.momentum.desktop.ui.ThemeManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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
 * Goal detail screen: goal header (status chip, category, dates), milestones
 * with completion toggles, tasks list, and actions (edit / archive / trash).
 * Everything reads from / writes to the real SQLite database.
 */
public class GoalDetailController implements PageView {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final AtomicReference<Map<String, Object>> goal = new AtomicReference<>(null);

    private final Label title = new Label();
    private final Label statusChip = new Label();
    private final Label meta = new Label();
    private final Label notesLabel = new Label();
    private final ListView<Map<String, Object>> milestonesList = new ListView<>();
    private final ListView<Map<String, Object>> tasksList = new ListView<>();

    public GoalDetailController(ServiceHost host, ThemeManager themes, ToastHost toasts,
                                ModalOverlay modals, NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(headerBar(), goalCard(), milestonesSection(), tasksSection());

        milestonesList.getStyleClass().add("milestone-list");
        milestonesList.setCellFactory(l -> new MilestoneCell());
        tasksList.getStyleClass().add("task-list");
        tasksList.setCellFactory(l -> new TaskCell());
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        String goalId = parameter == null ? null : String.valueOf(parameter);
        if (goalId != null) loadGoal(goalId);
    }

    /* ---------------------------------------------------------------- layout */

    private Node headerBar() {
        Button back = new Button("Back to goals");
        back.getStyleClass().add("btn-ghost");
        back.setOnAction(event -> navigation.select(NavDestination.GOALS));
        Button edit = new Button("Edit goal");
        edit.getStyleClass().addAll("btn", "btn-primary");
        edit.setOnAction(event -> {
            Map<String, Object> g = goal.get();
            if (g != null) navigation.select(NavDestination.GOAL_EDIT, g.get("id"));
        });
        HBox bar = new HBox(10, back, edit);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("page-head");
        return bar;
    }

    private Node goalCard() {
        title.getStyleClass().add("detail-title");
        statusChip.getStyleClass().add("status-chip");
        meta.getStyleClass().add("detail-meta");
        notesLabel.getStyleClass().add("detail-notes");
        notesLabel.setWrapText(true);

        Button complete = new Button("Mark complete");
        complete.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        complete.setOnAction(event -> toggleComplete(true));
        Button reopen = new Button("Reopen");
        reopen.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        reopen.setOnAction(event -> toggleComplete(false));
        Button archive = new Button("Archive goal");
        archive.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        archive.setOnAction(event -> archiveGoal());
        Button trash = new Button("Delete goal");
        trash.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");
        trash.setOnAction(event -> trashGoal());

        HBox actions = new HBox(8, complete, reopen, archive, trash);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, new HBox(10, title, statusChip), meta, actions, notesLabel);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node milestonesSection() {
        Label sectionTitle = new Label("Milestones");
        sectionTitle.getStyleClass().add("section-title");
        VBox section = new VBox(10, sectionTitle, milestonesList);
        section.getStyleClass().add("card");
        return section;
    }

    private Node tasksSection() {
        Label sectionTitle = new Label("Tasks");
        sectionTitle.getStyleClass().add("section-title");
        VBox section = new VBox(10, sectionTitle, tasksList);
        section.getStyleClass().add("card");
        return section;
    }

    /* ---------------------------------------------------------------- data */

    private void loadGoal(String goalId) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        Map<String, Object> g = repository.getGoal(goalId).orElse(null);
                        if (g == null) throw new IllegalStateException("Goal not found.");
                        goal.set(g);
                        List<Map<String, Object>> ms = repository.listMilestones(goalId);
                        ms.forEach(m -> m.put("goalId", goalId));
                        List<Map<String, Object>> ts = repository.listTasks(goalId, null);
                        milestonesList.getItems().setAll(ms);
                        tasksList.getItems().setAll(ts);
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
        new Thread(task, "goal-detail-loader").start();
    }

    private void refreshUI() {
        Map<String, Object> g = goal.get();
        if (g == null) return;
        title.setText(valueOr(g, "title", "Untitled"));
        String status = valueOr(g, "status", "active");
        statusChip.setText(capitalize(status));
        switch (status) {
            case "completed" -> statusChip.setStyle("-fx-text-fill: #34d399; -fx-background-color: rgba(52, 211, 153, 0.12);");
            case "archived"  -> statusChip.setStyle("-fx-text-fill: #94a3b8; -fx-background-color: rgba(148, 163, 184, 0.12);");
            default          -> statusChip.setStyle("-fx-text-fill: -accent; -fx-background-color: -accent-faint;");
        }
        String updated = valueOr(g, "updatedAt", "");
        meta.setText((updated.isBlank() ? "\u2014" : formatDate(updated))
                + "  \u2022  " + (valueOr(g, "categoryId", "\u2014").equals("\u2014") ? "No category"
                : valueOr(g, "categoryId", "\u2014")));
        notesLabel.setText(valueOr(g, "notes", "").isBlank()
                ? "No notes yet. Edit this goal to add notes."
                : valueOr(g, "notes", ""));
    }

    /* ---------------------------------------------------------------- actions */

    private void toggleComplete(boolean completing) {
        Map<String, Object> g = goal.get();
        if (g == null) return;
        String id = String.valueOf(g.get("id"));
        try {
            host.withRepository(repository -> {
                try {
                    Map<String, Object> patch = new LinkedHashMap<>(g);
                    patch.put("status", completing ? "completed" : "active");
                    patch.put("completedAt", completing ? Instant.now().toString() : null);
                    repository.upsertGoal(patch);
                    repository.recordActivity(completing ? "goal-completed" : "goal-reopened", id, null,
                            completing ? "Completed goal" : "Reopened goal");
                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            toasts.show(completing ? "Goal completed" : "Goal reopened", ToastHost.Tone.SUCCESS);
            loadGoal(id);
        } catch (RuntimeException e) {
            toasts.show("Could not update goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
        }
    }

    private void archiveGoal() {
        Map<String, Object> g = goal.get();
        if (g == null) return;
        String id = String.valueOf(g.get("id"));
        modals.show("Archive goal?",
                new Label("Archive \u201c" + valueOr(g, "title", "this goal") + "\u201d? "
                        + "It will move out of your active goals but stay restorable."),
                new ModalOverlay.Action("Archive", ModalOverlay.Tone.DANGER, () -> {
                    try {
                        host.withRepository(repository -> {
                            try {
                                repository.archiveGoal(id);
                                repository.recordActivity("goal-archived", id, null, "Archived goal");
                                return null;
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        toasts.show("Goal archived", ToastHost.Tone.SUCCESS);
                        navigation.select(NavDestination.GOALS);
                    } catch (RuntimeException e) {
                        toasts.show("Could not archive goal: " + e.getCause().getMessage(),
                                ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }

    private void trashGoal() {
        Map<String, Object> g = goal.get();
        if (g == null) return;
        String id = String.valueOf(g.get("id"));
        modals.show("Delete goal?",
                new Label("Move \u201c" + valueOr(g, "title", "this goal") + "\u201d to the trash? "
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
                        toasts.show("Could not delete goal: " + e.getCause().getMessage(),
                                ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }

    /* ---------------------------------------------------------------- helpers */

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String formatDate(String iso) {
        try {
            return java.time.Instant.parse(iso)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DAY);
        } catch (Exception e) {
            return iso;
        }
    }

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    /* ---------------------------------------------------------------- cells */

    private final class MilestoneCell extends ListCell<Map<String, Object>> {
        private final CheckBox completed = new CheckBox();
        private final Label title = new Label();
        private final Label due = new Label();
        private final Button delete = new Button("Delete");

        private MilestoneCell() {
            completed.getStyleClass().add("milestone-check");
            title.getStyleClass().add("milestone-title");
            due.getStyleClass().add("milestone-due");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");

            HBox row = new HBox(10, completed, title, due, delete);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(title, Priority.ALWAYS);
            row.getStyleClass().add("milestone-row");

            completed.setOnAction(event -> toggleMilestone());
            delete.setOnAction(event -> deleteMilestone());
            setGraphic(row);
        }

        @Override
        protected void updateItem(Map<String, Object> m, boolean empty) {
            super.updateItem(m, empty);
            if (empty || m == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String id = String.valueOf(m.get("id"));
            completed.setSelected(Boolean.TRUE.equals(m.get("completed")));
            completed.setUserData(id);
            title.setText(valueOr(m, "title", "Untitled"));
            String dueDate = valueOr(m, "dueDate", "");
            due.setText(dueDate.isBlank() ? "\u2014" : formatDate(dueDate));
            delete.setUserData(id);
            setText(null);
            setOpacity(completed.isSelected() ? 0.55 : 1.0);
        }

        private void toggleMilestone() {
            String id = String.valueOf(completed.getUserData());
            Map<String, Object> m = findMilestone(id);
            if (m == null) return;
            boolean completing = !Boolean.TRUE.equals(m.get("completed"));
            try {
                host.withRepository(repository -> {
                    try {
                        Map<String, Object> patch = new LinkedHashMap<>(m);
                        patch.put("completed", completing);
                        patch.put("completedAt", completing ? Instant.now().toString() : null);
                        repository.upsertMilestone(id, patch);
                        repository.recordActivity("milestone-completed", String.valueOf(m.get("goalId")), id,
                                completing ? "Completed milestone" : "Reopened milestone");
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                toasts.show(completing ? "Milestone completed" : "Milestone reopened", ToastHost.Tone.SUCCESS);
                loadGoal(String.valueOf(m.get("goalId")));
            } catch (RuntimeException e) {
                toasts.show("Could not update milestone: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
            }
        }

        private void deleteMilestone() {
            String id = String.valueOf(delete.getUserData());
            Map<String, Object> m = findMilestone(id);
            if (m == null) return;
            modals.show("Delete milestone?",
                    new Label("Delete milestone \u201c" + valueOr(m, "title", "this milestone") + "\u201d?"),
                    new ModalOverlay.Action("Delete", ModalOverlay.Tone.DANGER, () -> {
                        try {
                            host.withRepository(repository -> {
                                try {
                                    repository.deleteMilestone(id);
                                    repository.recordActivity(null, String.valueOf(m.get("goalId")), null,
                                            "Deleted milestone");
                                    return null;
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            toasts.show("Milestone deleted", ToastHost.Tone.SUCCESS);
                            loadGoal(String.valueOf(m.get("goalId")));
                        } catch (RuntimeException e) {
                            toasts.show("Could not delete milestone: " + e.getCause().getMessage(),
                                    ToastHost.Tone.ERROR);
                        }
                    }),
                    new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
        }

        private Map<String, Object> findMilestone(String id) {
            for (Map<String, Object> m : milestonesList.getItems()) {
                if (id.equals(String.valueOf(m.get("id")))) return m;
            }
            return null;
        }
    }

    private final class TaskCell extends ListCell<Map<String, Object>> {
        private final CheckBox completed = new CheckBox();
        private final Label title = new Label();
        private final Label due = new Label();
        private final Button delete = new Button("Delete");

        private TaskCell() {
            completed.getStyleClass().add("task-check");
            title.getStyleClass().add("task-title");
            due.getStyleClass().add("task-due");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");

            HBox row = new HBox(10, completed, title, due, delete);
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
            String dueDate = valueOr(t, "dueDate", "");
            due.setText(dueDate.isBlank() ? "\u2014" : formatDate(dueDate));
            delete.setUserData(id);
            setOpacity(completed.isSelected() ? 0.55 : 1.0);
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
                loadGoal(String.valueOf(t.get("goalId")));
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
                            loadGoal(String.valueOf(t.get("goalId")));
                        } catch (RuntimeException e) {
                            toasts.show("Could not delete task: " + e.getCause().getMessage(),
                                    ToastHost.Tone.ERROR);
                        }
                    }),
                    new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
        }

        private Map<String, Object> findTask(String id) {
            for (Map<String, Object> t : tasksList.getItems()) {
                if (id.equals(String.valueOf(t.get("id")))) return t;
            }
            return null;
        }
    }
}
