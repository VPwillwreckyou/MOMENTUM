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
 * Calendar: month view showing goals with deadlines on their due dates.
 * Read-only view over the real SQLite database; click a goal to open it.
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
        HBox header = new HBox(10, title);
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
        HBox bar = new HBox(10, prev, monthLabel, next);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("calendar-nav");
        return bar;
    }

    /* ---------------------------------------------------------------- data */

    private void load() {
        monthLabel.setText(current.get().getMonth().toString() + " " + current.get().getYear());

        final AtomicReference<List<Map<String, Object>>> goals = new AtomicReference<>(new ArrayList<>());
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        List<Map<String, Object>> all = repository.listGoals(null);
                        goals.set(all.stream()
                                .filter(g -> valueOr(g, "deadline", "") != null && !valueOr(g, "deadline", "").isBlank())
                                .collect(Collectors.toList()));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> renderGrid(goals.get())));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Could not load calendar: " + task.getException().getMessage(),
                        ToastHost.Tone.ERROR)));
        new Thread(task, "calendar-loader").start();
    }

    private void renderGrid(List<Map<String, Object>> goals) {
        dayGrid.getChildren().clear();

        YearMonth ym = current.get();
        LocalDate first = ym.atDay(1);
        int startDow = first.getDayOfWeek().getValue() % 7; // Monday=0..Sunday=6
        LocalDate end = ym.atEndOfMonth();

        String[] weekdays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (int d = 0; d < 7; d++) {
            Label wd = new Label(weekdays[d]);
            wd.getStyleClass().add("calendar-weekday");
            dayGrid.add(wd, d, 0);
        }

        // Index goals by day-of-month.
        Map<Integer, List<Map<String, Object>>> byDay = new LinkedHashMap<>();
        for (Map<String, Object> g : goals) {
            String dl = valueOr(g, "deadline", "");
            if (dl.isBlank()) continue;
            try {
                LocalDate date = LocalDate.parse(dl);
                if (date.getYear() == ym.getYear() && date.getMonth() == ym.getMonth()) {
                    byDay.computeIfAbsent(date.getDayOfMonth(), k -> new ArrayList<>()).add(g);
                }
            } catch (Exception ignored) {
            }
        }

        int row = 1;
        int col = startDow;
        for (int day = 1; day <= end.getDayOfMonth(); day++) {
            VBox dayCell = new VBox(4);
            dayCell.getStyleClass().add("calendar-day");
            dayCell.setMinHeight(95);
            dayCell.setPrefHeight(95);

            Label dayNum = new Label(String.valueOf(day));
            dayNum.getStyleClass().add("calendar-day-num");

            VBox events = new VBox(2);
            events.getStyleClass().add("calendar-events");
            for (Map<String, Object> g : byDay.getOrDefault(day, List.of())) {
                Label ev = new Label(valueOr(g, "title", "Untitled"));
                ev.getStyleClass().add("calendar-event");
                ev.setWrapText(true);
                ev.setOnMouseClicked(event -> navigation.select(NavDestination.GOAL_DETAIL, g.get("id")));
                events.getChildren().add(ev);
            }
            if (events.getChildren().isEmpty()) {
                events.setManaged(false);
                events.setVisible(false);
            }

            dayCell.getChildren().addAll(dayNum, events);
            dayGrid.add(dayCell, col, row);

            col++;
            if (col == 7) {
                col = 0;
                row++;
            }
        }
    }

    /* ---------------------------------------------------------------- helpers */

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
