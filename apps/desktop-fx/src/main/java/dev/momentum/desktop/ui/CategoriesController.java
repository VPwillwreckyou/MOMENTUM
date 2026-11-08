package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.sql.SQLException;
import java.time.Instant;
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
import javafx.scene.paint.Color;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Categories screen: list, create, edit and delete goal categories.
 * Every mutation writes to the real SQLite database via SqliteRepository.
 */
public class CategoriesController implements PageView {

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final ListView<Map<String, Object>> list = new ListView<>();

    public CategoriesController(ServiceHost host, ToastHost toasts, ModalOverlay modals,
                                NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), listSection());

        list.getStyleClass().add("category-list");
        list.setPlaceholder(new Label("No categories yet - create your first category."));
        list.setCellFactory(l -> new CategoryCell());

        Button newCat = new Button("New category");
        newCat.getStyleClass().addAll("btn", "btn-primary");
        newCat.setOnAction(event -> editCategory(null));
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
        Label title = new Label("Categories");
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

    /* ---------------------------------------------------------------- data */

    private void load() {
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
        task.setOnSucceeded(event -> Platform.runLater(() -> list.getItems().setAll(categories.get())));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load categories: " + task.getException().getMessage(),
                        ToastHost.Tone.ERROR)));
        new Thread(task, "categories-loader").start();
    }

    /* ---------------------------------------------------------------- cell */

    private final class CategoryCell extends ListCell<Map<String, Object>> {
        private final HBox row = new HBox(10);
        private final Label colorDot = new Label("\u25CF");
        private final Label name = new Label();
        private final Button edit = new Button("Edit");
        private final Button trash = new Button("Delete");

        private CategoryCell() {
            row.getStyleClass().add("category-row");
            row.setAlignment(Pos.CENTER_LEFT);
            colorDot.getStyleClass().add("color-dot");
            name.getStyleClass().add("category-name");
            edit.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            trash.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");
            HBox actions = new HBox(8, edit, trash);
            actions.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().addAll(colorDot, name, actions);
            HBox.setHgrow(name, Priority.ALWAYS);

            edit.setOnAction(event -> editCategory(getItem()));
            trash.setOnAction(event -> deleteCategory(getItem()));
        }

        @Override
        protected void updateItem(Map<String, Object> c, boolean empty) {
            super.updateItem(c, empty);
            if (empty || c == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            colorDot.setTextFill(Color.web(valueOr(c, "color", "#22d3ee")));
            name.setText(valueOr(c, "name", "Untitled"));
            setText(null);
            setGraphic(row);
        }

        private void deleteCategory(Map<String, Object> c) {
            if (c == null) return;
            String id = String.valueOf(c.get("id"));
            modals.show("Delete category?",
                    new Label("Delete category \u201c" + valueOr(c, "name", "this category") + "\u201d? "
                            + "Goals in this category will keep their data (category link is cleared)."),
                    new ModalOverlay.Action("Delete", ModalOverlay.Tone.DANGER, () -> {
                        try {
                            host.withRepository(repository -> {
                                try {
                                    if (repository.goalsInCategory(id) > 0) {
                                        // Clear category reference on goals before deleting the category.
                                        // (SqliteRepository currently has no bulk method; we clear via upsert per goal.)
                                        // Simpler: just delete category - FK ON DELETE SET NULL handles goals.
                                        repository.softDeleteCategory(id);
                                        repository.recordActivity(null, null, null, "Deleted category");
                                        return null;
                                    } else {
                                        repository.softDeleteCategory(id);
                                        repository.recordActivity(null, null, null, "Deleted category");
                                        return null;
                                    }
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            toasts.show("Category deleted", ToastHost.Tone.SUCCESS);
                            load();
                        } catch (RuntimeException e) {
                            toasts.show("Could not delete category: " + e.getCause().getMessage(),
                                    ToastHost.Tone.ERROR);
                        }
                    }),
                    new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
        }
    }

    /* ---------------------------------------------------------------- create / edit */

    private void editCategory(Map<String, Object> existing) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New category" : "Edit category");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        TextField name = new TextField();
        name.setPromptText("Category name");
        if (existing != null) name.setText(valueOr(existing, "name", ""));
        TextField color = new TextField();
        color.setPromptText("Accent color (hex)");
        color.setText(valueOr(existing, "color", "#22d3ee"));
        TextField icon = new TextField();
        icon.setPromptText("Icon (target, work, health…)");
        icon.setText(valueOr(existing, "icon", "target"));

        content.getChildren().addAll(field("Name", name), field("Color", color), field("Icon", icon));
        dialog.getDialogPane().setContent(content);

        Platform.runLater(name::requestFocus);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                String nameText = name.getText();
                if (nameText == null || nameText.isBlank()) return null;
                Map<String, Object> cat = new LinkedHashMap<>();
                cat.put("name", nameText);
                cat.put("color", color.getText());
                cat.put("icon", icon.getText());
                cat.put("updatedAt", Instant.now().toString());
                if (existing == null) {
                    cat.put("id", UUID.randomUUID().toString());
                    cat.put("createdAt", Instant.now().toString());
                } else {
                    cat.put("id", existing.get("id"));
                    cat.put("createdAt", existing.get("createdAt"));
                }
                return cat;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                host.withRepository(repository -> {
                    try {
                        repository.upsertCategory(result);
                        repository.recordActivity(null, null, null,
                                existing == null ? "Created category" : "Edited category");
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                toasts.show("Category saved", ToastHost.Tone.SUCCESS);
                load();
            } catch (RuntimeException e) {
                toasts.show("Could not save category: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
            }
        });
    }

    private static Node field(String labelText, Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(6, label, control);
        box.getStyleClass().add("form-field-box");
        return box;
    }

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
