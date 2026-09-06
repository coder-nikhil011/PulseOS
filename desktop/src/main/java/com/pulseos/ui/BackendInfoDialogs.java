package com.pulseos.ui;

import com.pulseos.telemetry.SystemMetrics;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.util.List;

/** Compact, explainable backend-data inspector used by every dashboard card. */
public final class BackendInfoDialogs {
    private BackendInfoDialogs() {}

    public static void showHealth(Window owner, SystemMetrics m, double storageUsed, String score,
                                  String performance, String hardware, String storage, String battery) {
        double ramPct = m.getTotalMemoryGb() <= 0 ? 0 : m.getUsedMemoryGb() / m.getTotalMemoryGb() * 100.0;
        showData(owner, "Device Health Score — Backend Detail",
                "The score is computed from live telemetry, not a hard-coded value.", List.of(
                        "Final score: " + score,
                        "Performance component: " + performance + "  | CPU %.1f%%, RAM %.1f%%".formatted(m.getCpuLoadPercentage(), ramPct),
                        "Hardware component: " + hardware + "  | Temperature %.1f°C".formatted(m.getCoreTemperature()),
                        "Storage component: " + storage + "  | Used %.1f%%".formatted(storageUsed * 100),
                        "Battery component: " + battery,
                        "Telemetry source: OSHI HardwareAbstractionLayer",
                        "Process source: OSHI process snapshot / ProcessHandle",
                        "Scoring mode: abnormal-pressure weighted; normal activity is not treated as a fault.",
                        "Last sample: live dashboard tick."));
    }

    public static void showProblems(Window owner, SystemMetrics m, double storageUsed) {
        double ramPct = m.getTotalMemoryGb() <= 0 ? 0 : m.getUsedMemoryGb() / m.getTotalMemoryGb() * 100.0;
        boolean cpu = m.getCpuLoadPercentage() > 80;
        boolean ram = ramPct > 85;
        boolean temp = m.getCoreTemperature() > 78;
        boolean storage = storageUsed > .88;
        showData(owner, "Active Problems — Detection Backend",
                "Thresholds are explicit so the demo can explain why an alert appears.", List.of(
                        "CPU alert threshold: > 80% | Current: %.1f%% | %s".formatted(m.getCpuLoadPercentage(), cpu ? "TRIGGERED" : "OK"),
                        "RAM alert threshold: > 85% | Current: %.1f%% | %s".formatted(ramPct, ram ? "TRIGGERED" : "OK"),
                        "Temperature alert threshold: > 78°C | Current: %.1f°C | %s".formatted(m.getCoreTemperature(), temp ? "TRIGGERED" : "OK"),
                        "Storage alert threshold: > 88% | Current: %.1f%% | %s".formatted(storageUsed * 100, storage ? "TRIGGERED" : "OK"),
                        "Top CPU process: " + (m.getTopProcesses().isEmpty() ? "none" : m.getTopProcesses().get(0).name()),
                        "Decision engine: local deterministic rules; no hidden cloud dependency."));
    }

    public static void showHardware(Window owner) {
        try {
            oshi.SystemInfo si = new oshi.SystemInfo();
            var p = si.getHardware().getProcessor();
            showData(owner, "Hardware Intelligence — Backend Data", "Live processor inventory from OSHI.", List.of(
                    "Processor: " + p.getProcessorIdentifier().getName(),
                    "Vendor: " + p.getProcessorIdentifier().getVendor(),
                    "Physical cores: " + p.getPhysicalProcessorCount(),
                    "Logical processors: " + p.getLogicalProcessorCount(),
                    "Base/vendor frequency: " + String.format("%.2f GHz", p.getProcessorIdentifier().getVendorFreq()/1_000_000_000.0),
                    "Max frequency: " + String.format("%.2f GHz", p.getMaxFreq()/1_000_000_000.0),
                    "Backend: OSHI HardwareAbstractionLayer"));
        } catch (Exception e) {
            showData(owner, "Hardware Intelligence — Backend Data", "Hardware query failed safely.", List.of("Error: " + e.getMessage()));
        }
    }

    public static void showData(Window owner, String title, String subtitle, List<String> lines) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("card");
        Label heading = new Label(title);
        heading.getStyleClass().add("card-header-title");
        Label sub = new Label(subtitle);
        sub.setWrapText(true);
        sub.getStyleClass().add("muted-note");
        box.getChildren().addAll(heading, sub);
        for (String line : lines) {
            Label row = new Label(line);
            row.setWrapText(true);
            row.getStyleClass().add("backend-row");
            box.getChildren().add(row);
        }
        Button close = new Button("Close");
        close.getStyleClass().add("action-btn");
        box.getChildren().add(close);
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        Scene scene = new Scene(box, 620, Math.min(620, 190 + lines.size() * 42));
        if (owner != null && owner.getScene() != null) scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        stage.setScene(scene);
        close.setOnAction(e -> stage.close());
        stage.show();
    }
}
