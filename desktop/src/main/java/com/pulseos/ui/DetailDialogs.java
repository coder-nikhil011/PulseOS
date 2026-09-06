package com.pulseos.ui;

import com.pulseos.healer.StorageCategoryScanner;
import com.pulseos.telemetry.SystemMetrics;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.function.LongConsumer;

/**
 * User ask: "click on CPU graph / pie chart / threads / temp / battery / RAM to
 * see live detail + take action". Each stat on the Health tab is now clickable and
 * opens one of these popups. Where the user asked for a "reduce" action (temp, RAM),
 * the dialog offers a real "Terminate process" button — actually ending the selected
 * process via ProcessHandle, which is the only honest way software can lower CPU-driven
 * heat or free memory (there's no OS API that just "reduces temperature" directly).
 */
public final class DetailDialogs {

    private DetailDialogs() {}

    public static void showCpuDetail(Window owner, SystemMetrics metrics) {
        VBox content = new VBox(10);
        content.getChildren().add(new Label(String.format("Overall CPU load: %.1f%%", metrics.getCpuLoadPercentage())));
        content.getChildren().add(new Label("Backend: OSHI process telemetry • sampled every 1s"));
        content.getChildren().add(new Label("Top processes by CPU usage right now:"));
        content.getChildren().add(buildProcessTable(metrics.getTopProcesses(), null));
        openDialog(owner, "CPU Usage — Live Detail", content, 560, 420);
    }

    public static void showThreadsDetail(Window owner, SystemMetrics metrics) {
        VBox content = new VBox(10);
        content.getChildren().add(new Label(String.format("Total live threads on system: %,d", metrics.getActiveThreadCount())));
        content.getChildren().add(new Label("Backend: OSHI process/thread telemetry"));
        content.getChildren().add(new Label("Which processes are creating the most threads:"));

        var byThreads = metrics.getTopProcesses().stream()
                .sorted((a, b) -> Integer.compare(b.threadCount(), a.threadCount()))
                .toList();
        content.getChildren().add(buildProcessTable(byThreads, null));
        openDialog(owner, "Thread Activity — Live Detail", content, 560, 420);
    }

    public static void showTempDetail(Window owner, SystemMetrics metrics, LongConsumer killAction,
                                       java.util.function.Supplier<java.util.concurrent.CompletableFuture<String>> aiDiagnosis) {
        VBox content = new VBox(10);
        boolean abnormal = metrics.getCoreTemperature() > 75.0;

        Label statusLabel = new Label(abnormal
                ? String.format("⚠️ %.0f°C is higher than normal (>75°C).", metrics.getCoreTemperature())
                : String.format("🟢 %.0f°C is within a normal range.", metrics.getCoreTemperature()));
        content.getChildren().add(statusLabel);

        if (abnormal) {
            content.getChildren().add(new Label(
                    "Software can't lower temperature directly — the real fix is reducing CPU load. " +
                    "Terminate the heaviest process below to cut heat generation:"));
            content.getChildren().add(buildProcessTable(metrics.getTopProcesses(), killAction));

            Label aiLabel = new Label("");
            aiLabel.setWrapText(true);
            aiLabel.getStyleClass().add("info-callout-text");
            Button aiBtn = new Button("🤖 Ask AI what's happening");
            aiBtn.setOnAction(e -> {
                aiLabel.setText("🤖 Thinking...");
                aiDiagnosis.get().thenAccept(text ->
                        javafx.application.Platform.runLater(() -> aiLabel.setText("🤖 " + text)));
            });
            content.getChildren().addAll(aiBtn, aiLabel);
        } else {
            content.getChildren().add(new Label("No action needed right now."));
        }
        openDialog(owner, "Core Temperature — Detail", content, 560, 480);
    }

    public static void showRamDetail(Window owner, SystemMetrics metrics, LongConsumer killAction) {
        VBox content = new VBox(10);
        content.getChildren().add(new Label(String.format("RAM in use: %.2f / %.2f GB",
                metrics.getUsedMemoryGb(), metrics.getTotalMemoryGb())));
        content.getChildren().add(new Label("Backend: OSHI GlobalMemory + process telemetry"));
        content.getChildren().add(new Label("Where your memory is actually going — terminate anything you don't need:"));
        content.getChildren().add(buildProcessTable(metrics.getTopByRam(), killAction));
        openDialog(owner, "RAM Usage — Free Up Memory", content, 560, 420);
    }

    public static void showBatteryDetail(Window owner, SystemMetrics metrics) {
        VBox content = new VBox(8);
        content.getChildren().add(new Label("Backend: OSHI PowerSources • values depend on OS-exposed battery sensors"));
        if (metrics.getBatteryPercent() <= 0) {
            content.getChildren().add(new Label("No battery detected — this looks like a desktop machine."));
        } else {
            content.getChildren().add(new Label("Charge: " + metrics.getBatteryPercent() + "%"));
            content.getChildren().add(new Label("Status: " + (metrics.isBatteryCharging() ? "Charging" : "On battery")));
            content.getChildren().add(new Label(metrics.getBatteryHealthPercent() > 0
                    ? "Battery health: " + metrics.getBatteryHealthPercent() + "% of original design capacity"
                    : "Battery health: unavailable on this system"));
            content.getChildren().add(new Label(metrics.getBatteryCycleCount() > 0
                    ? "Charge cycles: " + metrics.getBatteryCycleCount()
                    : "Charge cycles: unavailable on this system"));
        }
        openDialog(owner, "Battery — Status Detail", content, 420, 260);
    }

    public static void showStorageDetail(Window owner, StorageCategoryScanner.CategoryResult result, Runnable goToStorageHealer) {
        VBox content = new VBox(10);
        content.getChildren().add(new Label("Storage breakdown:"));
        content.getChildren().add(new Label("Backend: StorageCategoryScanner + BuildArtifactScanner • dashboard scan uses Downloads/Desktop/Documents/Pictures/Projects/Developer"));

        TableView<java.util.Map.Entry<String, Double>> categoryTable = new TableView<>();
        TableColumn<java.util.Map.Entry<String, Double>, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getKey()));
        catCol.setPrefWidth(220);
        TableColumn<java.util.Map.Entry<String, Double>, String> sizeCol = new TableColumn<>("Size (MB)");
        sizeCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.0f", cd.getValue().getValue())));
        sizeCol.setPrefWidth(120);
        categoryTable.getColumns().setAll(List.of(catCol, sizeCol));
        categoryTable.getItems().addAll(result.categoryMb().entrySet());
        categoryTable.setPrefHeight(160);

        content.getChildren().add(categoryTable);

        content.getChildren().add(new Label("Large / unused items found:"));
        TableView<StorageCategoryScanner.LargeItem> itemTable = new TableView<>();
        TableColumn<StorageCategoryScanner.LargeItem, String> nameCol = new TableColumn<>("Path");
        nameCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().path().toString()));
        nameCol.setPrefWidth(320);
        TableColumn<StorageCategoryScanner.LargeItem, String> szCol = new TableColumn<>("Size (MB)");
        szCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(String.format("%.0f", cd.getValue().sizeMb())));
        szCol.setPrefWidth(100);
        TableColumn<StorageCategoryScanner.LargeItem, String> daysCol = new TableColumn<>("Days Unused");
        daysCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(String.valueOf(cd.getValue().daysUnused())));
        daysCol.setPrefWidth(100);
        itemTable.getColumns().setAll(List.of(nameCol, szCol, daysCol));
        itemTable.getItems().addAll(result.largeItems());
        itemTable.setPrefHeight(160);
        VBox.setVgrow(itemTable, Priority.ALWAYS);

        content.getChildren().add(itemTable);

        Button cleanBtn = new Button("🧹 Go clean this up in Storage Healer");
        cleanBtn.getStyleClass().add("alert-btn-danger");
        cleanBtn.setOnAction(e -> {
            if (goToStorageHealer != null) goToStorageHealer.run();
        });
        content.getChildren().add(cleanBtn);

        openDialog(owner, "Storage Breakdown — Detail", content, 620, 560);
    }

    private static TableView<SystemMetrics.ProcessInfo> buildProcessTable(List<SystemMetrics.ProcessInfo> processes, LongConsumer killAction) {
        TableView<SystemMetrics.ProcessInfo> table = new TableView<>();

        TableColumn<SystemMetrics.ProcessInfo, String> nameCol = new TableColumn<>("Process");
        nameCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().name()));
        nameCol.setPrefWidth(180);

        TableColumn<SystemMetrics.ProcessInfo, String> cpuCol = new TableColumn<>("CPU %");
        cpuCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.1f%%", cd.getValue().cpuPercent())));
        cpuCol.setPrefWidth(70);

        TableColumn<SystemMetrics.ProcessInfo, String> ramCol = new TableColumn<>("RAM (MB)");
        ramCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.0f", cd.getValue().getRamMb())));
        ramCol.setPrefWidth(90);

        TableColumn<SystemMetrics.ProcessInfo, String> threadCol = new TableColumn<>("Threads");
        threadCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(cd.getValue().threadCount())));
        threadCol.setPrefWidth(70);

        table.getColumns().setAll(List.of(nameCol, cpuCol, ramCol, threadCol));

        if (killAction != null) {
            TableColumn<SystemMetrics.ProcessInfo, Void> actionCol = new TableColumn<>("Action");
            actionCol.setPrefWidth(140);
            actionCol.setCellFactory(col -> new TableCell<>() {
                private final Button killBtn = new Button("Terminate");
                {
                    killBtn.getStyleClass().add("alert-btn-danger");
                    killBtn.setOnAction(e -> {
                        SystemMetrics.ProcessInfo p = getTableView().getItems().get(getIndex());
                        killAction.accept(p.pid());
                        killBtn.setText("Terminated");
                        killBtn.setDisable(true);
                    });
                }
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : killBtn);
                }
            });
            table.getColumns().add(actionCol);
        }

        table.getItems().addAll(processes);
        table.setPrefHeight(260);
        return table;
    }

    private static void openDialog(Window owner, String title, VBox content, double width, double height) {
        content.setPadding(new Insets(16));
        content.getStyleClass().add("card");

        Button closeBtn = new Button("Close");
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        content.getChildren().add(footer);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        Scene scene = new Scene(content, width, height);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        closeBtn.setOnAction(e -> stage.close());
        stage.show();
    }
}
