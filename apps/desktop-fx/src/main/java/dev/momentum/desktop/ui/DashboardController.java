package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.File;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Dashboard: live statistics from the SQLite database, the recent goals list,
 * and quick actions. Every number is read from the real database; nothing here
 * is placeholder data.
 */
public class DashboardController implements PageView {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final ServiceHost host;
    private final ThemeManager themes;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final ListView<Map<String, Object>> goalsList = new ListView<>();
    private final Label activeStat = new Label("\u2013");
    private final Label completedStat = new Label("\u2013");
    private final Label archivedStat = new Label("\u2013");
    private final Label categoriesStat = new Label("\u2013");

    public DashboardController(ServiceHost host, ThemeManager themes, ToastHost toasts,
                               ModalOverlay modals, NavigationManager navigation) {
        this.host = host;
        this.themes = themes;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), statsRow(), goalsSection());

        goalsList.getStyleClass().add("goal-list");
        goalsList.setPlaceholder(new Label("No goals yet - create your first goal to get started."));
        goalsList.setCellFactory(list -> new GoalCell());
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
        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-title");

        Button newGoal = new Button("New goal");
        newGoal.getStyleClass().addAll("btn", "btn-primary");
        newGoal.setOnAction(event -> navigation.select(NavDestination.GOAL_EDIT));

        Button importBackup = new Button("Import backup\u2026");
        importBackup.getStyleClass().addAll("btn", "btn-ghost");
        importBackup.setOnAction(event -> importBackup());

        HBox actions = new HBox(10, newGoal, importBackup);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(10, title, actions);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node statsRow() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.getStyleClass().add("stat-grid");

        Label activeName = new Label("Active goals");
        activeName.getStyleClass().add("stat-name");
        activeStat.getStyleClass().add("stat-value");

        Label completedName = new Label("Completed goals");
        completedName.getStyleClass().add("stat-name");
        completedStat.getStyleClass().add("stat-value");

        Label archivedName = new Label("Archived goals");
        archivedName.getStyleClass().add("stat-name");
        archivedStat.getStyleClass().add("stat-value");

        Label categoriesName = new Label("Categories");
        categoriesName.getStyleClass().add("stat-name");
        categoriesStat.getStyleClass().add("stat-value");

        grid.add(card(activeStat, activeName), 0, 0);
        grid.add(card(completedStat, completedName), 1, 0);
        grid.add(card(archivedStat, archivedName), 2, 0);
        grid.add(card(categoriesStat, categoriesName), 3, 0);

        HBox row = new HBox(grid);
        HBox.setHgrow(grid, Priority.ALWAYS);
        row.getStyleClass().add("card-row");
        return row;
    }

    private static HBox card(Label value, Label name) {
        VBox stat = new VBox(6, value, name);
        stat.getStyleClass().add("stat-card");
        HBox wrapper = new HBox(stat);
        wrapper.getStyleClass().add("card-row");
        return wrapper;
    }

    private Node goalsSection() {
        Label sectionTitle = new Label("Recent goals");
        sectionTitle.getStyleClass().add("card-title");

        Button viewAll = new Button("View all\u2026");
        viewAll.getStyleClass().add("btn-ghost");
        viewAll.setOnAction(event -> navigation.select(NavDestination.GOALS));

        HBox sectionHead = new HBox(10, sectionTitle, viewAll);
        sectionHead.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(sectionTitle, Priority.ALWAYS);

        VBox section = new VBox(10, sectionHead, goalsList);
        section.getStyleClass().add("card");
        VBox.setVgrow(goalsList, Priority.ALWAYS);
        return section;
    }

    /* ---------------------------------------------------------------- data */

    private void load() {
        final int[] active = {0};
        final int[] completed = {0};
        final int[] archived = {0};
        final AtomicReference<List<Map<String, Object>>> loadedGoals = new AtomicReference<>(new ArrayList<>());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        List<Map<String, Object>> loaded = repository.listGoals(null);
                        loadedGoals.set(loaded);
                        for (Map<String, Object> goal : loaded) {
                            switch (String.valueOf(goal.get("status"))) {
                                case "active" -> active[0]++;
                                case "completed" -> completed[0]++;
                                case "archived" -> archived[0]++;
                            }
                        }
                        categoriesStat.setText(String.valueOf(repository.count("categories")));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                return null;
            }
        };

        task.setOnSucceeded(event -> Platform.runLater(() -> {
            activeStat.setText(String.valueOf(active[0]));
            completedStat.setText(String.valueOf(completed[0]));
            archivedStat.setText(String.valueOf(archived[0]));
            goalsList.getItems().setAll(loadedGoals.get());
        }));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load dashboard data: " + task.getException().getMessage(),
                        ToastHost.Tone.ERROR)));

        new Thread(task, "dashboard-loader").start();
    }

    private void refresh() {
        load();
    }

    /* ---------------------------------------------------------------- goal cell */

    private final class GoalCell extends ListCell<Map<String, Object>> {

        private final HBox row = new HBox(12);
        private final Label title = new Label();
        private final Label meta = new Label();
        private final Label statusChip = new Label();
        private final Button edit = new Button("Edit");
        private final Button toggle = new Button();
        private final Button archive = new Button("Archive");
        private final Button trash = new Button("Delete");

        private GoalCell() {
            row.getStyleClass().add("goal-row");
            row.setAlignment(Pos.CENTER_LEFT);

            title.getStyleClass().add("goal-title");
            title.setWrapText(true);

            meta.getStyleClass().add("goal-meta");

            statusChip.getStyleClass().add("status-chip");

            edit.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            toggle.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            archive.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            trash.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            trash.getStyleClass().add("btn-danger");

            HBox actions = new HBox(8, edit, toggle, archive, trash);
            actions.setAlignment(Pos.CENTER_RIGHT);

            row.getChildren().addAll(title, meta, statusChip, actions);
            HBox.setHgrow(title, Priority.ALWAYS);

            row.setOnMouseClicked(event -> {
                Map<String, Object> goal = getItem();
                if (goal != null && event.getClickCount() == 1
                        && !(event.getTarget() instanceof Button)) {
                    navigation.select(NavDestination.GOAL_DETAIL, goal.get("id"));
                }
            });

            edit.setOnAction(event -> {
                Map<String, Object> goal = getItem();
                if (goal != null) navigation.select(NavDestination.GOAL_EDIT, goal.get("id"));
            });

            toggle.setOnAction(event -> {
                Map<String, Object> goal = getItem();
                if (goal == null) return;
                String id = String.valueOf(goal.get("id"));
                String status = String.valueOf(goal.get("status"));
                boolean completing = !"completed".equals(status);
                try {
                    host.withRepository(repository -> {
                        try {
                            Map<String, Object> patch = new LinkedHashMap<>(goal);
                            patch.put("status", completing ? "completed" : "active");
                            patch.put("completedAt", completing ? Instant.now().toString() : null);
                            repository.upsertGoal(patch);
                            repository.recordActivity(completing ? "goal-completed" : "goal-reopened", id, null,
                                    completing ? "Completed goal" : "Reopened goal");
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                        return null;
                    });
                    toasts.show(completing ? "Goal completed" : "Goal reopened", ToastHost.Tone.SUCCESS);
                    refresh();
                } catch (RuntimeException e) {
                    toasts.show("Could not update goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                }
            });

            archive.setOnAction(event -> archiveGoal(getItem()));
            trash.setOnAction(event -> trashGoal(getItem()));
        }

        @Override
        protected void updateItem(Map<String, Object> goal, boolean empty) {
            super.updateItem(goal, empty);
            if (empty || goal == null) {
                setText(null);
                setGraphic(null);
                return;
            }
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

            setText(null);
            setGraphic(row);
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

        private void archiveGoal(Map<String, Object> goal) {
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
                            refresh();
                        } catch (RuntimeException e) {
                            toasts.show("Could not archive goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                        }
                    }),
                    new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
        }

        private void trashGoal(Map<String, Object> goal) {
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
                            refresh();
                        } catch (RuntimeException e) {
                            toasts.show("Could not delete goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                        }
                    }),
                    new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
        }
    }

    /* ---------------------------------------------------------------- backup import */

    private void importBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import MOMENTUM backup");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON backup", "*.json"));
        Stage owner = (Stage) root.getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String json = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
                host.migrations().importBackupJson(json);
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> {
            toasts.show("Backup imported", ToastHost.Tone.SUCCESS);
            refresh();
            navigation.select(NavDestination.DASHBOARD);
        }));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Import failed: " + task.getException().getCause().getMessage(), ToastHost.Tone.ERROR)));
        new Thread(task, "backup-importer").start();
    }
}
