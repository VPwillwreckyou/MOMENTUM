package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.sql.SQLException;
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
 * Global Search: application-wide search across goals, tasks, categories,
 * milestones, and notes. Displays grouped results by type and opens the
 * relevant destination when a result is clicked.
 */
public class GlobalSearchController implements PageView {

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final TextField search = new TextField();
    private final ListView<Map<String, Object>> list = new ListView<>();

    public GlobalSearchController(ServiceHost host, ToastHost toasts, ModalOverlay modals,
                                  NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), searchBox(), listSection());

        list.getStyleClass().add("search-list");
        list.setCellFactory(l -> new SearchResultCell());
        list.setPlaceholder(new Label("Type to search across goals, tasks, categories, and milestones."));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        Platform.runLater(search::requestFocus);
        if (parameter != null) {
            search.setText(String.valueOf(parameter));
            performSearch();
        }
    }

    /* ---------------------------------------------------------------- layout */

    private Node header() {
        Label title = new Label("Global Search");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(10, title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node searchBox() {
        search.setPromptText("Search everything\u2026");
        search.textProperty().addListener((o, old, nw) -> performSearch());
        search.setPrefWidth(Double.MAX_VALUE);
        HBox.setHgrow(search, Priority.ALWAYS);

        VBox box = new VBox(8, search);
        box.getStyleClass().add("card");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Node listSection() {
        VBox section = new VBox(10, list);
        section.getStyleClass().add("card");
        VBox.setVgrow(list, Priority.ALWAYS);
        return section;
    }

    /* ---------------------------------------------------------------- search */

    private void performSearch() {
        String query = search.getText() == null ? "" : search.getText().trim();
        if (query.isEmpty()) {
            list.getItems().clear();
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        List<Map<String, Object>> results = new ArrayList<>();

                        // Search goals
                        for (Map<String, Object> g : repository.listGoals(null)) {
                            String title = valueOr(g, "title", "");
                            String notes = valueOr(g, "notes", "");
                            if (title.toLowerCase().contains(query.toLowerCase())
                                    || notes.toLowerCase().contains(query.toLowerCase())) {
                                Map<String, Object> r = new LinkedHashMap<>(g);
                                r.put("resultType", "goal");
                                r.put("resultTitle", title);
                                results.add(r);
                            }
                        }

                        // Search tasks across all goals
                        for (Map<String, Object> g : repository.listGoals(null)) {
                            String goalId = String.valueOf(g.get("id"));
                            for (Map<String, Object> t : repository.listTasks(goalId, null)) {
                                String title = valueOr(t, "title", "");
                                String notes = valueOr(t, "notes", "");
                                if (title.toLowerCase().contains(query.toLowerCase())
                                        || notes.toLowerCase().contains(query.toLowerCase())) {
                                    Map<String, Object> r = new LinkedHashMap<>(t);
                                    r.put("resultType", "task");
                                    r.put("resultTitle", title);
                                    r.put("goalTitle", valueOr(g, "title", "Untitled"));
                                    results.add(r);
                                }
                            }
                        }

                        // Search categories
                        for (Map<String, Object> c : repository.listCategories()) {
                            String name = valueOr(c, "name", "");
                            if (name.toLowerCase().contains(query.toLowerCase())) {
                                Map<String, Object> r = new LinkedHashMap<>(c);
                                r.put("resultType", "category");
                                r.put("resultTitle", name);
                                results.add(r);
                            }
                        }

                        final AtomicReference<List<Map<String, Object>>> resultRef = new AtomicReference<>(results);
                        Platform.runLater(() -> list.getItems().setAll(resultRef.get()));
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                return null;
            }
        };
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Search failed: " + task.getException().getMessage(), ToastHost.Tone.ERROR)));
        new Thread(task, "global-search").start();
    }

    /* ---------------------------------------------------------------- cell */

    private final class SearchResultCell extends ListCell<Map<String, Object>> {
        private final Label iconLabel = new Label();
        private final Label titleLabel = new Label();
        private final Label subLabel = new Label();
        private final HBox row = new HBox(12, iconLabel, titleLabel, subLabel);

        private SearchResultCell() {
            iconLabel.getStyleClass().add("search-icon");
            titleLabel.getStyleClass().add("search-title");
            subLabel.getStyleClass().add("search-sub");

            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);
            row.getStyleClass().add("search-row");
            setGraphic(row);
        }

        @Override
        protected void updateItem(Map<String, Object> r, boolean empty) {
            super.updateItem(r, empty);
            if (empty || r == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String type = valueOr(r, "resultType", "goal");
            iconLabel.setText(typeIcon(type));
            titleLabel.setText(valueOr(r, "resultTitle", "Untitled"));

            StringBuilder sub = new StringBuilder();
            switch (type) {
                case "goal" -> sub.append(valueOr(r, "status", "active")).append(" \u2022 ")
                        .append(valueOr(r, "categoryId", "\u2014").equals("\u2014") ? "No category" : valueOr(r, "categoryId", ""));
                case "task" -> sub.append("Task in \u201c").append(valueOr(r, "goalTitle", "")).append("\u201d");
                case "category" -> sub.append("Category");
            }
            subLabel.setText(sub.toString());

            String id = String.valueOf(r.get("id"));
            setOnMouseClicked(event -> openResult(type, id));

            setText(null);
            setGraphic(row);
        }

        private String typeIcon(String type) {
            return switch (type) {
                case "goal" -> "\u25C9";
                case "task" -> "\u2611";
                case "category" -> "\u25A2";
                default -> "\u25CF";
            };
        }

        private void openResult(String type, String id) {
            switch (type) {
                case "goal" -> navigation.select(NavDestination.GOAL_DETAIL, id);
                case "task" -> navigation.select(NavDestination.GOALS);
                case "category" -> navigation.select(NavDestination.CATEGORIES);
            }
        }
    }

    /* ---------------------------------------------------------------- helpers */

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
