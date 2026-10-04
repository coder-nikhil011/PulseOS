package com.pulseos.ui.views;

import com.pulseos.telemetry.NetworkTelemetryService;
import com.pulseos.telemetry.PlatformCapabilityService;
import com.pulseos.telemetry.SystemMetrics;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Component-by-component health center. It never marks an unavailable sensor as healthy. */
public final class HardwareHealthView extends VBox {
    private record Card(VBox container, Label status, Label summary, Button diagnose, Button fix) {}

    private final GridPane grid = new GridPane();
    private final Map<String, Card> cards = new LinkedHashMap<>();
    private final Consumer<String> inspectAction;
    private final Consumer<String> diagnoseAction;
    private final Consumer<String> fixAction;
    private final Consumer<String> navigateAction;
    private PlatformCapabilityService.Capabilities capabilities = PlatformCapabilityService.Capabilities.empty();

    public HardwareHealthView(Consumer<String> inspectAction, Consumer<String> diagnoseAction,
                              Consumer<String> fixAction, Consumer<String> navigateAction) {
        this.inspectAction = inspectAction;
        this.diagnoseAction = diagnoseAction;
        this.fixAction = fixAction;
        this.navigateAction = navigateAction;
        getStyleClass().add("hardware-health-view");
        setSpacing(14);
        setPadding(new Insets(4));

        Label title = new Label("HARDWARE HEALTH CENTER");
        title.getStyleClass().add("card-header-title");
        Label subtitle = new Label("Every component reports its current state, evidence and available diagnosis path.");
        subtitle.getStyleClass().add("muted-note");
        subtitle.setWrapText(true);

        javafx.scene.layout.HBox quickNavigation = new javafx.scene.layout.HBox(7);
        quickNavigation.setAlignment(Pos.CENTER_LEFT);
        quickNavigation.getStyleClass().add("hardware-quick-nav");
        addNavigationButton(quickNavigation, "Overview", "overview");
        addNavigationButton(quickNavigation, "Smart Router", "router");
        addNavigationButton(quickNavigation, "Storage Healer", "storage");
        addNavigationButton(quickNavigation, "Converter", "converter");
        addNavigationButton(quickNavigation, "Settings", "settings");

        grid.setHgap(10);
        grid.setVgap(10);
        String[][] components = {
                {"CPU / Processor", "CPU load, frequency and process pressure"},
                {"RAM / Memory", "Memory usage and pressure"},
                {"GPU / Graphics", "Vendor metrics depend on driver support"},
                {"Storage / SSD", "Capacity and safe storage signals"},
                {"Battery / Power", "Capacity, charging and cycle data"},
                {"Fan / Cooling", "Fan RPM and cooling response"},
                {"Thermal Sensors", "Temperature and throttling signals"},
                {"Network", "DNS, reachability and latency"},
                {"Wi-Fi / Bluetooth", "Wireless adapter and connection signals"},
                {"Display / Input", "Display, keyboard and touchpad signals"},
                {"Audio / Camera", "Microphone, speakers and camera signals"},
                {"USB / Peripherals", "USB, external display and accessory events"},
                {"Motherboard / Firmware", "Board identity and firmware inventory"},
                {"Startup / Crashes", "Boot, crash and recovery events"},
                {"OS / Drivers", "Services, updates and driver events"},
                {"Security", "Protection and suspicious-system-event signals"}
        };
        for (int i = 0; i < components.length; i++) {
            int column = i % 2;
            int row = i / 2;
            VBox card = createCard(components[i][0], components[i][1]);
            grid.add(card, column, row);
            GridPane.setFillWidth(card, true);
        }
        getChildren().addAll(title, quickNavigation, subtitle, grid);
    }

    private void addNavigationButton(javafx.scene.layout.HBox navigation, String label, String target) {
        Button button = new Button(label);
        button.getStyleClass().add("quick-nav-btn");
        button.setOnAction(event -> navigateAction.accept(target));
        navigation.getChildren().add(button);
    }

    private VBox createCard(String component, String hint) {
        VBox card = new VBox(7);
        card.getStyleClass().add("component-health-card");
        card.setMaxWidth(Double.MAX_VALUE);

        Label name = new Label(component);
        name.getStyleClass().add("card-header-title");
        Label status = new Label("CHECKING");
        status.getStyleClass().add("component-status");
        javafx.scene.layout.HBox heading = new javafx.scene.layout.HBox(name);
        heading.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
        heading.getChildren().add(status);

        Label summary = new Label(hint);
        summary.setWrapText(true);
        summary.getStyleClass().add("hw-text");
        Button diagnose = new Button("Diagnose");
        diagnose.getStyleClass().add("action-btn");
        diagnose.setOnAction(event -> diagnoseAction.accept(component));
        diagnose.setVisible(false);
        diagnose.setManaged(false);
        Button fix = new Button("Fix it");
        fix.getStyleClass().add("alert-btn-danger");
        fix.setOnAction(event -> fixAction.accept(component));
        fix.setVisible(false);
        fix.setManaged(false);
        javafx.scene.layout.HBox actions = new javafx.scene.layout.HBox(7, diagnose, fix);
        card.getChildren().addAll(heading, summary, actions);
        card.setOnMouseClicked(event -> {
            if (event.getTarget() != diagnose && event.getTarget() != fix) {
                String state = status.getText();
                if ("PROBLEM".equals(state) || "OBSERVED".equals(state)) diagnoseAction.accept(component);
                else inspectAction.accept(component);
            }
        });
        cards.put(component, new Card(card, status, summary, diagnose, fix));
        return card;
    }

    public void update(SystemMetrics metrics, double storageUsed,
                       NetworkTelemetryService.NetworkMetrics network, int networkFailures) {
        if (metrics == null) return;
        set("CPU / Processor", metrics.getCpuLoadPercentage() > 80 ? "PROBLEM" : "GOOD",
                String.format("Load %.1f%% · Clock %.2f GHz", metrics.getCpuLoadPercentage(), metrics.getClockSpeedGhz()));

        double ramUsed = metrics.getTotalMemoryGb() <= 0 ? 0 : metrics.getUsedMemoryGb() / metrics.getTotalMemoryGb() * 100;
        set("RAM / Memory", ramUsed > 85 ? "PROBLEM" : "GOOD",
                String.format("Used %.1f%% · %.2f / %.2f GB", ramUsed, metrics.getUsedMemoryGb(), metrics.getTotalMemoryGb()));

        set("GPU / Graphics", capabilities.graphics().isEmpty() ? "LIMITED" : "GOOD",
            capabilities.graphics().isEmpty() ? "GPU inventory is not exposed by this device"
                : String.join("; ", capabilities.graphics()) + " · health sensors limited");
        set("Storage / SSD", storageUsed > .88 ? "PROBLEM" : "GOOD",
                String.format("Capacity %.1f%% used · SMART health unavailable", storageUsed * 100));

        if (metrics.getBatteryPercent() < 0) {
            set("Battery / Power", "LIMITED", "No battery telemetry exposed; desktop or restricted sensor");
        } else if (metrics.getBatteryHealthPercent() > 0 && metrics.getBatteryHealthPercent() < 80) {
            set("Battery / Power", "PROBLEM", "Capacity health " + metrics.getBatteryHealthPercent() + "% · review recommended");
        } else {
            set("Battery / Power", "GOOD", "Charge " + metrics.getBatteryPercent() + "% · " + (metrics.isBatteryCharging() ? "charging" : "on battery"));
        }

        int[] fanSpeeds = metrics.getFanSpeeds();
        double temp = metrics.getCoreTemperature();
        boolean fanStoppedUnderHeat = temp > 78 && fanSpeeds.length > 0
            && java.util.Arrays.stream(fanSpeeds).allMatch(speed -> speed <= 0);
        set("Fan / Cooling", fanSpeeds.length == 0 ? "LIMITED" : fanStoppedUnderHeat ? "PROBLEM" : "GOOD",
            fanSpeeds.length == 0 ? "Fan RPM is not exposed by this device" :
                String.format("Fan RPM: %s", java.util.Arrays.toString(fanSpeeds)));
        set("Thermal Sensors", temp <= 0 ? "LIMITED" : temp > 78 ? "PROBLEM" : "GOOD",
                temp <= 0 ? "Temperature sensor not exposed" : String.format("CPU temperature %.1f°C", temp));

        if (network == null) {
            set("Network", "CHECKING", "Waiting for the first network probe");
        } else {
            boolean reachable = network.dnsAvailable() && network.internetReachable();
            set("Network", !reachable && networkFailures >= 3 ? "PROBLEM" : reachable ? "GOOD" : "OBSERVED",
                    reachable ? "Reachable · " + network.latencyMs() + " ms" : "DNS or internet reachability failed");
        }
        set("Wi-Fi / Bluetooth", capabilities.network().isEmpty() && capabilities.bluetooth().isEmpty() ? "LIMITED" : "GOOD",
            wirelessSummary());
        set("Display / Input", capabilities.displays().isEmpty() ? "LIMITED" : "GOOD",
            capabilities.displays().isEmpty() ? "Display inventory unavailable; input tests still require permission"
                : String.join("; ", capabilities.displays()) + " · input tests not run");
        set("Audio / Camera", capabilities.audio().isEmpty() ? "LIMITED" : "GOOD",
            capabilities.audio().isEmpty() ? "Audio inventory unavailable; camera/mic tests require permission"
                : String.join("; ", capabilities.audio()) + " · camera/mic tests require permission");
        set("USB / Peripherals", capabilities.usb().isEmpty() ? "LIMITED" : "GOOD",
            capabilities.usb().isEmpty() ? "USB inventory unavailable" : capabilities.usb().size() + " USB device(s) detected · health tests not run");
        set("Motherboard / Firmware", capabilities.board().isBlank() ? "LIMITED" : "GOOD",
            capabilities.board().isBlank() ? "Board inventory unavailable" : capabilities.board() + " · firmware " + capabilities.firmware());
        set("Startup / Crashes", "LIMITED", "Windows Event Viewer and macOS unified-log adapter are not connected yet");
        set("OS / Drivers", "LIMITED", "Service, update and driver-event adapter is not connected yet");
        set("Security", "LIMITED", "Protection-state adapter is not connected; PulseOS will not claim malware from heuristics");
    }

    public void setCapabilities(PlatformCapabilityService.Capabilities capabilities) {
        this.capabilities = capabilities == null ? PlatformCapabilityService.Capabilities.empty() : capabilities;
    }

    private String wirelessSummary() {
        if (capabilities.network().isEmpty() && capabilities.bluetooth().isEmpty()) return "Wireless adapters not detected";
        return "Network: " + capabilities.network().size() + " · Bluetooth: " + capabilities.bluetooth().size()
                + " · signal and disconnect history need a platform adapter";
    }

    private void set(String component, String state, String summary) {
        Card card = cards.get(component);
        if (card == null) return;
        card.status().setText(state);
        card.status().getStyleClass().removeAll("status-good", "status-warning", "status-limited", "status-problem", "status-checking");
        card.status().getStyleClass().add(switch (state) {
            case "GOOD" -> "status-good";
            case "PROBLEM" -> "status-problem";
            case "OBSERVED" -> "status-warning";
            case "CHECKING" -> "status-checking";
            default -> "status-limited";
        });
        card.summary().setText(summary);
        boolean problem = "PROBLEM".equals(state) || "OBSERVED".equals(state);
        boolean fixable = problem && isFixable(component);
        card.diagnose().setVisible(problem);
        card.diagnose().setManaged(problem);
        card.fix().setVisible(fixable);
        card.fix().setManaged(fixable);
        card.container().pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("problem"), problem);
    }

    private boolean isFixable(String component) {
        return component.startsWith("CPU") || component.startsWith("RAM")
                || component.startsWith("Storage") || component.startsWith("Thermal")
                || component.startsWith("Fan");
    }
}