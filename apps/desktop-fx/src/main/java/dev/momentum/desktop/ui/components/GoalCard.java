package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Reusable goal row: title, status chip, category, updated date and action
 * buttons (view / edit / toggle completion / archive / delete). Used by the
 * dashboard and the goals list.
 */
public final class GoalCard {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;

    private final HBox row;
    private final Label title;
    private final Label meta;
    private final Label statusChip;
    private final Button edit;
    private final Button toggle;
    private final Button archive;
    private final Button trash;

    private Map<String, Object> goal;
    private Runnable onChanged;
    private Runnable onRestore;

    public GoalCard(ServiceHost host, ToastHost toasts, ModalOverlay modals) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;

        row = new HBox(12);
        row.getStyleClass().add("goal-row");
        row.setAlignment(Pos.CENTER_LEFT);

        title = new Label();
        title.getStyleClass().add("goal-title");
        title.setWrapText(true);

        meta = new Label();
        meta.getStyleClass().add("goal-meta");

        statusChip = new Label();
        statusChip.getStyleClass().add("status-chip");

        edit = new Button("Edit");
        edit.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        toggle = new Button();
        toggle.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        archive = new Button("Archive");
        archive.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        trash = new Button("Delete");
        trash.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        trash.getStyleClass().add("btn-danger");

        HBox actions = new HBox(8, edit, toggle, archive, trash);
        actions.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(title, meta, statusChip, actions);
        HBox.setHgrow(title, Priority.ALWAYS);
    }

    public Node node() {
        return row;
    }

    public void bind(Map<String, Object> goal, Runnable onView, Runnable onEdit, Runnable onChanged,
                     Runnable onRestore) {
        this.goal = goal;
        this.onChanged = onChanged;
        this.onRestore = onRestore;
        String titleText = valueOr(goal, "title", "Untitled");
        String status = valueOr(goal, "status", "active");
        String category = valueOr(goal, "categoryId", "\u2014");
        String updated = valueOr(goal, "updatedAt", "");

        title.setText(titleText);
        meta.setText(category + "  \u2022  " + (updated.isBlank() ? "\u2014" : formatDate(updated)));
        statusChip.setText(capitalize(status));
        switch (status) {
            case "completed" -> statusChip.setStyle("-fx-text-fill: #34d399; -fx-background-color: rgba(52, 211, 153, 0.12);");
            case "archived"  -> statusChip.setStyle("-fx-text-fill: #94a3b8; -fx-background-color: rgba(148, 163, 184, 0.12);");
            default          -> statusChip.setStyle("-fx-text-fill: -accent; -fx-background-color: -accent-faint;");
        }
        toggle.setText("completed".equals(status) ? "Reopen" : "Complete");
        boolean isTrashed = isTrashed(goal);
        archive.setText(isTrashed ? "Restore" : "Archive");
        archive.setOnAction(event -> {
            if ("archived".equals(status)) {
                restoreGoal();
            } else {
                archiveGoal();
            }
        });

        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1 && !(event.getTarget() instanceof Button) && onView != null) {
                onView.run();
            }
        });
        edit.setOnAction(event -> { if (onEdit != null) onEdit.run(); });
        toggle.setOnAction(event -> toggleCompletion());

        if (isTrashed) {
            trash.setText("Permanently delete");
            trash.setOnAction(event -> purgeGoal());
        } else {
            trash.setText("Delete");
            trash.setOnAction(event -> trashGoal());
        }
    }

    private String formatDate(String iso) {
        try {
            return Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY);
        } catch (Exception e) {
            return iso;
        }
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private void runOnChanged() {
        if (onChanged != null) onChanged.run();
    }

    private boolean isTrashed(Map<String, Object> g) {
        String deletedAt = valueOr(g, "deletedAt", "");
        return deletedAt != null && !deletedAt.isEmpty();
    }

    private void purgeGoal() {
        if (goal == null) return;
        String id = String.valueOf(goal.get("id"));
        modals.show("Permanently delete goal?",
                new Label("Permanently delete \u201c" + valueOr(goal, "title", "this goal") + "\u201d? "
                        + "This cannot be undone. All tasks, milestones, notes, and attachments "
                        + "associated with this goal will be lost."),
                new ModalOverlay.Action("Delete permanently", ModalOverlay.Tone.DANGER, () -> {
                    try {
                        host.withRepository(repository -> {
                            try {
                                repository.purgeGoal(id);
                                repository.recordActivity("goal-purged", id, null,
                                        "Permanently deleted goal");
                                return null;
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        toasts.show("Goal permanently deleted", ToastHost.Tone.SUCCESS);
                        runOnChanged();
                    } catch (RuntimeException e) {
                        toasts.show("Could not delete goal: " + e.getCause().getMessage(),
                                ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }

    private void restoreGoal() {
        if (goal == null) return;
        String id = String.valueOf(goal.get("id"));
        try {
            host.withRepository(repository -> {
                try {
                    repository.restoreGoal(id);
                    repository.recordActivity("goal-restored", id, null, "Restored goal");
                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            toasts.show("Goal restored", ToastHost.Tone.SUCCESS);
            runOnChanged();
        } catch (RuntimeException e) {
            toasts.show("Could not restore goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
        }
    }

    /* ---------------------------------------------------------------- actions */

    private void toggleCompletion() {
        if (goal == null) return;
        String id = String.valueOf(goal.get("id"));
        String status = String.valueOf(goal.get("status"));
        boolean completing = !"completed".equals(status);
        try {
            host.withRepository(repository -> {
                Map<String, Object> patch = new LinkedHashMap<>(goal);
                patch.put("status", completing ? "completed" : "active");
                patch.put("completedAt", completing ? Instant.now().toString() : null);
                try {
                    repository.upsertGoal(patch);
                    repository.recordActivity(completing ? "goal-completed" : "goal-reopened", id, null,
                            completing ? "Completed goal" : "Reopened goal");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            toasts.show(completing ? "Goal completed" : "Goal reopened", ToastHost.Tone.SUCCESS);
            runOnChanged();
        } catch (RuntimeException e) {
            toasts.show("Could not update goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
        }
    }

    private void archiveGoal() {
        if (goal == null) return;
        String id = String.valueOf(goal.get("id"));
        modals.show("Archive goal?",
                new Label("Archive \u201c" + valueOr(goal, "title", "this goal") + "\u201d? "
                        + "It will move out of your active goals but stay restorable."),
                new ModalOverlay.Action("Archive", ModalOverlay.Tone.DANGER, () -> {
                    try {
                        host.withRepository(repository -> {
                            try {
                                repository.archiveGoal(id);
                                repository.recordActivity("goal-archived", id, null, "Archived goal");
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                            return null;
                        });
                        toasts.show("Goal archived", ToastHost.Tone.SUCCESS);
                        runOnChanged();
                    } catch (RuntimeException e) {
                        toasts.show("Could not archive goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }

    private void trashGoal() {
        if (goal == null) return;
        String id = String.valueOf(goal.get("id"));
        modals.show("Delete goal?",
                new Label("Move \u201c" + valueOr(goal, "title", "this goal") + "\u201d to the trash? "
                        + "It can be restored or permanently deleted from the Trash screen."),
                new ModalOverlay.Action("Delete", ModalOverlay.Tone.DANGER, () -> {
                    try {
                        host.withRepository(repository -> {
                            try {
                                repository.trashGoal(id);
                                repository.recordActivity("goal-trashed", id, null, "Moved goal to trash");
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                            return null;
                        });
                        toasts.show("Goal moved to trash", ToastHost.Tone.SUCCESS);
                        runOnChanged();
                    } catch (RuntimeException e) {
                        toasts.show("Could not delete goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }
}
