package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Import/Export screen: export goals to CSV or PDF, import goals from CSV.
 * Uses SqliteRepository for data access. All file I/O is local (no browser).
 */
public class ImportExportController implements PageView {

    private final ServiceHost host;
    private final ToastHost toasts;
    private final ModalOverlay modals;
    private final NavigationManager navigation;

    private final VBox root = new VBox(16);

    public ImportExportController(ServiceHost host, ToastHost toasts, ModalOverlay modals,
                                  NavigationManager navigation) {
        this.host = host;
        this.toasts = toasts;
        this.modals = modals;
        this.navigation = navigation;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), exportCard(), importCard());
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {}

    /* ---------------------------------------------------------------- layout */

    private Node header() {
        Label title = new Label("Import / Export");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(10, title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node exportCard() {
        Label title = new Label("Export");
        title.getStyleClass().add("card-title");
        Label sub = new Label("Export all goals to CSV or PDF for backup or reporting.");
        sub.getStyleClass().add("hero-sub");

        Button csvBtn = new Button("Export goals to CSV");
        csvBtn.getStyleClass().addAll("btn", "btn-primary");
        csvBtn.setOnAction(event -> exportCsv());

        Button pdfBtn = new Button("Export goals to PDF");
        pdfBtn.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        pdfBtn.setOnAction(event -> exportPdf());

        VBox card = new VBox(10, title, sub, csvBtn, pdfBtn);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node importCard() {
        Label title = new Label("Import");
        title.getStyleClass().add("card-title");
        Label sub = new Label("Import goals from CSV. You can preview matches and handle duplicates before confirming.");
        sub.getStyleClass().add("hero-sub");

        Button importBtn = new Button("Import goals from CSV");
        importBtn.getStyleClass().addAll("btn", "btn-primary");
        importBtn.setOnAction(event -> importCsv());

        VBox card = new VBox(10, title, sub, importBtn);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /* ---------------------------------------------------------------- export CSV */

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export goals to CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName("momentum-goals.csv");
        Stage owner = (Stage) root.getScene().getWindow();
        File file = chooser.showSaveDialog(owner);
        if (file == null) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        String csv = repository.exportGoalsCsv();
                        Files.writeString(file.toPath(), csv);
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() ->
                toasts.show("Exported goals to " + file.getName(), ToastHost.Tone.SUCCESS)));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Export failed: " + task.getException().getCause().getMessage(), ToastHost.Tone.ERROR)));
        new Thread(task, "csv-export").start();
    }

    /* ---------------------------------------------------------------- export PDF */

    private void exportPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export goals to PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName("momentum-goals.pdf");
        Stage owner = (Stage) root.getScene().getWindow();
        File file = chooser.showSaveDialog(owner);
        if (file == null) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        List<Map<String, Object>> goals = repository.listGoals(null);
                        try (PDDocument doc = new PDDocument()) {
                            PDPage page = new PDPage();
                            doc.addPage(page);
                            PDPageContentStream cs = new PDPageContentStream(doc, page);
                            try {
                                cs.beginText();
                                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                                cs.newLineAtOffset(50, 700);
                                cs.showText("MOMENTUM - Goals Export");
                                cs.endText();

                                float y = 650;
                                cs.beginText();
                                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                                cs.newLineAtOffset(50, y);
                                cs.showText("ID");
                                cs.newLineAtOffset(60, 0);
                                cs.showText("Title");
                                cs.newLineAtOffset(150, 0);
                                cs.showText("Status");
                                cs.newLineAtOffset(80, 0);
                                cs.showText("Created");
                                cs.endText();

                                for (Map<String, Object> g : goals) {
                                    y -= 15;
                                    if (y < 50) {
                                        cs.endText();
                                        cs.close();
                                        PDPage newPage = new PDPage();
                                        doc.addPage(newPage);
                                        cs = new PDPageContentStream(doc, newPage);
                                        cs.beginText();
                                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                                        cs.newLineAtOffset(50, 700);
                                        y = 685;
                                    }
                                    cs.beginText();
                                    cs.newLineAtOffset(50, y);
                                    cs.showText(String.valueOf(g.get("id")));
                                    cs.newLineAtOffset(60, 0);
                                    cs.showText(String.valueOf(g.get("title")));
                                    cs.newLineAtOffset(150, 0);
                                    cs.showText(valueOr(g, "status", "active"));
                                    cs.newLineAtOffset(80, 0);
                                    cs.showText(valueOr(g, "createdAt", ""));
                                    cs.endText();
                                }
                            } finally {
                                cs.close();
                            }
                            doc.save(file);
                        }
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() ->
                toasts.show("Exported goals to " + file.getName(), ToastHost.Tone.SUCCESS)));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("PDF export failed: " + task.getException().getCause().getMessage(), ToastHost.Tone.ERROR)));
        new Thread(task, "pdf-export").start();
    }

    /* ---------------------------------------------------------------- import CSV */

    private void importCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import goals from CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        Stage owner = (Stage) root.getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        try {
            String csvContent = Files.readString(file.toPath());
            String[] lines = csvContent.split("\n");
            if (lines.length < 2) {
                toasts.show("CSV file is empty or has no data rows.", ToastHost.Tone.ERROR);
                return;
            }

            // Parse header and rows
            String[] header = parseCsvLine(lines[0].trim());
            List<Map<String, Object>> rows = new java.util.ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                String[] fields = parseCsvLine(line);
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (int j = 0; j < header.length && j < fields.length; j++) {
                    row.put(header[j], fields[j]);
                }
                rows.add(row);
            }

            if (rows.isEmpty()) {
                toasts.show("CSV file contains no data rows.", ToastHost.Tone.ERROR);
                return;
            }

            showImportPreview(rows);
        } catch (IOException e) {
            toasts.show("Could not read CSV file: " + e.getMessage(), ToastHost.Tone.ERROR);
        }
    }

    private void showImportPreview(List<Map<String, Object>> rows) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Import preview");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Label info = new Label(rows.size() + " goal(s) found in CSV. Import will create new goals.");
        info.getStyleClass().add("hero-sub");

        ListView<Map<String, Object>> list = new ListView<>();
        list.setCellFactory(l -> new PreviewCell());
        list.getItems().addAll(rows);
        list.setPrefHeight(300);

        VBox content = new VBox(10, info, list);
        content.setMaxWidth(Double.MAX_VALUE);
        dialog.getDialogPane().setContent(content);

        Platform.runLater(() -> dialog.getDialogPane().lookupButton(ButtonType.OK).requestFocus());

        dialog.showAndWait().ifPresent(confirmed -> {
            if (!confirmed) return;
            performImport(rows);
        });
    }

    private void performImport(List<Map<String, Object>> rows) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                host.withRepository(repository -> {
                    try {
                        int imported = 0;
                        for (Map<String, Object> row : rows) {
                            Map<String, Object> goal = new java.util.LinkedHashMap<>();
                            goal.put("id", java.util.UUID.randomUUID().toString());
                            goal.put("title", String.valueOf(row.getOrDefault("title", "Untitled")));
                            goal.put("description", String.valueOf(row.getOrDefault("description", "")));
                            goal.put("notes", String.valueOf(row.getOrDefault("notes", "")));
                            goal.put("categoryId", String.valueOf(row.getOrDefault("category_id", "")));
                            goal.put("status", String.valueOf(row.getOrDefault("status", "active")));
                            goal.put("kind", String.valueOf(row.getOrDefault("kind", "one-time")));
                            goal.put("priority", String.valueOf(row.getOrDefault("priority", "medium")));
                            goal.put("startDate", String.valueOf(row.getOrDefault("start_date", "")));
                            goal.put("deadline", String.valueOf(row.getOrDefault("deadline", "")));
                            goal.put("createdAt", java.time.Instant.now().toString());
                            goal.put("updatedAt", java.time.Instant.now().toString());
                            repository.upsertGoal(goal);
                            repository.recordActivity("goal-imported", String.valueOf(goal.get("id")), null,
                                    "Imported goal from CSV");
                            imported++;
                        }
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() ->
                toasts.show("Imported " + rows.size() + " goals", ToastHost.Tone.SUCCESS)));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Import failed: " + task.getException().getCause().getMessage(), ToastHost.Tone.ERROR)));
        new Thread(task, "csv-import").start();
    }

    /* ---------------------------------------------------------------- preview cell */

    private final class PreviewCell extends ListCell<Map<String, Object>> {
        private final Label titleLabel = new Label();
        private final Label statusLabel = new Label();

        private PreviewCell() {
            titleLabel.getStyleClass().add("search-title");
            statusLabel.getStyleClass().add("search-sub");
            HBox row = new HBox(10, titleLabel, statusLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);
            setGraphic(row);
        }

        @Override
        protected void updateItem(Map<String, Object> row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            titleLabel.setText(valueOr(row, "title", "Untitled"));
            statusLabel.setText(valueOr(row, "status", "active"));
            setText(null);
            setGraphic(new HBox(10, titleLabel, statusLabel));
        }
    }

    /* ---------------------------------------------------------------- helpers */

    private static String valueOr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static String[] parseCsvLine(String line) {
        java.util.List<String> fields = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
