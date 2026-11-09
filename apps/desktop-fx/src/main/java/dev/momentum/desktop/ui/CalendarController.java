package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Calendar: interactive month view showing goals and tasks on their due dates.
 * Click a day to create a goal/task. Click an item to open its detail.
 * Everything reads from / writes to the real SQLite database.
 */
public class CalendarController implements PageView {

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);
    private final AtomicReference<YearMonth> current = new AtomicReference<>(YearMonth.now());
    private final Label monthLabel = new Label();
    private final GridPane dayGrid = new GridPane();

    public CalendarController(ServiceHost host, ToastHost toasts, ModalOverlay modals,
                              NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), navBar(), dayGrid);

        dayGrid.getStyleClass().add("calendar-grid");
        dayGrid.setHgap(8);
        dayGrid.setVgap(8);
        for (int col = 0; col < 7; col++) {
            dayGrid.getColumnConstraints().add(new ColumnConstraints(0, 0, Double.MAX_VALUE,
                    Priority.ALWAYS, HPos.CENTER, true));
        }
        load();
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
        Label title = new Label("Calendar");
        title.getStyleClass().add("page-title");
        Button today = new Button("Today");
        today.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        today.setOnAction(event -> {
            current.set(YearMonth.now());
            load();
        });
        HBox header = new HBox(10, title, today);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node navBar() {
        Button prev = new Button("\u25C4");
        prev.getStyleClass().addAll("btn", "btn-ghost");
        prev.setOnAction(event -> {
            current.set(current.get().minusMonths(1));
            load();
        });
        Button next = new Button("\u25BA");
        next.getStyleClass().addAll("btn", "btn-ghost");
        next.setOnAction(event -> {
            current.set(current.get().plusMonths(1));
            load();
        });
        monthLabel.getStyleClass().add("calendar-month");
        ComboBox<String> yearBox = new ComboBox<>();
        yearBox.setPrefWidth(80);
        int thisYear = YearMonth.now().getYear();
        for (int y = thisYear - 5; y <= thisYear + 5; y++) {
            yearBox.getItems().add(String.valueOf(y));
        }
        yearBox.setValue(String.valueOf(current.get().getYear()));
        yearBox.setOnAction(event -> {
            try {
                int y = Integer.parseInt(yearBox.getValue());
                current.set(YearMonth.of(y, current.get().getMonthValue()));
                load();
            } catch (NumberFormatException ignored) {}
        });
        HBox bar = new HBox(10, prev, monthLabel, yearBox, next);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("calendar-nav");
        return bar;
    }

    /* ---------------------------------------------------------------- data */

    private void load() {
        monthLabel.setText(current.get().getMonth().toString() + " " + current.get().getYear());

        final AtomicReference<List<Map<String, Object>>> items = new AtomicReference<>(new ArrayList<>());
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        List<Map<String, Object>> all = new ArrayList<>();
                        // Goals with deadlines
                        for (Map<String, Object> g : repository.listGoals(null)) {
                            String dl = valueOr(g, "deadline", "");
                            if (dl.isBlank()) continue;
                            Map<String, Object> entry = new LinkedHashMap<>(g);
                            entry.put("itemType", "goal");
                            entry.put("itemDate", dl);
                            all.add(entry);
                        }
                        // Tasks with due dates
                        for (Map<String, Object> g : repository.listGoals(null)) {
                            String goalId = String.valueOf(g.get("id"));
                            for (Map<String, Object> t : repository.listTasks(goalId, null)) {
                                String dd = valueOr(t, "dueDate", "");
                                if (dd.isBlank()) continue;
                                Map<String, Object> entry = new LinkedHashMap<>(t);
                                entry.put("itemType", "task");
                                entry.put("itemDate", dd);
                                entry.put("goalId", goalId);
                                all.add(entry);
                            }
                        }
                        items.set(all);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> renderGrid(items.get())));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load calendar: " + task.getException().getMessage(),
                        ToastHost.Tone.ERROR)));
        new Thread(task, "calendar-loader").start();
    }

    private void renderGrid(List<Map<String, Object>> items) {
        dayGrid.getChildren().clear();

        YearMonth ym = current.get();
        LocalDate first = ym.atDay(1);
        int startDow = first.getDayOfWeek().getValue() % 7; // Monday=0..Sunday=6
        LocalDate end = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();

        String[] weekdays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (int d = 0; d < 7; d++) {
            Label wd = new Label(weekdays[d]);
            wd.getStyleClass().add("calendar-weekday");
            dayGrid.add(wd, d, 0);
        }

        // Index items by day-of-month.
        Map<Integer, List<Map<String, Object>>> byDay = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String dateStr = valueOr(item, "itemDate", "");
            if (dateStr.isBlank()) continue;
            try {
                LocalDate date = LocalDate.parse(dateStr);
                if (date.getYear() == ym.getYear() && date.getMonth() == ym.getMonth()) {
                    byDay.computeIfAbsent(date.getDayOfMonth(), k -> new ArrayList<>()).add(item);
                }
            } catch (Exception ignored) {}
        }

        int row = 1;
        int col = startDow;
        for (int day = 1; day <= end.getDayOfMonth(); day++) {
            LocalDate dayDate = LocalDate.of(ym.getYear(), ym.getMonthValue(), day);
            boolean isToday = dayDate.equals(today);
            boolean isPast = dayDate.isBefore(today);

            VBox dayCell = new VBox(4);
            dayCell.getStyleClass().add("calendar-day");
            dayCell.setMinHeight(110);
            dayCell.setPrefHeight(110);
            if (isToday) {
                dayCell.getStyleClass().add("calendar-day-today");
            }

            Label dayNum = new Label(String.valueOf(day));
            dayNum.getStyleClass().add("calendar-day-num");
            if (isToday) {
                dayNum.getStyleClass().add("calendar-day-num-today");
            }

            VBox events = new VBox(2);
            events.getStyleClass().add("calendar-events");
            for (Map<String, Object> item : byDay.getOrDefault(day, List.of())) {
                String type = valueOr(item, "itemType", "goal");
                Label ev = new Label(valueOr(item, "title", "Untitled"));
                ev.getStyleClass().add("calendar-event");
                ev.getStyleClass().add("cal-" + type);
                ev.setWrapText(true);
                ev.setMaxWidth(Double.MAX_VALUE);

                boolean completed = Boolean.TRUE.equals(item.get("completed"));
                if (completed) {
                    ev.getStyleClass().add("calendar-event-completed");
                    ev.setOpacity(0.6);
                }

                if (isPast && !completed && "goal".equals(type)) {
                    ev.getStyleClass().add("calendar-event-overdue");
                }

                String itemId = String.valueOf(item.get("id"));
                String goalId = valueOr(item, "goalId", "");
                ev.setOnMouseClicked(event -> {
                    if ("goal".equals(type)) {
                        navigation.select(NavDestination.GOAL_DETAIL, itemId);
                    } else {
                        navigation.select(NavDestination.GOAL_DETAIL, goalId);
                    }
                });
                events.getChildren().add(ev);
            }
            if (events.getChildren().isEmpty()) {
                events.setManaged(false);
                events.setVisible(false);
            }

            dayCell.getChildren().addAll(dayNum, events);

            // Click empty day to create goal
            final int dayForClick = day;
            dayCell.setOnMouseClicked(event -> {
                if (event.getTarget() == dayCell || event.getTarget() == dayNum) {
                    LocalDate selectedDate = LocalDate.of(ym.getYear(), ym.getMonthValue(), dayForClick);
                    createGoalOnDate(selectedDate);
                }
            });

            dayGrid.add(dayCell, col, row);

            col++;
            if (col == 7) {
                col = 0;
                row++;
            }
        }
    }

    /* ---------------------------------------------------------------- create on date */

    private void createGoalOnDate(LocalDate date) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Create goal");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        TextField titleField = new TextField();
        titleField.setPromptText("Goal title");
        TextArea notesField = new TextArea();
        notesField.setPromptText("Notes");
        notesField.setPrefRowCount(3);
        ComboBox<String> priorityField = new ComboBox<>();
        priorityField.getItems().addAll("low", "medium", "high", "critical");
        priorityField.setValue("medium");

        content.getChildren().addAll(field("Title *", titleField),
                field("Notes", notesField),
                field("Priority", priorityField));
        dialog.getDialogPane().setContent(content);

        Platform.runLater(titleField::requestFocus);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                String titleText = titleField.getText();
                if (titleText == null || titleText.isBlank()) return null;
                Map<String, Object> goal = new LinkedHashMap<>();
                goal.put("id", UUID.randomUUID().toString());
                goal.put("title", titleText);
                goal.put("notes", notesField.getText());
                goal.put("priority", priorityField.getValue());
                goal.put("deadline", date.toString());
                goal.put("createdAt", java.time.Instant.now().toString());
                goal.put("updatedAt", java.time.Instant.now().toString());
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
                        repository.recordActivity("goal-created",
                                String.valueOf(result.get("id")), null, "Created goal from calendar");
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                toasts.show("Goal created", ToastHost.Tone.SUCCESS);
                load();
            } catch (RuntimeException e) {
                toasts.show("Could not create goal: " + e.getCause().getMessage(), ToastHost.Tone.ERROR);
            }
        });
    }

    private Node field(String labelText, Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(6, label, control);
        box.getStyleClass().add("form-field-box");
        return box;
    }

    /* ---------------------------------------------------------------- helpers */

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
