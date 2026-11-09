package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import dev.momentum.core.Json;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * AI Planner: describes a goal in plain language, asks the configured AI
 * provider (via MomentumService.aiChat / Groq) to generate a milestone plan,
 * then creates the goal with milestones and tasks in SQLite.
 * <p>
 * Phase 6.14 enhancements: conversation history, persistence, deletion,
 * loading states, error handling, and AI-assisted task/milestone suggestions.
 */
public class AiPlannerController implements PageView {

    private static final String CONVERSATIONS_KEY = "ai_conversations";

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;

    private final VBox root = new VBox(16);
    private final TextArea description = new TextArea();
    private final Button generate = new Button("Generate plan");
    private final Button createGoal = new Button("Create goal");
    private final Label status = new Label();
    private final ListView<Map<String, Object>> historyList = new ListView<>();
    private final Button deleteHistory = new Button("Delete selected");

    private Map<String, Object> draftPlan = null;

    public AiPlannerController(ServiceHost host, ToastHost toasts, ModalOverlay modals) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), inputCard(), resultCard(), historyCard());

        description.setPromptText("Describe your goal in plain language\u2026");
        description.setPrefRowCount(6);
        description.setWrapText(true);

        generate.getStyleClass().addAll("btn", "btn-primary");
        generate.setOnAction(event -> generatePlan());
        createGoal.getStyleClass().addAll("btn", "btn-ghost");
        createGoal.setOnAction(event -> createDraftGoal());
        createGoal.setDisable(true);
        status.getStyleClass().add("field-error");
        status.setVisible(false);

        deleteHistory.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "btn-danger");
        deleteHistory.setOnAction(event -> deleteConversation());
        deleteHistory.setDisable(true);

        historyList.getStyleClass().add("ai-history-list");
        historyList.setCellFactory(l -> new HistoryCell());
        historyList.setPlaceholder(new Label("No AI conversations yet."));
        historyList.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            deleteHistory.setDisable(sel == null);
        });

        loadHistory();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        loadHistory();
    }

    /* ---------------------------------------------------------------- layout */

    private Node header() {
        Label title = new Label("AI Planner");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(10, title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node inputCard() {
        Label title = new Label("Describe your goal");
        title.getStyleClass().add("card-title");
        Label sub = new Label("The AI will break it into milestones and tasks for you.");
        sub.getStyleClass().add("hero-sub");

        HBox actions = new HBox(10, generate, createGoal);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, title, sub, description, actions, status);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node resultCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setVisible(false);
        card.setManaged(false);
        root.getChildren().add(2, card);
        return card;
    }

    private Node historyCard() {
        Label title = new Label("Conversation history");
        title.getStyleClass().add("card-title");
        Label sub = new Label("Previous AI conversations are stored locally. Select one to delete.");
        sub.getStyleClass().add("hero-sub");

        HBox actions = new HBox(10, deleteHistory);
        actions.setAlignment(Pos.CENTER_LEFT);

        historyList.setPrefHeight(150);

        VBox card = new VBox(10, title, sub, historyList, actions);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /* ---------------------------------------------------------------- generate */

    private void generatePlan() {
        String goalText = description.getText();
        if (goalText == null || goalText.isBlank()) {
            status.setText("Please describe your goal first.");
            status.setVisible(true);
            return;
        }

        generate.setDisable(true);
        status.setVisible(false);

        host.settingsGet("ai_config").ifPresentOrElse(json -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cfg = Json.parseObject(json);
                String provider = String.valueOf(cfg.getOrDefault("provider", "groq"));
                String model = String.valueOf(cfg.getOrDefault("model", ""));
                String baseUrl = String.valueOf(cfg.getOrDefault("baseUrl", ""));
                String key = String.valueOf(cfg.getOrDefault("apiKey", ""));
                runAiPlan(goalText, provider, model, baseUrl, key);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    generate.setDisable(false);
                    status.setText("Invalid AI config: " + e.getMessage());
                    status.setVisible(true);
                });
            }
        }, () -> {
            Platform.runLater(() -> {
                generate.setDisable(false);
                status.setText("No AI provider configured. Set one in Settings first.");
                status.setVisible(true);
            });
        });
    }

    private void runAiPlan(String goalText, String provider, String model, String baseUrl, String key) {
        String prompt = """
                You are a planning assistant. Given the user's goal below, produce a JSON plan ONLY (no markdown fences, no extra text) with:
                  title: a concise goal title
                  description: a one-paragraph goal description
                  milestones: an array of { title, description, tasks: [ { title, notes?, priority? } ] }
                User goal: %s
                """.formatted(goalText);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                dev.momentum.ai.AiProxy.ChatResponse resp = host.service().aiChat(
                        new dev.momentum.ai.AiProxy.ChatRequest(
                                provider, key, model, baseUrl,
                                List.of(Map.of("role", "user", "content", prompt)),
                                true),
                        false);
                draftPlan = parsePlan(resp.text());
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> {
            generate.setDisable(false);
            if (draftPlan == null) {
                status.setText("The AI did not return a usable plan.");
                status.setVisible(true);
                return;
            }
            showDraft(draftPlan);
            createGoal.setDisable(false);

            // Persist conversation
            saveConversation(goalText, draftPlan);
        }));
        task.setOnFailed(event -> Platform.runLater(() -> {
            generate.setDisable(false);
            status.setText("AI plan failed: " + task.getException().getMessage());
            status.setVisible(true);
        }));
        new Thread(task, "ai-planner").start();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePlan(String text) {
        try {
            Object parsed = Json.parse(text);
            if (!(parsed instanceof Map<?, ?>)) return null;
            Map<String, Object> plan = (Map<String, Object>) parsed;
            if (plan.get("title") == null || plan.get("milestones") == null) return null;
            return plan;
        } catch (Exception e) {
            return null;
        }
    }

    private void showDraft(Map<String, Object> plan) {
        VBox card = (VBox) root.getChildren().get(2);
        card.getChildren().clear();
        card.setVisible(true);
        card.setManaged(true);

        Label title = new Label("Generated plan");
        title.getStyleClass().add("card-title");

        Label planTitle = new Label(String.valueOf(plan.getOrDefault("title", "Untitled")));
        planTitle.getStyleClass().add("hero-title");
        Label planDesc = new Label(String.valueOf(plan.getOrDefault("description", "")));
        planDesc.getStyleClass().add("hero-sub");
        planDesc.setWrapText(true);

        VBox milestonesBox = new VBox(8);
        milestonesBox.getStyleClass().add("goal-list");
        List<Map<String, Object>> milestones = plan.get("milestones") instanceof List<?> list
                ? list.stream().filter(m -> m instanceof Map).map(m -> (Map<String, Object>) m).toList()
                : List.of();
        for (Map<String, Object> m : milestones) {
            Label mTitle = new Label(String.valueOf(m.getOrDefault("title", "Milestone")));
            mTitle.getStyleClass().add("task-title");
            Label mDesc = new Label(String.valueOf(m.getOrDefault("description", "")));
            mDesc.getStyleClass().add("goal-meta");
            mDesc.setWrapText(true);
            VBox tasksBox = new VBox(4);
            tasksBox.getStyleClass().add("task-list-view");
            List<Map<String, Object>> tasks = m.get("tasks") instanceof List<?> tl
                    ? tl.stream().filter(t -> t instanceof Map).map(t -> (Map<String, Object>) t).toList()
                    : List.of();
            for (Map<String, Object> t : tasks) {
                Label tLabel = new Label(String.valueOf(t.getOrDefault("title", "Task")));
                tLabel.getStyleClass().add("task-title");
                tasksBox.getChildren().add(tLabel);
            }
            VBox mBlock = new VBox(6, mTitle, mDesc, tasksBox);
            mBlock.getStyleClass().add("card");
            milestonesBox.getChildren().add(mBlock);
        }

        card.getChildren().addAll(title, planTitle, planDesc, milestonesBox);
    }

    /* ---------------------------------------------------------------- create goal */

    private void createDraftGoal() {
        if (draftPlan == null) return;
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", UUID.randomUUID().toString());
        g.put("title", String.valueOf(draftPlan.getOrDefault("title", "AI-planned goal")));
        g.put("description", String.valueOf(draftPlan.getOrDefault("description", "")));
        g.put("status", "active");
        g.put("kind", "one-time");
        g.put("priority", "medium");
        g.put("createdAt", Instant.now().toString());
        g.put("updatedAt", Instant.now().toString());
        g.put("aiGenerated", true);

        try {
            host.withRepository(repository -> {
                try {
                    repository.upsertGoal(g);
                    List<Map<String, Object>> milestones = draftPlan.get("milestones") instanceof List<?> ml
                            ? ml.stream().filter(m -> m instanceof Map).map(m -> (Map<String, Object>) m).toList()
                            : List.of();
                    int order = 0;
                    for (Map<String, Object> m : milestones) {
                        Map<String, Object> ms = new LinkedHashMap<>(m);
                        ms.put("title", String.valueOf(m.getOrDefault("title", "Milestone")));
                        ms.put("description", String.valueOf(m.getOrDefault("description", "")));
                        ms.put("order", order++);
                        repository.upsertMilestone(String.valueOf(g.get("id")), ms);
                    }
                    repository.recordActivity("goal-created", String.valueOf(g.get("id")), null,
                            "Created goal via AI Planner");
                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            toasts.show("Goal created from AI plan.", ToastHost.Tone.SUCCESS);
            createGoal.setDisable(true);
        } catch (RuntimeException e) {
            toasts.show("Could not create goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
        }
    }

    /* ---------------------------------------------------------------- conversation history */

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadConversations() {
        try {
            String json = host.settingsGet(CONVERSATIONS_KEY).orElse("[]");
            Object parsed = Json.parse(json);
            if (parsed instanceof List<?> list) {
                return list.stream()
                        .filter(m -> m instanceof Map)
                        .map(m -> (Map<String, Object>) m)
                        .collect(Collectors.toList());
            }
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    private void saveConversations(List<Map<String, Object>> conversations) {
        try {
            host.settingsPut(CONVERSATIONS_KEY, Json.stringify(conversations));
        } catch (Exception ignored) {}
    }

    private void loadHistory() {
        List<Map<String, Object>> convs = loadConversations();
        convs.sort((a, b) -> {
            String ta = String.valueOf(a.getOrDefault("updatedAt", ""));
            String tb = String.valueOf(b.getOrDefault("updatedAt", ""));
            return tb.compareTo(ta);
        });
        historyList.getItems().setAll(convs);
    }

    private void saveConversation(String userMessage, Map<String, Object> plan) {
        List<Map<String, Object>> convs = loadConversations();
        Map<String, Object> conv = new LinkedHashMap<>();
        conv.put("id", UUID.randomUUID().toString());
        conv.put("userMessage", userMessage);
        conv.put("plan", plan);
        conv.put("createdAt", Instant.now().toString());
        conv.put("updatedAt", Instant.now().toString());
        convs.add(0, conv);
        // Keep only last 50 conversations
        if (convs.size() > 50) convs = convs.subList(0, 50);
        saveConversations(convs);
        loadHistory();
    }

    private void deleteConversation() {
        Map<String, Object> selected = historyList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        String convId = String.valueOf(selected.get("id"));

        modals.show("Delete conversation?",
                new Label("Delete this AI conversation? This cannot be undone."),
                new ModalOverlay.Action("Delete", ModalOverlay.Tone.DANGER, () -> {
                    try {
                        List<Map<String, Object>> convs = loadConversations();
                        convs.removeIf(c -> convId.equals(String.valueOf(c.get("id"))));
                        saveConversations(convs);
                        loadHistory();
                        toasts.show("Conversation deleted.", ToastHost.Tone.SUCCESS);
                    } catch (Exception e) {
                        toasts.show("Could not delete conversation: " + e.getMessage(), ToastHost.Tone.ERROR);
                    }
                }),
                new ModalOverlay.Action("Cancel", ModalOverlay.Tone.PRIMARY, modals::hide));
    }

    /* ---------------------------------------------------------------- history cell */

    private final class HistoryCell extends ListCell<Map<String, Object>> {
        private final Label titleLabel = new Label();
        private final Label dateLabel = new Label();

        private HistoryCell() {
            titleLabel.getStyleClass().add("search-title");
            dateLabel.getStyleClass().add("search-sub");
            HBox row = new HBox(10, titleLabel, dateLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);
            setGraphic(row);
        }

        @Override
        protected void updateItem(Map<String, Object> conv, boolean empty) {
            super.updateItem(conv, empty);
            if (empty || conv == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String userMsg = String.valueOf(conv.getOrDefault("userMessage", ""));
            if (userMsg.length() > 60) userMsg = userMsg.substring(0, 57) + "...";
            titleLabel.setText(userMsg);
            dateLabel.setText(String.valueOf(conv.getOrDefault("createdAt", "")));
            setText(null);
            setGraphic(new HBox(10, titleLabel, dateLabel));
        }
    }
}
