package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Finance screen: financial goals with target amounts, current progress,
 * currency support, transaction records, and progress visualization.
 * Finance data is stored in the goals.finance_json column as JSON.
 * All monetary amounts use BigDecimal (no floating-point).
 */
public class FinanceController implements PageView {

    private static final String DEFAULT_CURRENCY = "USD";

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);

    public FinanceController(ServiceHost host, ToastHost toasts, ModalOverlay modals,
                             NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), statsBar(), goalsList());
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        reloadGoals();
    }

    /* ---------------------------------------------------------------- layout */

    private Node header() {
        Label title = new Label("Finance");
        title.getStyleClass().add("page-title");
        Button newGoal = new Button("New financial goal");
        newGoal.getStyleClass().addAll("btn", "btn-primary");
        newGoal.setOnAction(event -> editFinancialGoal(null));
        HBox header = new HBox(10, title, newGoal);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node statsBar() {
        VBox stats = new VBox(8);
        stats.getStyleClass().add("card");
        stats.setMaxWidth(Double.MAX_VALUE);

        Label totalValue = new Label("$0.00");
        totalValue.getStyleClass().add("finance-stat-value");
        Label savedValue = new Label("$0.00");
        savedValue.getStyleClass().add("finance-stat-value");
        Label remainingValue = new Label("$0.00");
        remainingValue.getStyleClass().add("finance-stat-value");

        HBox row = new HBox(20,
                statBox("Total target", totalValue),
                statBox("Total saved", savedValue),
                statBox("Remaining", remainingValue));
        row.setAlignment(Pos.CENTER_LEFT);
        stats.getChildren().addAll(row);
        return stats;
    }

    private Node statBox(String label, Label value) {
        VBox box = new VBox(6, new Label(label), value);
        box.getStyleClass().add("finance-stat-box");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Node goalsList() {
        ListView<Map<String, Object>> list = new ListView<>();
        list.getStyleClass().add("goal-list");
        list.setCellFactory(l -> new FinanceGoalCell());
        list.setPlaceholder(new Label("No financial goals yet."));
        VBox section = new VBox(10, list);
        section.getStyleClass().add("card");
        VBox.setVgrow(list, Priority.ALWAYS);
        return section;
    }

    /* ---------------------------------------------------------------- data */

    private void reloadGoals() {
        try {
            host.withRepository(repository -> {
                try {
                    List<Map<String, Object>> all = repository.listGoals(null);
                    List<Map<String, Object>> financial = new ArrayList<>();
                    for (Map<String, Object> g : all) {
                        Object finance = g.get("finance");
                        if (finance != null && !String.valueOf(finance).isBlank()) {
                            financial.add(g);
                        }
                    }
                    financial.sort((a, b) -> {
                        String ta = valueOr(a, "updatedAt", "");
                        String tb = valueOr(b, "updatedAt", "");
                        return tb.compareTo(ta);
                    });
                    // Find the ListView in the UI and update it
                    Node content = root.getChildren().get(2); // goalsList section
                    if (content instanceof VBox section && section.getChildren().get(0) instanceof ListView<?> lv) {
                        @SuppressWarnings("unchecked")
                        ListView<Map<String, Object>> list = (ListView<Map<String, Object>>) lv;
                        list.getItems().setAll(financial);
                    }
                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            toasts.show("Could not load finance: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
        }
    }

    /* ---------------------------------------------------------------- create / edit financial goal */

    private void editFinancialGoal(Map<String, Object> existing) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New financial goal" : "Edit financial goal");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(12);

        TextField titleField = new TextField();
        titleField.setPromptText("Goal title (e.g. \"Emergency fund\")");
        if (existing != null) titleField.setText(valueOr(existing, "title", ""));

        TextField targetField = new TextField();
        targetField.setPromptText("Target amount");
        if (existing != null) {
            Object fin = existing.get("finance");
            if (fin instanceof Map) {
                targetField.setText(String.valueOf(((Map<String, Object>) fin).getOrDefault("target", "")));
            }
        }

        ComboBox<String> currencyField = new ComboBox<>();
        currencyField.getItems().addAll("USD", "EUR", "GBP", "JPY", "CAD", "AUD");
        currencyField.setValue(DEFAULT_CURRENCY);
        if (existing != null) {
            Object fin = existing.get("finance");
            if (fin instanceof Map) {
                currencyField.setValue(String.valueOf(((Map<String, Object>) fin).getOrDefault("currency", DEFAULT_CURRENCY)));
            }
        }

        TextArea notesField = new TextArea();
        notesField.setPromptText("Notes");
        notesField.setPrefRowCount(3);
        if (existing != null) notesField.setText(valueOr(existing, "notes", ""));

        content.getChildren().addAll(
                field("Title *", titleField),
                field("Target amount", targetField),
                field("Currency", currencyField),
                field("Notes", notesField));
        dialog.getDialogPane().setContent(content);

        Platform.runLater(titleField::requestFocus);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                String titleText = titleField.getText();
                if (titleText == null || titleText.isBlank()) return null;
                String targetText = targetField.getText();
                BigDecimal target;
                try {
                    target = new BigDecimal(targetText).setScale(2, RoundingMode.HALF_UP);
                } catch (NumberFormatException e) {
                    toasts.show("Invalid target amount", ToastHost.Tone.ERROR);
                    return null;
                }

                Map<String, Object> finance = new LinkedHashMap<>();
                finance.put("target", target.toString());
                finance.put("currency", currencyField.getValue());
                finance.put("current", existing != null ? extractCurrent(existing) : "0.00");
                finance.put("transactions", existing != null ? extractTransactions(existing) : new ArrayList<>());

                Map<String, Object> goal = new LinkedHashMap<>();
                goal.put("id", existing == null ? UUID.randomUUID().toString() : existing.get("id"));
                goal.put("title", titleText);
                goal.put("notes", notesField.getText());
                goal.put("finance", finance);
                goal.put("createdAt", existing != null ? existing.get("createdAt") : Instant.now().toString());
                goal.put("updatedAt", Instant.now().toString());
                goal.put("status", "active");
                goal.put("categoryId", "");
                return goal;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                host.withRepository(repository -> {
                    try {
                        repository.upsertGoal(result);
                        repository.recordActivity(
                                existing == null ? "finance-created" : "finance-edited",
                                String.valueOf(result.get("id")), null,
                                existing == null ? "Created financial goal" : "Edited financial goal");
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                toasts.show("Financial goal saved", ToastHost.Tone.SUCCESS);
                reloadGoals();
            } catch (RuntimeException e) {
                toasts.show("Could not save financial goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
            }
        });
    }

    /* ---------------------------------------------------------------- cell */

    private final class FinanceGoalCell extends ListCell<Map<String, Object>> {
        private final Label title = new Label();
        private final Label progressLabel = new Label();
        private final ProgressBar progressBar = new ProgressBar();
        private final Label details = new Label();
        private final Button edit = new Button("Edit");
        private final Button delete = new Button("Delete");
        private final Button addTx = new Button("Add transaction");
        private final VBox row = new VBox(8);

        private FinanceGoalCell() {
            title.getStyleClass().add("task-title");
            progressLabel.getStyleClass().add("task-meta");
            details.getStyleClass().add("task-meta");
            progressBar.setPrefWidth(200);
            edit.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");
            addTx.getStyleClass().addAll("btn", "btn-small", "btn-ghost");

            HBox top = new HBox(10, title, edit, addTx, delete);
            top.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(title, Priority.ALWAYS);

            HBox bottom = new HBox(10, progressBar, progressLabel, details);
            bottom.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(progressBar, Priority.ALWAYS);

            row.getChildren().addAll(top, bottom);
            row.getStyleClass().add("finance-row");
            row.setMaxWidth(Double.MAX_VALUE);

            edit.setOnAction(event -> editFinancialGoal(getItem()));
            addTx.setOnAction(event -> {
                Map<String, Object> item = getItem();
                if (item != null) addTransaction(String.valueOf(item.get("id")));
            });
            delete.setOnAction(event -> {
                Map<String, Object> item = getItem();
                if (item != null) deleteFinancialGoal(String.valueOf(item.get("id")));
            });
            setGraphic(row);
        }

        @Override
        protected void updateItem(Map<String, Object> g, boolean empty) {
            super.updateItem(g, empty);
            if (empty || g == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String id = String.valueOf(g.get("id"));
            title.setText(valueOr(g, "title", "Untitled"));

            Object fin = g.get("finance");
            String currency = DEFAULT_CURRENCY;
            BigDecimal target = BigDecimal.ZERO;
            BigDecimal current = BigDecimal.ZERO;
            if (fin instanceof Map) {
                currency = String.valueOf(((Map<String, Object>) fin).getOrDefault("currency", DEFAULT_CURRENCY));
                try { target = new BigDecimal(String.valueOf(((Map<String, Object>) fin).getOrDefault("target", "0"))); } catch (Exception ignored) {}
                try { current = new BigDecimal(String.valueOf(((Map<String, Object>) fin).getOrDefault("current", "0"))); } catch (Exception ignored) {}
            }

            BigDecimal remaining = target.subtract(current);
            double pct = target.compareTo(BigDecimal.ZERO) > 0
                    ? current.divide(target, 4, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            progressBar.setProgress(Math.max(0, Math.min(1, pct)));

            details.setText(String.format("%s %.2f / %s %.2f  •  %s %.2f remaining",
                    currency, current, currency, target, currency, remaining.max(BigDecimal.ZERO)));
            progressLabel.setText(String.format("%.0f%%", pct * 100));

            edit.setUserData(id);
            delete.setUserData(id);
            addTx.setUserData(id);
            setText(null);
            setGraphic(row);
        }

        private void deleteFinancialGoal(String id) {
            ListView<Map<String, Object>> parentList = getListView();
            if (parentList == null) return;
            Map<String, Object> g = null;
            for (Map<String, Object> item : parentList.getItems()) {
                if (id.equals(String.valueOf(item.get("id")))) { g = item; break; }
            }
            if (g == null) return;
            final Map<String, Object> goal = g;
            modals.show("Delete financial goal?",
                    new Label("Delete financial goal \u201c" + valueOr(goal, "title", "this goal") + "\u201d?"),
                    new ModalOverlay.Action("Delete", ModalOverlay.Tone.DANGER, () -> {
                        try {
                            host.withRepository(repository -> {
                                try {
                                    Map<String, Object> patch = new LinkedHashMap<>(goal);
                                    patch.put("finance", null);
                                    repository.upsertGoal(patch);
                                    repository.recordActivity("finance-deleted", id, null,
                                            "Deleted financial goal data");
                                    return null;
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            toasts.show("Financial goal removed", ToastHost.Tone.SUCCESS);
                            reloadGoals();
                        } catch (RuntimeException e) {
                            toasts.show("Could not delete: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                        }
                    }),
                    new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
        }

        private void addTransaction(String goalId) {
            ListView<Map<String, Object>> parentList = getListView();
            if (parentList == null) return;
            Map<String, Object> g = null;
            for (Map<String, Object> item : parentList.getItems()) {
                if (goalId.equals(String.valueOf(item.get("id")))) { g = item; break; }
            }
            if (g == null) return;
            final Map<String, Object> goal = g;

            Dialog<Map<String, Object>> dialog = new Dialog<>();
            dialog.setTitle("Add transaction");
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            VBox content = new VBox(12);
            TextField amountField = new TextField();
            amountField.setPromptText("Amount (e.g. 50.00)");
            ComboBox<String> typeField = new ComboBox<>();
            typeField.getItems().addAll("deposit", "withdrawal");
            typeField.setValue("deposit");
            TextField noteField = new TextField();
            noteField.setPromptText("Note");

            content.getChildren().addAll(
                    field("Amount *", amountField),
                    field("Type", typeField),
                    field("Note", noteField));
            dialog.getDialogPane().setContent(content);

            Platform.runLater(amountField::requestFocus);

            dialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    String amtText = amountField.getText();
                    BigDecimal amount;
                    try {
                        amount = new BigDecimal(amtText).setScale(2, RoundingMode.HALF_UP);
                    } catch (NumberFormatException e) {
                        toasts.show("Invalid amount", ToastHost.Tone.ERROR);
                        return null;
                    }
                    Map<String, Object> tx = new LinkedHashMap<>();
                    tx.put("id", UUID.randomUUID().toString());
                    tx.put("amount", amount.toString());
                    tx.put("type", typeField.getValue());
                    tx.put("note", noteField.getText());
                    tx.put("date", Instant.now().toString());
                    return tx;
                }
                return null;
            });

            dialog.showAndWait().ifPresent(tx -> {
                try {
                    host.withRepository(repository -> {
                        try {
                            Object fin = goal.get("finance");
                            Map<String, Object> finance = fin instanceof Map ? new LinkedHashMap<>((Map<String, Object>) fin) : new LinkedHashMap<>();
                            List<Map<String, Object>> txs = new ArrayList<>();
                            Object existingTx = finance.get("transactions");
                            if (existingTx instanceof List) {
                                txs.addAll((List<Map<String, Object>>) existingTx);
                            }
                            txs.add(tx);
                            finance.put("transactions", txs);

                            BigDecimal current = BigDecimal.ZERO;
                            String currency = String.valueOf(finance.getOrDefault("currency", DEFAULT_CURRENCY));
                            for (Map<String, Object> t : txs) {
                                BigDecimal amt = new BigDecimal(String.valueOf(t.get("amount")));
                                if ("deposit".equals(t.get("type"))) {
                                    current = current.add(amt);
                                } else {
                                    current = current.subtract(amt);
                                }
                            }
                            finance.put("current", current.setScale(2, RoundingMode.HALF_UP).toString());

                            Map<String, Object> patch = new LinkedHashMap<>(goal);
                            patch.put("finance", finance);
                            repository.upsertGoal(patch);
                            repository.recordActivity("finance-transaction", goalId, null,
                                    "Added transaction to financial goal");
                            return null;
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    toasts.show("Transaction added", ToastHost.Tone.SUCCESS);
                    reloadGoals();
                } catch (RuntimeException e) {
                    toasts.show("Could not add transaction: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
                }
            });
        }
    }

    /* ---------------------------------------------------------------- helpers */

    private static String extractCurrent(Map<String, Object> goal) {
        Object fin = goal.get("finance");
        if (fin instanceof Map) {
            return String.valueOf(((Map<String, Object>) fin).getOrDefault("current", "0.00"));
        }
        return "0.00";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractTransactions(Map<String, Object> goal) {
        Object fin = goal.get("finance");
        if (fin instanceof Map) {
            Object txs = ((Map<String, Object>) fin).get("transactions");
            if (txs instanceof List) {
                return (List<Map<String, Object>>) txs;
            }
        }
        return new ArrayList<>();
    }

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private Node field(String labelText, Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(6, label, control);
        box.getStyleClass().add("form-field-box");
        return box;
    }
}
