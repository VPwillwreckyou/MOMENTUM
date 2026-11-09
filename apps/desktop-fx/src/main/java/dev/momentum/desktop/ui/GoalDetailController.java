package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import dev.momentum.data.AttachmentStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Goal detail screen: goal header (status chip, category, dates), milestones
 * with completion toggles, tasks list, attachments, and actions (edit /
 * archive / trash). Everything reads from / writes to the real SQLite
 * database.
 */
public class GoalDetailController implements PageView {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;
    private final AttachmentStore attachmentStore;

    private final VBox root = new VBox(16);
    private final AtomicReference<Map<String, Object>> goal = new AtomicReference<>(null);

    private final Label title = new Label();
    private final Label statusChip = new Label();
    private final Label meta = new Label();
    private final Label notesLabel = new Label();
    private final ListView<Map<String, Object>> milestonesList = new ListView<>();
    private final ListView<Map<String, Object>> tasksList = new ListView<>();
    private final ListView<Map<String, Object>> attachmentsList = new ListView<>();

    public GoalDetailController(ServiceHost host, ThemeManager themes, ToastHost toasts,
                                ModalOverlay modals, NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;
        this.attachmentStore = new AttachmentStore(host.dataDirectory());

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(headerBar(), goalCard(), milestonesSection(), tasksSection());

        milestonesList.getStyleClass().add("milestone-list");
        milestonesList.setCellFactory(l -> new MilestoneCell());
        tasksList.getStyleClass().add("task-list");
        tasksList.setCellFactory(l -> new TaskCell());
        attachmentsList.getStyleClass().add("goal-list");
        attachmentsList.setCellFactory(l -> new AttachmentCell());
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

        Label attachmentsTitle = new Label("Attachments");
        attachmentsTitle.getStyleClass().add("card-title");
        attachmentsList.getStyleClass().add("goal-list");
        attachmentsList.setCellFactory(l -> new AttachmentCell());
        attachmentsList.setPrefHeight(120);
        VBox attachmentsSection = new VBox(8, attachmentsTitle, attachmentsList);
        attachmentsSection.getStyleClass().add("card");

        Button addAttachment = new Button("Add attachment");
        addAttachment.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        addAttachment.setOnAction(event -> addAttachment());

        HBox attachmentsHeader = new HBox(8, attachmentsTitle, addAttachment);
        attachmentsHeader.setAlignment(Pos.CENTER_LEFT);

        VBox attachmentsPanel = new VBox(8, attachmentsHeader, attachmentsList);
        attachmentsPanel.getStyleClass().add("card");

        card.getChildren().addAll(new HBox(10, title, statusChip), meta, actions, notesLabel, attachmentsPanel);
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
                        Map<String, Object[]> attsMap = repository.listAttachments(goalId);
                        List<Map<String, Object>> atts = new ArrayList<>();
                        for (Map.Entry<String, Object[]> e : attsMap.entrySet()) {
                            Map<String, Object> att = new LinkedHashMap<>();
                            att.put("id", e.getKey());
                            Object[] arr = e.getValue();
                            att.put("goalId", arr[0]);
                            att.put("name", arr[1]);
                            att.put("mimeType", arr[2]);
                            att.put("size", arr[3]);
                            att.put("file_path", arr[4]);
                            att.put("createdAt", arr[5]);
                            atts.add(att);
                        }
                        attachmentsList.getItems().setAll(atts);
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

    /* ---------------------------------------------------------------- attachment */

    private void addAttachment() {
        Map<String, Object> g = goal.get();
        if (g == null) return;
        String goalId = String.valueOf(g.get("id"));

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach file to goal");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        Stage owner = (Stage) root.getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String id = UUID.randomUUID().toString();
            String relativePath = attachmentStore.save(id, file.getName(), bytes);
            String detectedMimeType = java.nio.file.Files.probeContentType(file.toPath());
            final String mimeType = detectedMimeType != null ? detectedMimeType : "application/octet-stream";

            host.withRepository(repository -> {
                try {
                    repository.insertAttachment(goalId, id, file.getName(),
                            mimeType, file.length(), relativePath, Instant.now().toString());
                    repository.recordActivity("attachment-added", goalId, id, "Added attachment");
                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

            toasts.show("Attachment added", ToastHost.Tone.SUCCESS);
            loadGoal(goalId);
        } catch (Exception e) {
            toasts.show("Could not attach file: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = String.valueOf("KMGTPE".charAt(exp - 1));
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /* ---------------------------------------------------------------- cells */

    private final class MilestoneCell extends ListCell<Map<String, Object>> {
        private final CheckBox completed = new CheckBox();
        private final Label title = new Label();
        private final Label due = new Label();
        private final Button delete = new Button("Delete");
        private final HBox row = new HBox(10, completed, title, due, delete);

        private MilestoneCell() {
            completed.getStyleClass().add("milestone-check");
            title.getStyleClass().add("milestone-title");
            due.getStyleClass().add("milestone-due");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");

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
            setGraphic(row);
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
        private final HBox row = new HBox(10, completed, title, due, delete);

        private TaskCell() {
            completed.getStyleClass().add("task-check");
            title.getStyleClass().add("task-title");
            due.getStyleClass().add("task-due");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");

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
            setText(null);
            setGraphic(row);
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

    private final class AttachmentCell extends ListCell<Map<String, Object>> {
        private final Label nameLabel = new Label();
        private final Label infoLabel = new Label();
        private final Button openButton = new Button("Open");
        private final Button deleteButton = new Button("Delete");
        private final HBox row = new HBox(10, nameLabel, infoLabel, openButton, deleteButton);

        private AttachmentCell() {
            nameLabel.getStyleClass().add("task-title");
            infoLabel.getStyleClass().add("task-meta");
            openButton.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            deleteButton.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");

            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            row.getStyleClass().add("task-row");

            openButton.setOnAction(event -> openAttachment(getItem()));
            deleteButton.setOnAction(event -> deleteAttachment(getItem()));
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
            String id = String.valueOf(a.get("id"));
            nameLabel.setText(valueOr(a, "name", "Untitled"));
            String sizeStr = valueOr(a, "size", "0");
            long sizeBytes = 0;
            try {
                sizeBytes = Long.parseLong(sizeStr);
            } catch (NumberFormatException ignored) {}
            infoLabel.setText(valueOr(a, "mimeType", "") + "  \u2022  " + formatFileSize(sizeBytes));
            deleteButton.setUserData(id);
            setText(null);
            setGraphic(row);
        }

        private void openAttachment(Map<String, Object> a) {
            String id = String.valueOf(a.get("id"));
            String relPath = valueOr(a, "file_path", "");
            if (relPath.isEmpty()) return;
            try {
                Path filePath = attachmentStore.resolveRelative(relPath);
                if (filePath.toFile().exists()) {
                    java.awt.Desktop.getDesktop().open(filePath.toFile());
                } else {
                    toasts.show("Attachment file not found", ToastHost.Tone.ERROR);
                }
            } catch (Exception e) {
                toasts.show("Could not open attachment: " + e.getMessage(), ToastHost.Tone.ERROR);
            }
        }

        private void deleteAttachment(Map<String, Object> a) {
            String id = String.valueOf(a.get("id"));
            String relPath = valueOr(a, "file_path", "");
            modals.show("Delete attachment?",
                    new Label("Delete attachment \u201c" + valueOr(a, "name", "this file") + "\u201d?"),
                    new ModalOverlay.Action("Delete", ModalOverlay.Tone.DANGER, () -> {
                        try {
                            try {
                                attachmentStore.delete(relPath);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            host.withRepository(repository -> {
                                try {
                                    repository.deleteAttachment(id);
                                    repository.recordActivity("attachment-deleted",
                                            String.valueOf(a.get("goalId")), id, "Deleted attachment");
                                    return null;
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            toasts.show("Attachment deleted", ToastHost.Tone.SUCCESS);
                            loadGoal(String.valueOf(a.get("goalId")));
                        } catch (RuntimeException e) {
                            toasts.show("Could not delete attachment: " + e.getCause().getMessage(),
                                    ToastHost.Tone.ERROR);
                        }
                    }),
                    new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
        }
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

}