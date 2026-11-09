package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Goals list: every goal in the database, with status filtering, search and
 * category filtering. Click a goal to open it; buttons toggle completion,
 * archive or move to trash after user confirmation.
 * <p>
 * Phase 6.7: Bulk operations - multi-selection with bulk archive, restore,
 * delete, category change, and priority change.
 */
public class GoalsListController implements PageView {

    private final ServiceHost host;
    private final ThemeManager themes;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final ListView<Map<String, Object>> list = new ListView<>();
    private final ToggleGroup statusGroup = new ToggleGroup();
    private final TextField search = new TextField();
    private final ComboBox<CategoryEntry> category = new ComboBox<>();
    private final Button newGoal = new Button("New goal");

    // Bulk operations state
    private final ToggleButton selectMode = new ToggleButton("Select");
    private final Set<String> selectedIds = new java.util.HashSet<>();
    private final HBox bulkBar = new HBox(8);

    private final List<Map<String, Object>> categories = new ArrayList<>();

    private record CategoryEntry(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public GoalsListController(ServiceHost host, ThemeManager themes, ToastHost toasts,
                               ModalOverlay modals, NavigationManager navigation) {
        this.host = host;
        this.themes = themes;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(toolbar(), bulkBar, listSection());

        list.getStyleClass().add("goal-list");
        list.setPlaceholder(new Label("No goals match your filters."));
        list.setCellFactory(l -> new GoalListCell());

        newGoal.getStyleClass().addAll("btn", "btn-primary");
        newGoal.setOnAction(event -> navigation.select(NavDestination.GOAL_EDIT));

        search.setPromptText("Search goals\u2026");
        search.textProperty().addListener((obs, old, value) -> load());

        category.setPromptText("All categories");
        category.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> load());

        statusGroup.getToggles().forEach(toggle ->
                toggle.selectedProperty().addListener((obs, old, value) -> load()));

        // Select mode toggle
        selectMode.getStyleClass().addAll("btn", "btn-ghost");
        selectMode.setOnAction(event -> {
            if (!selectMode.isSelected()) {
                selectedIds.clear();
            }
            load();
        });

        // Bulk action buttons
        Button bulkArchive = new Button("Archive selected");
        bulkArchive.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");
        bulkArchive.setOnAction(event -> bulkArchive());

        Button bulkRestore = new Button("Restore selected");
        bulkRestore.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        bulkRestore.setOnAction(event -> bulkRestore());

        Button bulkDelete = new Button("Delete selected");
        bulkDelete.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");
        bulkDelete.setOnAction(event -> bulkDelete());

        Button bulkCategory = new Button("Change category");
        bulkCategory.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        bulkCategory.setOnAction(event -> bulkChangeCategory());

        bulkBar.getChildren().addAll(selectMode, bulkArchive, bulkRestore, bulkDelete, bulkCategory);
        bulkBar.setAlignment(Pos.CENTER_LEFT);
        bulkBar.getStyleClass().add("bulk-bar");
        bulkBar.setVisible(false);
        bulkBar.setManaged(false);
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

    private Node toolbar() {
        RadioButton all = new RadioButton("All goals");
        RadioButton active = new RadioButton("Active");
        RadioButton completed = new RadioButton("Completed");
        RadioButton archived = new RadioButton("Archived");
        all.setToggleGroup(statusGroup);
        active.setToggleGroup(statusGroup);
        completed.setToggleGroup(statusGroup);
        archived.setToggleGroup(statusGroup);
        all.setUserData(null);
        active.setUserData("active");
        completed.setUserData("completed");
        archived.setUserData("archived");
        all.setSelected(true);

        HBox statusBar = new HBox(10, all, active, completed, archived);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        search.setPrefWidth(260);
        category.setPrefWidth(200);

        HBox toolbar = new HBox(10, statusBar, search, category, newGoal);
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
        String query = search.getText();
        String queryLower = query == null ? "" : query.trim().toLowerCase();
        String categoryId = category.getValue() == null ? null : category.getValue().id();

        final AtomicReference<List<Map<String, Object>>> loadedGoals = new AtomicReference<>(new ArrayList<>());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        loadedGoals.set(repository.listGoals(null).stream()
                                .filter(goal -> statusMatches(goal, status))
                                .filter(goal -> matchesQuery(goal, queryLower))
                                .filter(goal -> categoryId == null
                                        || categoryId.equals(valueOr(goal, "categoryId", null)))
                                .collect(Collectors.toList()));
                        categories.clear();
                        categories.addAll(repository.listCategories());
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                return null;
            }
        };

        task.setOnSucceeded(event -> Platform.runLater(() -> {
            CategoryEntry previous = category.getValue();
            category.getItems().clear();
            category.getItems().add(new CategoryEntry(null, "All categories"));
            for (Map<String, Object> c : categories) {
                category.getItems().add(new CategoryEntry(String.valueOf(c.get("id")),
                        String.valueOf(c.get("name"))));
            }
            if (previous != null) {
                category.getItems().filtered(e -> e.id() != null && e.id().equals(previous.id()))
                        .stream().findFirst().ifPresent(e -> category.setValue(e));
            }
            list.getItems().setAll(loadedGoals.get());
            updateBulkBarVisibility();
        }));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load goals: " + task.getException().getMessage(),
                        ToastHost.Tone.ERROR)));
        new Thread(task, "goals-loader").start();
    }

    private void updateBulkBarVisibility() {
        boolean show = selectMode.isSelected() && !selectedIds.isEmpty();
        bulkBar.setVisible(show);
        bulkBar.setManaged(show);
    }

    private String selectedStatus() {
        Toggle selected = statusGroup.getSelectedToggle();
        if (selected == null) return null;
        Object data = selected.getUserData();
        return data == null ? null : String.valueOf(data);
    }

    private boolean statusMatches(Map<String, Object> goal, String status) {
        if (status == null) return true;
        return status.equals(valueOr(goal, "status", "active"));
    }

    private boolean matchesQuery(Map<String, Object> goal, String query) {
        if (query.isEmpty()) return true;
        return (valueOr(goal, "title", "") + " " + valueOr(goal, "description", "") + " "
                + valueOr(goal, "notes", "")).toLowerCase().contains(query);
    }

    /* ---------------------------------------------------------------- bulk operations */

    private void bulkArchive() {
        if (selectedIds.isEmpty()) return;
        modals.show("Archive " + selectedIds.size() + " goals?",
                new Label("Archive " + selectedIds.size() + " selected goals? They will move out of active goals but stay restorable."),
                new ModalOverlay.Action("Archive", ModalOverlay.Tone.DANGER, () -> {
                    try {
                        host.withRepository(repository -> {
                            try {
                                for (String id : new ArrayList<>(selectedIds)) {
                                    repository.archiveGoal(id);
                                    repository.recordActivity("goal-archived", id, null, "Bulk archived");
                                }
                                return null;
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        toasts.show(selectedIds.size() + " goals archived", ToastHost.Tone.SUCCESS);
                        selectedIds.clear();
                        load();
                    } catch (RuntimeException e) {
                        toasts.show("Bulk archive failed: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }

    private void bulkRestore() {
        if (selectedIds.isEmpty()) return;
        modals.show("Restore " + selectedIds.size() + " goals?",
                new Label("Restore " + selectedIds.size() + " selected archived goals?"),
                new ModalOverlay.Action("Restore", ModalOverlay.Tone.PRIMARY, () -> {
                    try {
                        host.withRepository(repository -> {
                            try {
                                for (String id : new ArrayList<>(selectedIds)) {
                                    repository.restoreGoalFromTrash(id);
                                    repository.recordActivity("goal-restored", id, null, "Bulk restored");
                                }
                                return null;
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        toasts.show(selectedIds.size() + " goals restored", ToastHost.Tone.SUCCESS);
                        selectedIds.clear();
                        load();
                    } catch (RuntimeException e) {
                        toasts.show("Bulk restore failed: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }

    private void bulkDelete() {
        if (selectedIds.isEmpty()) return;
        modals.show("Permanently delete " + selectedIds.size() + " goals?",
                new Label("Permanently delete " + selectedIds.size() + " selected goals? This cannot be undone."),
                new ModalOverlay.Action("Delete", ModalOverlay.Tone.DANGER, () -> {
                    try {
                        host.withRepository(repository -> {
                            try {
                                for (String id : new ArrayList<>(selectedIds)) {
                                    repository.purgeGoal(id);
                                    repository.recordActivity("goal-deleted", id, null, "Bulk deleted");
                                }
                                return null;
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        toasts.show(selectedIds.size() + " goals deleted", ToastHost.Tone.SUCCESS);
                        selectedIds.clear();
                        load();
                    } catch (RuntimeException e) {
                        toasts.show("Bulk delete failed: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }

    private void bulkChangeCategory() {
        if (selectedIds.isEmpty()) return;
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Change category");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.setPromptText("Select category");
        for (Map<String, Object> c : categories) {
            categoryBox.getItems().add(String.valueOf(c.get("name")));
        }
        categoryBox.getItems().add(0, "(No category)");

        VBox content = new VBox(10, new Label("Category"), categoryBox);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(categoryName -> {
            try {
                String catId = "(No category)".equals(categoryName) ? null
                        : categories.stream()
                                .filter(c -> categoryName.equals(String.valueOf(c.get("name"))))
                                .map(c -> String.valueOf(c.get("id")))
                                .findFirst().orElse(null);

                host.withRepository(repository -> {
                    try {
                        for (String id : new ArrayList<>(selectedIds)) {
                            repository.changeGoalCategory(id, catId);
                            repository.recordActivity("goal-category-changed", id, null,
                                    "Bulk category change to " + (catId == null ? "none" : categoryName));
                        }
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                toasts.show(selectedIds.size() + " goals updated", ToastHost.Tone.SUCCESS);
                selectedIds.clear();
                load();
            } catch (RuntimeException e) {
                toasts.show("Bulk category change failed: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
            }
        });
    }

    /* ---------------------------------------------------------------- cell */

    private final class GoalListCell extends ListCell<Map<String, Object>> {
        private final GoalCard card = new GoalCard(host, toasts, modals);
        private final CheckBox selectCheck = new CheckBox();

        private GoalListCell() {
            getStyleClass().add("goal-list-cell");
            selectCheck.setOnAction(event -> {
                Map<String, Object> item = getItem();
                if (item != null) {
                    String id = String.valueOf(item.get("id"));
                    if (selectCheck.isSelected()) {
                        selectedIds.add(id);
                    } else {
                        selectedIds.remove(id);
                    }
                    updateBulkBarVisibility();
                }
            });
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

            selectCheck.setVisible(selectMode.isSelected());
            selectCheck.setManaged(selectMode.isSelected());
            selectCheck.setSelected(selectedIds.contains(id));

            card.bind(goal,
                    () -> navigation.select(NavDestination.GOAL_DETAIL, id),
                    () -> navigation.select(NavDestination.GOAL_EDIT, id),
                    GoalsListController.this::load,
                    GoalsListController.this::load);

            HBox row = new HBox(10, selectCheck, card.node());
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            setText(null);
            setGraphic(row);
        }
    }

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
