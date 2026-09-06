package com.pulseos.ui.views;

import com.pulseos.ai.AiFeaturesService;
import com.pulseos.healer.BuildArtifactScanner.ArtifactDetails;
import com.pulseos.healer.StorageHealerService;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Problem 1 + 2 fix, real UI: shows every heavy build-artifact folder found, clearly
 * separates "Active Project — Protected" from "Safe to Clean", and only deletes what
 * the user selects from the safe list. Nothing here silently deletes an active project.
 */
public class StorageHealerView extends VBox {

    public static class Row {
        final ArtifactDetails details;
        final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);

        Row(ArtifactDetails details) {
            this.details = details;
        }
    }

    private final StorageHealerService healerService;
    private final AiFeaturesService aiService;
    private final TableView<Row> table = new TableView<>();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final Label summaryLabel = new Label("Scan nahi hua abhi — 'Scan Now' dabao.");
    private final Label aiExplainLabel = new Label("");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Consumer<String> logCallback;
    private boolean scanStarted = false;

    public StorageHealerView(StorageHealerService healerService, AiFeaturesService aiService, Consumer<String> logCallback) {
        this.healerService = healerService;
        this.aiService = aiService;
        this.logCallback = logCallback;
        this.getStyleClass().add("card");
        this.setSpacing(12);
        setPadding(new Insets(4));
        buildUi();
    }

    private void buildUi() {
        Label title = new Label("💾 STORAGE HEALER — Ghost Build Artifacts");
        title.getStyleClass().add("card-header-title");

        spinner.setMaxSize(18, 18);
        spinner.setVisible(false);

        Button scanBtn = new Button("🔍 Scan Now");
        scanBtn.setOnAction(e -> runScan());

        Button cleanBtn = new Button("🧹 Clean Selected (Safe Only)");
        cleanBtn.getStyleClass().add("alert-btn-danger");
        cleanBtn.setOnAction(e -> runClean());

        Button aiExplainBtn = new Button("🤖 AI: Is this safe?");
        aiExplainBtn.setOnAction(e -> runAiExplain());

        HBox actionBar = new HBox(10, scanBtn, cleanBtn, aiExplainBtn, spinner);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        aiExplainLabel.setWrapText(true);
        aiExplainLabel.getStyleClass().add("info-callout-text");

        setupTable();

        VBox.setVgrow(table, Priority.ALWAYS);
        this.getChildren().addAll(title, actionBar, summaryLabel, aiExplainLabel, table);
        Platform.runLater(this::runScan);
    }

    private void setupTable() {
        TableColumn<Row, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cd -> cd.getValue().selected);
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);
        selectCol.setPrefWidth(60);

        TableColumn<Row, String> pathCol = new TableColumn<>("Folder");
        pathCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().details.path().toString()));
        pathCol.setPrefWidth(420);

        TableColumn<Row, String> sizeCol = new TableColumn<>("Size (MB)");
        sizeCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.1f", cd.getValue().details.getSizeInMb())));
        sizeCol.setPrefWidth(90);

        TableColumn<Row, String> daysCol = new TableColumn<>("Days Unused");
        daysCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(cd.getValue().details.daysUnused())));
        daysCol.setPrefWidth(100);

        TableColumn<Row, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().details.isActiveProject() ? "🟢 Active — Protected" : "⚪ Safe to Clean"));
        statusCol.setPrefWidth(160);

        table.getColumns().setAll(List.of(selectCol, pathCol, sizeCol, daysCol, statusCol));
        table.setEditable(true);
        table.setItems(rows);
        table.setPlaceholder(new Label("Koi scan result nahi — 'Scan Now' click karo."));
    }

    public void ensureLoaded() {
        if (rows.isEmpty() && !scanStarted) runScan();
    }

    private void runScan() {
        if (scanStarted) return;
        scanStarted = true;
        spinner.setVisible(true);
        summaryLabel.setText("Scanning...");
        Path home = Paths.get(System.getProperty("user.home"));

        healerService.analyzeCommonLocations(home).thenAccept(artifacts -> Platform.runLater(() -> {
            spinner.setVisible(false);
            rows.setAll(artifacts.stream().map(Row::new).collect(Collectors.toList()));

            long totalMb = (long) artifacts.stream().mapToDouble(ArtifactDetails::getSizeInMb).sum();
            long safeCount = artifacts.stream().filter(a -> !a.isActiveProject()).count();
            long protectedCount = artifacts.size() - safeCount;

            summaryLabel.setText(String.format(
                    "Found %d artifact folders (%,d MB total) — %d safe to clean, %d protected (active project).",
                    artifacts.size(), totalMb, safeCount, protectedCount));

            logCallback.accept("🔍 Scan complete: " + artifacts.size() + " artifacts found");
            scanStarted = false;
        }));
    }

    private void runAiExplain() {
        List<Row> selected = rows.stream().filter(r -> r.selected.get()).collect(Collectors.toList());
        ArtifactDetails target = !selected.isEmpty() ? selected.get(0).details
                : (!rows.isEmpty() ? rows.get(0).details : null);

        if (target == null) {
            aiExplainLabel.setText("Pehle scan karo — explain karne ke liye kuch hona chahiye.");
            return;
        }

        aiExplainLabel.setText("🤖 Thinking...");
        aiService.explainCleanupSafety(target).thenAccept(explanation ->
                Platform.runLater(() -> aiExplainLabel.setText("🤖 " + explanation)));
    }

    private void runClean() {
        List<ArtifactDetails> selected = rows.stream()
                .filter(r -> r.selected.get())
                .map(r -> r.details)
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            summaryLabel.setText("Pehle kuch select karo table mein.");
            return;
        }

        long protectedSelected = selected.stream().filter(ArtifactDetails::isActiveProject).count();

        healerService.quarantineAndClean(selected).thenAccept(freedBytes -> Platform.runLater(() -> {
            double freedMb = freedBytes / (1024.0 * 1024.0);
            summaryLabel.setText(String.format(
                    "Cleaned! Freed %.1f MB (moved to quarantine, permanent delete after 7 days).%s",
                    freedMb, protectedSelected > 0 ? " " + protectedSelected + " active project(s) were skipped for safety." : ""));
            logCallback.accept("✅ Storage Healer freed " + String.format("%.1f", freedMb) + " MB");
            runScan();
        }));
    }
}
