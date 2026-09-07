package com.pulseos.ui;

import java.util.ArrayList;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import com.pulseos.ai.AiFeaturesService;
import com.pulseos.converter.DocumentConverterEngine;
import com.pulseos.healer.BuildArtifactScanner.ArtifactDetails;
import com.pulseos.healer.StorageHealerService;
import com.pulseos.healer.StorageCategoryScanner;
import com.pulseos.telemetry.HardwareTelemetryService;
import com.pulseos.telemetry.SystemMetrics;
import com.pulseos.ui.components.TelemetryChartCard;
import com.pulseos.ui.components.ToastNotification;
import com.pulseos.ui.views.ConverterView;
import com.pulseos.ui.views.SettingsView;
import com.pulseos.ui.views.SmartRouterView;
import com.pulseos.ui.views.StorageHealerView;
import com.pulseos.watcher.DownloadInterceptorService;
import com.pulseos.watcher.DownloadOrganizerService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public class MainDashboardController {
    @FXML private StackPane rootPane, contentArea;
    @FXML private VBox healthView, healthHeroCard, liveSnapshotCard, problemsCard, predictionCard, batteryCard, storageCard, drainerCard, hardwareCard, watcherCard, aboutCard;
    @FXML private Button navHealthBtn, navSmartRouterBtn, navStorageHealerBtn, navConverterBtn, navSettingsBtn;

    @FXML private Label cpuLabel, threadsLabel, ramLabel, clockSpeedLabel, tempLabel, freeStorageLabel;
    @FXML private Label cpuBackendLabel, ramBackendLabel, tempBackendLabel, threadsBackendLabel, clockBackendLabel;
    @FXML private Label cpuChartInfoLabel, ramChartInfoLabel, tempChartInfoLabel;
    @FXML private Label healthDataSourceLabel, problemSummaryLabel, predictionInputsLabel, predictionConfidenceLabel;
    @FXML private Label batteryHealthInfoLabel, batteryBackendLabel, storageArrowLabel, drainerSummaryLabel, watcherBackendLabel;
    @FXML private Label topCpuProcessLabel, topRamProcessLabel, processCountLabel;
    @FXML private Label batteryPercentLabel, batteryStatusLabel, batteryDetailLabel, powerInfoLabel, batteryIconLabel, batteryConditionLabel;
    @FXML private Label processorNameLabel, processorVendorLabel, physicalProcessorLabel, coreCountLabel, baseSpeedLabel, maxSpeedLabel;
    @FXML private Label largestItemTypeLabel, largestFolderLabel, largestFolderSizeLabel, largestFolderNoteLabel;
    @FXML private Button cleanNowBtn, fixIssuesBtn;
    @FXML private Label watcherStatusLabel, interceptedCountLabel, lastFileLabel, lastTimeLabel;
    @FXML private Label footerStatusLabel, footerRamLabel, footerTempLabel;
    @FXML private ProgressBar storageProgressBar;
    @FXML private PieChart storagePieChart;
    @FXML private HBox alertBanner;
    @FXML private Label alertTitleLabel, alertProcessLabel;
    @FXML private TelemetryChartCard cpuChart, ramChart, tempChart;
    @FXML private Label healthScoreLabel, healthGradeLabel, healthReasonLabel;
    @FXML private ProgressBar performanceHealthBar;
    @FXML private Label performanceHealthLabel, hardwareHealthLabel, storageHealthLabel, batteryHealthLabel;
    @FXML private Label issue1Label, issue2Label, issue3Label, predictionLabel, predictionTrendLabel, liveStatusLabel, problemBadgeLabel;
    @FXML private ListView<String> activityLogView;

    private HardwareTelemetryService telemetryService;
    private DownloadInterceptorService watcherService;
    private DownloadOrganizerService organizerService;
    private StorageHealerService healerService;
    private AiFeaturesService aiService;
    private final DocumentConverterEngine documentEngine = new DocumentConverterEngine();
    private StorageHealerView storageHealerView;
    private ConverterView converterView;
    private SmartRouterView smartRouterView;
    private SettingsView settingsView;
    private final StorageCategoryScanner categoryScanner = new StorageCategoryScanner();

    private int tickCounter = 0;
    private int interceptedCount = 0;
    private boolean autoOrganizeEnabled = true;
    private boolean protectActiveProjects = true;
    private final Map<String, Integer> organizedByCategory = new LinkedHashMap<>();
    private SystemMetrics latestMetrics;
    private StorageCategoryScanner.CategoryResult latestStorageResult;
    private final Deque<Double> cpuHistory = new ArrayDeque<>();
    private final Deque<Double> ramHistory = new ArrayDeque<>();
    private final Deque<Double> tempHistory = new ArrayDeque<>();
    private final Deque<Double> storageHistory = new ArrayDeque<>();
    private final Deque<Double> batteryHistory = new ArrayDeque<>();
    private double avgCpu = 0;
    private double avgRam = 0;
    private double avgTemp = 0;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm:ss a");

    @FXML
    public void initialize() {
        healerService = new StorageHealerService();
        aiService = new AiFeaturesService("phi3");
        cpuChart.configure("CPU Load", "CPU", "Usage %", 0, 100);
        ramChart.configure("Memory Pressure", "RAM", "Usage %", 0, 100);
        tempChart.configure("CPU Temperature", "Temperature", "°C", 0, 100);
        buildOtherViews();
        startTelemetry();
        startWatcher();
        initializeDashboardDataState();
        updateStorageInfo();
        populateStaticHardwareInfo();
        wireDashboardInteractions();
        runInitialStorageHealthScan();
    }


    private void initializeDashboardDataState() {
        healthScoreLabel.setText("—/100");
        healthGradeLabel.setText("ANALYZING");
        healthDataSourceLabel.setText("Live score · updates every 1s");
        performanceHealthLabel.setText("—");
        hardwareHealthLabel.setText("—");
        storageHealthLabel.setText("—");
        batteryHealthLabel.setText("—");
        issue1Label.setText("• CPU: starting live check");
        issue2Label.setText("• Memory: starting live check");
        issue3Label.setText("• Storage / temperature: starting live check");
        problemSummaryLabel.setText("Live checks · CPU, memory, temperature and storage");
        predictionLabel.setText("Prediction: waiting for the first live sample…");
        predictionInputsLabel.setText("CPU — · RAM — · Temp — · Storage —");
        predictionConfidenceLabel.setText("Confidence: calculating baseline…");
        predictionTrendLabel.setText("Trend: CPU warming up · RAM warming up · Thermal warming up · Storage warming up");
        problemBadgeLabel.setText("0 active");
        batteryHealthInfoLabel.setText("Health: detecting… · Cycles: detecting…");
        batteryBackendLabel.setText("Charging + health + cycles · live update");
        batteryIconLabel.setText("▮▮▮▮");
        batteryConditionLabel.setText("Battery condition: detecting…");
        storageArrowLabel.setText("→ Free: calculating · → Used: calculating");
        drainerSummaryLabel.setText("Top CPU/RAM apps refresh every 3s");
        watcherBackendLabel.setText("Downloads monitoring · waiting for file events");
        activityLogView.setItems(FXCollections.observableArrayList(
                LocalTime.now().format(TIME_FMT) + "  PulseOS dashboard started",
                LocalTime.now().format(TIME_FMT) + "  Telemetry engine initialized",
                LocalTime.now().format(TIME_FMT) + "  Storage analysis started",
                LocalTime.now().format(TIME_FMT) + "  System Watcher initialized"));
    }

    private void wireDashboardInteractions() {
        healthHeroCard.setOnMouseClicked(e -> openHealthBackend());
        liveSnapshotCard.setOnMouseClicked(e -> openSnapshotBackend());
        problemsCard.setOnMouseClicked(e -> openProblemsBackend());
        predictionCard.setOnMouseClicked(e -> openPredictionBackend());
        batteryCard.setOnMouseClicked(e -> { e.consume(); openBatteryDetail(); });
        storageCard.setOnMouseClicked(e -> { e.consume(); openStorageDetail(); });
        drainerCard.setOnMouseClicked(e -> openDrainerBackend());
        hardwareCard.setOnMouseClicked(e -> { e.consume(); openHardwareBackend(); });
        watcherCard.setOnMouseClicked(e -> openWatcherBackend());
        aboutCard.setOnMouseClicked(e -> openArchitectureBackend());
        fixIssuesBtn.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> e.consume());
        cleanNowBtn.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> e.consume());
        cpuChart.getStyleClass().add("clickable-stat");
        ramChart.getStyleClass().add("clickable-stat");
        tempChart.getStyleClass().add("clickable-stat");
        cpuChart.setOnMouseClicked(e -> { e.consume(); openChartBackend("CPU Load", latestMetrics == null ? 0 : latestMetrics.getCpuLoadPercentage(), "%"); });
        ramChart.setOnMouseClicked(e -> { e.consume(); openChartBackend("Memory Pressure", latestMetrics == null || latestMetrics.getTotalMemoryGb() <= 0 ? 0 : latestMetrics.getUsedMemoryGb() / latestMetrics.getTotalMemoryGb() * 100.0, "%"); });
        tempChart.setOnMouseClicked(e -> { e.consume(); openChartBackend("CPU Temperature", latestMetrics == null ? 0 : latestMetrics.getCoreTemperature(), "°C"); });
    }

    @FXML private void openClockDetail() {
        if (latestMetrics == null) return;
        BackendInfoDialogs.showData(owner(), "CPU Clock — Backend Detail",
                "Live frequency reported by OSHI / operating-system telemetry",
                java.util.List.of(
                        "Current frequency: " + (latestMetrics.getClockSpeedGhz() > 0 ? String.format("%.2f GHz", latestMetrics.getClockSpeedGhz()) : "Unavailable"),
                        "Processor telemetry source: OSHI HardwareAbstractionLayer",
                        "Sampling interval: 1 second",
                        "Use: compare current frequency with CPU load and temperature to detect throttling patterns."));
    }

    @FXML private void openHealthBackend() {
        if (latestMetrics == null) return;
        double ramPct = latestMetrics.getTotalMemoryGb() <= 0 ? 0 : latestMetrics.getUsedMemoryGb() / latestMetrics.getTotalMemoryGb() * 100.0;
        BackendInfoDialogs.showHealth(owner(), latestMetrics, storageProgressBar.getProgress(), healthScoreLabel.getText(),
                performanceHealthLabel.getText(), hardwareHealthLabel.getText(), storageHealthLabel.getText(), batteryHealthLabel.getText());
    }

    private void openSnapshotBackend() {
        if (latestMetrics == null) return;
        BackendInfoDialogs.showData(owner(), "Live Device Snapshot — Backend Data",
                "Raw values currently feeding the dashboard cards",
                java.util.List.of(
                        String.format("CPU: %.1f%%", latestMetrics.getCpuLoadPercentage()),
                        String.format("Memory: %.2f / %.2f GB (%.1f%%)", latestMetrics.getUsedMemoryGb(), latestMetrics.getTotalMemoryGb(), latestMetrics.getTotalMemoryGb() <= 0 ? 0 : latestMetrics.getUsedMemoryGb()/latestMetrics.getTotalMemoryGb()*100),
                        "Temperature: " + (latestMetrics.getCoreTemperature() > 0 ? String.format("%.1f°C", latestMetrics.getCoreTemperature()) : "Not exposed by this device"),
                        String.format("Threads: %,d", latestMetrics.getActiveThreadCount()),
                        String.format("Clock: %.2f GHz", latestMetrics.getClockSpeedGhz()),
                        String.format("Processes sampled: %,d", latestMetrics.getTotalProcessCount()),
                        "Telemetry source: OSHI + ProcessHandle",
                        "Refresh: every 1 second; process list is cached for performance.",
                        "Unknown/unsupported sensors are not replaced with fake values."));
    }

    private void openProblemsBackend() {
        if (latestMetrics == null) return;
        BackendInfoDialogs.showProblems(owner(), latestMetrics, storageProgressBar.getProgress());
    }

    private void openPredictionBackend() {
        if (latestMetrics == null) return;
        double ramPct = latestMetrics.getTotalMemoryGb() <= 0 ? 0 : latestMetrics.getUsedMemoryGb() / latestMetrics.getTotalMemoryGb() * 100.0;
        BackendInfoDialogs.showData(owner(), "Predictive Health — Backend Logic",
                "Explainable rules currently used by the prototype",
                java.util.List.of(
                        "Current prediction: " + predictionLabel.getText(),
                        String.format("CPU input: %.1f%%", latestMetrics.getCpuLoadPercentage()),
                        String.format("RAM input: %.1f%%", ramPct),
                        String.format("Temperature input: %.1f°C", latestMetrics.getCoreTemperature()),
                        String.format("Storage used: %.1f%%", storageProgressBar.getProgress()*100),
                        "Prediction engine: local deterministic heuristic rules + recent trend history",
                        "CPU/RAM use current pressure + sustained trend; thermal risk is only evaluated when a sensor is exposed; storage uses free-space pressure.",
                        "No cloud telemetry is required for this decision."));
    }

    private void openChartBackend(String name, double value, String unit) {
        BackendInfoDialogs.showData(owner(), name + " — Live Backend Data",
                "Telemetry chart is backed by the same live sample shown on the dashboard",
                java.util.List.of(
                        String.format("Current value: %.1f %s", value, unit),
                        "Data source: OSHI HardwareAbstractionLayer",
                        "Update interval: 1 second",
                        "Chart history: last 45 samples",
                        "Purpose: identify spikes, sustained load and recovery after remediation."));
    }

    private void openDrainerBackend() {
        if (latestMetrics == null) return;
        var cpu = latestMetrics.getTopProcesses();
        var ram = latestMetrics.getTopByRam();
        var lines = new java.util.ArrayList<String>();
        lines.add("Processes sampled: " + latestMetrics.getTotalProcessCount());
        for (int i = 0; i < Math.min(5, cpu.size()); i++) {
            var p = cpu.get(i);
            lines.add(String.format("CPU #%d: %s · %.1f%% CPU · %.0f MB RAM · PID %d", i + 1, p.name(), p.cpuPercent(), p.getRamMb(), p.pid()));
        }
        if (!ram.isEmpty()) lines.add(String.format("RAM leader: %s · %.0f MB", ram.get(0).name(), ram.get(0).getRamMb()));
        lines.add("Backend: OSHI process snapshots · refreshed every 3 seconds");
        BackendInfoDialogs.showData(owner(), "Top Resource Drainers — Live Data", "Actual processes ranked from the latest process snapshot.", lines);
    }

    private void openHardwareBackend() {
        BackendInfoDialogs.showHardware(owner());
    }

    private void openWatcherBackend() {
        BackendInfoDialogs.showData(owner(), "System Watcher — Backend Status",
                "Download monitoring and organization pipeline",
                java.util.List.of(
                        "Watcher folder: " + Paths.get(System.getProperty("user.home"), "Downloads"),
                        "WatchService: active",
                        "Auto-organize: " + (autoOrganizeEnabled ? "enabled" : "disabled"),
                        "Protect active projects: " + (protectActiveProjects ? "enabled" : "disabled"),
                        "Files organized this session: " + interceptedCount,
                        "File policy: incomplete/temp downloads are left unchanged",
                        "File info tracked: name + size + destination/category + organizer decision",
                        "Security note: the watcher does not scan files for malware",
                        "Backend: Java NIO WatchService + DownloadOrganizerService",
                        "PDF AI rename: optional local Ollama pipeline."));
    }

    private void openArchitectureBackend() {
        BackendInfoDialogs.showData(owner(), "PulseOS — Backend Architecture",
                "How the dashboard data is produced",
                java.util.List.of(
                        "UI: JavaFX 21 + FXML",
                        "Telemetry: OSHI 7.6.0",
                        "Process actions: Java ProcessHandle",
                        "Storage: BuildArtifactScanner + StorageCategoryScanner",
                        "Healing: reversible quarantine before permanent deletion",
                        "Watcher: Java NIO WatchService",
                        "AI: optional local Ollama integration",
                        "Design flow: MONITOR → UNDERSTAND → PREDICT → HEAL",
                        "Privacy model: local-first; no required cloud telemetry."));
    }

    @FXML private void showHealthView() { swapContent(healthView, navHealthBtn); }
    @FXML private void showSmartRouterView() { swapContent(smartRouterView, navSmartRouterBtn); }
    @FXML private void showStorageHealerView() { swapContent(storageHealerView, navStorageHealerBtn); storageHealerView.ensureLoaded(); }
    @FXML private void showConverterView() { swapContent(converterView, navConverterBtn); }
    @FXML private void showSettingsView() { swapContent(settingsView, navSettingsBtn); }

    private void swapContent(Node view, Button activeBtn) {
        contentArea.getChildren().setAll(view);
        for (Button b : new Button[]{navHealthBtn, navSmartRouterBtn, navStorageHealerBtn, navConverterBtn, navSettingsBtn}) {
            b.getStyleClass().remove("nav-tab-active");
        }
        activeBtn.getStyleClass().add("nav-tab-active");
    }

    private void buildOtherViews() {
        storageHealerView = new StorageHealerView(healerService, aiService, this::logActivity);
        converterView = new ConverterView(aiService, this::logActivity);
        smartRouterView = new SmartRouterView();
        settingsView = new SettingsView(autoOrganizeEnabled, enabled -> autoOrganizeEnabled = enabled,
                protectActiveProjects, enabled -> protectActiveProjects = enabled);
    }

    private void logActivity(String message) {
        Platform.runLater(() -> {
            activityLogView.getItems().add(0, LocalTime.now().format(TIME_FMT) + "  " + message);
            if (activityLogView.getItems().size() > 80) activityLogView.getItems().remove(80);
        });
    }

    private void startTelemetry() {
        telemetryService = new HardwareTelemetryService();
        telemetryService.setProcessListener(processes -> Platform.runLater(() -> smartRouterView.updateProcesses(processes, latestMetrics == null ? processes.size() : latestMetrics.getTotalProcessCount())));
        telemetryService.startMonitoring(metrics -> Platform.runLater(() -> updateTelemetryUI(metrics)), 1000);
    }

    private void updateTelemetryUI(SystemMetrics metrics) {
        latestMetrics = metrics;
        double ramPct = metrics.getTotalMemoryGb() <= 0 ? 0 : (metrics.getUsedMemoryGb() / metrics.getTotalMemoryGb()) * 100.0;
        double temp = Math.max(0, metrics.getCoreTemperature());
        tickCounter++;
        avgCpu = rollingAverage(avgCpu, metrics.getCpuLoadPercentage(), tickCounter);
        avgRam = rollingAverage(avgRam, ramPct, tickCounter);
        if (temp > 0) avgTemp = rollingAverage(avgTemp, temp, tickCounter);

        pushHistory(cpuHistory, metrics.getCpuLoadPercentage(), 30);
        pushHistory(ramHistory, ramPct, 30);
        if (temp > 0) pushHistory(tempHistory, temp, 30);
        pushHistory(storageHistory, storageProgressBar.getProgress() * 100.0, 30);
        if (metrics.getBatteryPercent() >= 0) pushHistory(batteryHistory, metrics.getBatteryPercent(), 30);

        cpuLabel.setText(String.format("%.0f%%", metrics.getCpuLoadPercentage()));
        threadsLabel.setText(String.format("%,d", metrics.getActiveThreadCount()));
        ramLabel.setText(String.format("%.1f GB", metrics.getUsedMemoryGb()));
        tempLabel.setText(temp > 0 ? String.format("%.0f°C", temp) : "N/A");
        clockSpeedLabel.setText(metrics.getClockSpeedGhz() > 0 ? String.format("%.2f GHz", metrics.getClockSpeedGhz()) : "N/A");

        var topCpu = metrics.getTopProcesses().isEmpty() ? null : metrics.getTopProcesses().get(0);
        var topRam = metrics.getTopByRam().isEmpty() ? null : metrics.getTopByRam().get(0);
        cpuBackendLabel.setText(topCpu == null
                ? "Top app/service: warming up…"
                : String.format("Top app/service: %s · %.1f%%", topCpu.name(), topCpu.cpuPercent()));
        ramBackendLabel.setText(topRam == null
                ? "Top app/service: warming up…"
                : String.format("Top app/service: %s · %.0f MB", topRam.name(), topRam.getRamMb()));
        tempBackendLabel.setText(temp > 0
                ? (topCpu == null ? "Sensor active · contributor warming up…"
                    : String.format("Sensor active · CPU contributor → %s (%.1f%%)", topCpu.name(), topCpu.cpuPercent()))
                : "Sensor not exposed by this device · not used as an active fault");
        threadsBackendLabel.setText(String.format("Processes: %,d · live thread count", metrics.getTotalProcessCount()));
        clockBackendLabel.setText(metrics.getClockSpeedGhz() > 0
                ? "Current CPU frequency"
                : "Frequency unavailable");

        cpuChart.addTelemetryPoint(String.valueOf(tickCounter), metrics.getCpuLoadPercentage());
        ramChart.addTelemetryPoint(String.valueOf(tickCounter), ramPct);
        if (temp > 0) tempChart.addTelemetryPoint(String.valueOf(tickCounter), temp);
        cpuChartInfoLabel.setText(topCpu == null
                ? "Top app/service: warming up…"
                : String.format("Top app/service → %s · %.1f%%", topCpu.name(), topCpu.cpuPercent()));
        ramChartInfoLabel.setText(topRam == null
                ? "Top app/service: warming up…"
                : String.format("Top app/service → %s · %.0f MB", topRam.name(), topRam.getRamMb()));
        tempChartInfoLabel.setText(temp > 0
                ? String.format("Live sensor · %.0f°C", temp)
                : "Thermal sensor not exposed · chart paused safely");

        if (metrics.getBatteryPercent() >= 0) {
            int pctValue = metrics.getBatteryPercent();
            String pct = pctValue + "%";
            batteryPercentLabel.setText(pct);
            batteryStatusLabel.setText(metrics.isBatteryCharging() ? "CHARGING" : "ON BATTERY");
            batteryDetailLabel.setText(metrics.isBatteryCharging() ? "AC adapter connected" : "Currently discharging");
            powerInfoLabel.setText(metrics.isBatteryCharging() ? "Power source: adapter" : "Power source: battery");
            String health = metrics.getBatteryHealthPercent() > 0 ? metrics.getBatteryHealthPercent() + "%" : "N/A";
            String cycles = metrics.getBatteryCycleCount() >= 0 ? String.valueOf(metrics.getBatteryCycleCount()) : "N/A";
            batteryHealthInfoLabel.setText("Health: " + health + " · Cycles: " + cycles);
            batteryConditionLabel.setText(metrics.getBatteryHealthPercent() > 0
                    ? (metrics.getBatteryHealthPercent() >= 90 ? "Battery condition: Excellent" : metrics.getBatteryHealthPercent() >= 80 ? "Battery condition: Good" : "Battery condition: Review")
                    : "Battery condition: Health telemetry unavailable");
            batteryIconLabel.setText(batteryGlyph(pctValue));
            batteryBackendLabel.setText("Power source telemetry · capacity + charging + health/cycles");
        } else {
            batteryPercentLabel.setText("N/A");
            batteryStatusLabel.setText("NO BATTERY DETECTED");
            batteryDetailLabel.setText("Desktop or unavailable battery sensor");
            powerInfoLabel.setText("This device does not expose battery telemetry");
            batteryHealthInfoLabel.setText("Health: N/A · Cycles: N/A");
            batteryConditionLabel.setText("Battery condition: Not measurable");
            batteryIconLabel.setText("▯▯▯▯");
            batteryBackendLabel.setText("Battery telemetry not exposed by this device");
        }

        footerRamLabel.setText(String.format("RAM: %.1f / %.1f GB", metrics.getUsedMemoryGb(), metrics.getTotalMemoryGb()));
        footerTempLabel.setText(temp > 0 ? String.format("CPU Temp: %.0f°C", temp) : "CPU Temp: N/A");
        smartRouterView.update(metrics);
        updateDrainerCard(metrics);
        evaluateResourceAlert(metrics);
        updateHealthIntelligence(metrics, ramPct, temp);
        liveStatusLabel.setText("● LIVE  ·  Last sample " + LocalTime.now().format(TIME_FMT));
    }

    private double rollingAverage(double current, double value, int n) {
        return current + (value - current) / Math.min(n, 60);
    }

    private void updateHealthIntelligence(SystemMetrics m, double ramPct, double temp) {
        try {
            double cpuAvg = average(cpuHistory, m.getCpuLoadPercentage());
            double ramAvg = average(ramHistory, ramPct);
            double tempAvg = tempHistory.isEmpty() ? 0 : average(tempHistory, temp);
            double usedRatio = storageProgressBar.getProgress();

            // Health is a sustained-condition score: short spikes should not dominate it,
            // but persistent CPU/RAM/thermal/storage pressure will reduce it.
            int performance = clamp((int) Math.round(100
                    - Math.max(0, cpuAvg - 55) * 0.55
                    - Math.max(0, ramAvg - 65) * 0.60));

            int hardware = tempAvg <= 0 ? 100
                    : clamp((int) Math.round(100 - Math.max(0, tempAvg - 60) * 1.30));

            int storage = usedRatio <= 0.70 ? 100
                    : clamp((int) Math.round(100 - (usedRatio - 0.70) * 170));

            int battery = -1;
            int reportedBatteryHealth = m.getBatteryHealthPercent();

            if (reportedBatteryHealth >= 20 && reportedBatteryHealth <= 100) {
                // Battery component is the battery-condition signal, not the charge level.
                // Keep a healthy 92% battery near 92% instead of applying a second penalty.
                battery = reportedBatteryHealth;
            } else if (m.getBatteryPercent() >= 0) {
                // If macOS/OSHI cannot provide a trustworthy health ratio, use the
                // actual current charge as a bounded fallback rather than showing 1%.
                battery = m.getBatteryPercent();
            }

            int score = clamp((int) Math.round(performance * .40 + hardware * .25 + storage * .20 + battery * .15));
            healthScoreLabel.setText(score + "/100");
            healthGradeLabel.setText(score >= 90 ? "EXCELLENT" : score >= 75 ? "GOOD" : score >= 55 ? "NEEDS ATTENTION" : "CRITICAL");

            boolean highCpu = m.getCpuLoadPercentage() > 80;
            boolean highRam = ramPct > 85;
            boolean highTemp = temp > 0 && temp > 78;
            boolean storagePressure = usedRatio > .88;
            int issueCount = (highCpu ? 1 : 0) + (highRam ? 1 : 0) + (highTemp ? 1 : 0) + (storagePressure ? 1 : 0);

            updateActiveProblems(m, ramPct, temp, usedRatio, highCpu, highRam, highTemp, storagePressure);

            if (issueCount == 0) {
                healthReasonLabel.setText("Your device is operating within the configured healthy range. PulseOS will lower this score only when pressure persists or a health signal deteriorates.");
                fixIssuesBtn.setText("✓ ALL CLEAR");
                fixIssuesBtn.setDisable(true);
            } else {
                healthReasonLabel.setText("PulseOS found " + issueCount + " active condition" + (issueCount > 1 ? "s" : "") + ". Review or safely remediate the affected area.");
                fixIssuesBtn.setText(storagePressure ? "FIX ISSUES SAFELY" : "REVIEW & FIX");
                fixIssuesBtn.setDisable(false);
            }

            setHealth(performanceHealthBar, performanceHealthLabel, performance);
            hardwareHealthLabel.setText(hardware + "%");
            storageHealthLabel.setText(storage + "%");
            batteryHealthLabel.setText(battery >= 0 ? battery + "%" : "N/A");

            predictionLabel.setText(buildPrediction(m, ramPct, temp));
            String tempInput = temp > 0 ? String.format("%.0f°C", temp) : "not measurable";
            predictionInputsLabel.setText(String.format("Current: CPU %.0f%% · RAM %.0f%% · Temp %s · Storage %.0f%% used",
                    m.getCpuLoadPercentage(), ramPct, tempInput, usedRatio * 100));
            predictionTrendLabel.setText(String.format("Trend: CPU %s · RAM %s · Thermal %s · Storage %s",
                    trend(cpuHistory), trend(ramHistory), tempHistory.isEmpty() ? "not measurable" : trend(tempHistory), trend(storageHistory)));
            predictionConfidenceLabel.setText(String.format("Confidence: %d%% · baseline samples: %d", Math.min(95, 70 + Math.min(25, tickCounter / 4)), Math.min(tickCounter, 60)));
            problemSummaryLabel.setText(issueCount == 0
                    ? "No active threshold breaches · monitoring for sustained changes"
                    : String.format("%d active condition(s) · based on current thresholds", issueCount));
            healthDataSourceLabel.setText("Score uses sustained CPU/RAM + thermal + storage + battery health");
            footerStatusLabel.setText(issueCount == 0 ? "● System Healthy" : "● " + issueCount + " issue" + (issueCount > 1 ? "s" : "") + " detected");
        } catch (Exception ex) {
            healthGradeLabel.setText("LIVE");
            healthDataSourceLabel.setText("Telemetry partially available · optional sensor not exposed");
        }
    }

    private void updateActiveProblems(SystemMetrics m, double ramPct, double temp, double usedRatio,
                                      boolean highCpu, boolean highRam, boolean highTemp, boolean storagePressure) {
        var labels = new Label[]{issue1Label, issue2Label, issue3Label};
        for (Label l : labels) { l.setVisible(false); l.setManaged(false); l.setText(""); }
        var messages = new ArrayList<String>();
        if (highCpu) {
            String leader = m.getTopProcesses().isEmpty() ? "top process unavailable" : m.getTopProcesses().get(0).name();
            messages.add("⚠ High CPU · " + String.format("%.0f%%", m.getCpuLoadPercentage()) + " · " + leader);
        }
        if (highRam) messages.add("⚠ High memory pressure · " + String.format("%.0f%% RAM used", ramPct));
        if (highTemp) messages.add("⚠ High temperature · " + String.format("%.0f°C", temp));
        if (storagePressure) messages.add("⚠ Storage nearly full · safe cleanup available");

        problemBadgeLabel.setText(messages.isEmpty() ? "0 active" : messages.size() + " active");
        if (messages.isEmpty()) {
            issue1Label.setText("✓ No active problems detected");
            issue1Label.setVisible(true); issue1Label.setManaged(true);
        } else {
            for (int i = 0; i < messages.size() && i < labels.length; i++) {
                labels[i].setText(messages.get(i));
                labels[i].setVisible(true); labels[i].setManaged(true);
            }
        }
    }

    private void pushHistory(Deque<Double> history, double value, int max) {
        history.addLast(value);
        while (history.size() > max) history.removeFirst();
    }

    private double average(Deque<Double> history, double fallback) {
        if (history.isEmpty()) return fallback;
        double sum = 0; for (double v : history) sum += v;
        return sum / history.size();
    }

    private String trend(Deque<Double> history) {
        if (history.size() < 6) return "warming up";
        var a = history.toArray(new Double[0]);
        int mid = a.length / 2;
        double first = 0, second = 0;
        for (int i = 0; i < mid; i++) first += a[i];
        for (int i = mid; i < a.length; i++) second += a[i];
        first /= mid; second /= (a.length - mid);
        double delta = second - first;
        if (Math.abs(delta) < 2.0) return "stable";
        return delta > 0 ? "rising" : "falling";
    }

    private String batteryGlyph(int pct) {
        if (pct >= 75) return "▮▮▮▮";
        if (pct >= 50) return "▮▮▮▫";
        if (pct >= 25) return "▮▮▫▫";
        return "▮▫▫▫";
    }

    private String buildPrediction(SystemMetrics m, double ramPct, double temp) {
        String cpuTrend = trend(cpuHistory);
        String ramTrend = trend(ramHistory);
        String storageTrend = trend(storageHistory);

        if (temp > 78) return "Thermal outlook: elevated — sustained heat may reduce performance if the current pattern continues.";
        if (ramPct > 85 || ("rising".equals(ramTrend) && ramPct > 75)) return "Memory outlook: elevated — pressure is rising and may cause slowdowns if this workload continues.";
        if (storageProgressBar.getProgress() > .88) return "Storage outlook: high pressure — free-space margin is low; cleanup should be reviewed soon.";
        if (m.getCpuLoadPercentage() > 80 || ("rising".equals(cpuTrend) && m.getCpuLoadPercentage() > 70)) return "Performance outlook: elevated — CPU load is trending upward and may persist if the current workload continues.";
        return "Outlook: stable — no immediate degradation pattern is visible from the current telemetry trend.";
    }

    private void setHealth(ProgressBar bar, Label label, int value) {
        bar.setProgress(value / 100.0);
        label.setText(value + "%");
    }

    private int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    private void populateStaticHardwareInfo() {
        try {
            oshi.SystemInfo si = new oshi.SystemInfo();
            var processor = si.getHardware().getProcessor();
            var id = processor.getProcessorIdentifier();
            processorNameLabel.setText("Processor: " + id.getName());
            processorVendorLabel.setText("Vendor: " + id.getVendor());
            physicalProcessorLabel.setText("Physical processors: " + processor.getPhysicalProcessorCount());
            coreCountLabel.setText("Logical processors: " + processor.getLogicalProcessorCount());
            baseSpeedLabel.setText(String.format("Base / vendor: %.2f GHz", id.getVendorFreq() / 1_000_000_000.0));
            maxSpeedLabel.setText(String.format("Max: %.2f GHz · Architecture: %s", processor.getMaxFreq() / 1_000_000_000.0, System.getProperty("os.arch", "unknown")));
        } catch (Exception e) {
            processorNameLabel.setText("Processor: information unavailable");
            processorVendorLabel.setText("Vendor: unavailable");
            physicalProcessorLabel.setText("Physical processors: unavailable");
            coreCountLabel.setText("Logical processors: unavailable");
            baseSpeedLabel.setText("Base / vendor: unavailable");
            maxSpeedLabel.setText("Max: unavailable");
        }
    }

    private void evaluateResourceAlert(SystemMetrics metrics) {
        boolean shouldAlert = metrics.getCpuLoadPercentage() >= 85 && !metrics.getTopProcesses().isEmpty();
        alertBanner.setVisible(shouldAlert);
        alertBanner.setManaged(shouldAlert);
        if (shouldAlert) {
            var top = metrics.getTopProcesses().get(0);
            alertTitleLabel.setText("HIGH RESOURCE ACTIVITY DETECTED");
            alertProcessLabel.setText(String.format("Top process: %s (PID %d) — %.1f%% CPU", top.name(), top.pid(), top.cpuPercent()));
        }
    }

    private void updateDrainerCard(SystemMetrics metrics) {
        var topCpu = metrics.getTopProcesses().isEmpty() ? null : metrics.getTopProcesses().get(0);
        var topRam = metrics.getTopByRam().isEmpty() ? null : metrics.getTopByRam().get(0);

        topCpuProcessLabel.setText(topCpu == null
                ? "CPU leader: warming up…"
                : String.format("CPU leader: %s — %.1f%%", topCpu.name(), topCpu.cpuPercent()));
        topRamProcessLabel.setText(topRam == null
                ? "Memory leader: warming up…"
                : String.format("Memory leader: %s — %.0f MB", topRam.name(), topRam.getRamMb()));
        processCountLabel.setText("Processes: " + metrics.getTotalProcessCount());
        drainerSummaryLabel.setText(topCpu == null && topRam == null
                ? "Live process snapshot warming up…"
                : "Top CPU/RAM processes · live snapshot");
    }

    private void updateStorageInfo() {
        java.io.File home = new java.io.File(System.getProperty("user.home"));
        long free = home.getFreeSpace() / (1024 * 1024 * 1024);
        long total = home.getTotalSpace() / (1024 * 1024 * 1024);
        if (total > 0) {
            double used = (double)(total - free) / total;
            storageProgressBar.setProgress(used);
            freeStorageLabel.setText(free + " GB free");
            var initial = FXCollections.<PieChart.Data>observableArrayList();
            initial.add(new PieChart.Data("Used Space", Math.max(1, total - free)));
            initial.add(new PieChart.Data("Free Space", Math.max(1, free)));
            storagePieChart.setData(initial);
            storageArrowLabel.setText(String.format("→ Used: %d GB (%.0f%%) · → Free: %d GB (%.0f%%)", total - free, used * 100, free, (1 - used) * 100));
        }
    }

    private void runInitialStorageHealthScan() {
        Path home = Paths.get(System.getProperty("user.home"));
        // Do not recursively scan the entire user home on startup. macOS home folders
        // can contain Library, caches and SDKs with hundreds of thousands of files.
        // PulseOS only needs the user-facing locations for a fast dashboard prototype.
        healerService.analyzeCommonLocations(home).thenAccept(artifacts ->
                Platform.runLater(() -> applyRealStorageBreakdown(home, artifacts)));
    }

    @FXML
    private void handleFixIssues() {
        if (latestMetrics == null) return;
        double ramPct = latestMetrics.getTotalMemoryGb() <= 0 ? 0
                : latestMetrics.getUsedMemoryGb() / latestMetrics.getTotalMemoryGb() * 100.0;
        double usedRatio = storageProgressBar.getProgress();

        // The only automatic cleanup is the safe, reversible quarantine path.
        if (usedRatio > .88) {
            healerService.analyzeCommonLocations(Paths.get(System.getProperty("user.home")))
                    .thenAccept(artifacts -> {
                        var safe = artifacts.stream().filter(a -> !a.isActiveProject()).toList();
                        if (safe.isEmpty()) {
                            Platform.runLater(() -> showStorageHealerView());
                            return;
                        }
                        healerService.quarantineAndClean(safe).thenAccept(freed -> Platform.runLater(() -> {
                            double mb = freed / (1024.0 * 1024.0);
                            logActivity("✅ Safe Fix moved " + String.format("%.1f MB", mb) + " to quarantine");
                            ToastNotification.show(rootPane, String.format("Safe Fix: %.1f MB reclaimed", mb), ToastNotification.Type.INFO);
                            updateStorageInfo();
                            runInitialStorageHealthScan();
                        }));
                    });
            return;
        }

        // Performance issues are review-first: Smart Router shows the exact process
        // before any process termination is requested.
        if (latestMetrics.getCpuLoadPercentage() > 80 || ramPct > 85 || latestMetrics.getCoreTemperature() > 78) {
            showSmartRouterView();
            logActivity("Opened Smart Router for guided performance fix");
        } else {
            showStorageHealerView();
        }
    }

    private void applyRealStorageBreakdown(Path home, java.util.List<ArtifactDetails> artifacts) {
        var result = categoryScanner.scan(home, artifacts);
        latestStorageResult = result;
        var pieData = FXCollections.<PieChart.Data>observableArrayList();
        double totalGb = Math.max(1.0, Paths.get(System.getProperty("user.home")).toFile().getTotalSpace() / (1024.0 * 1024.0 * 1024.0));
        double freeGb = Math.max(0.0, Paths.get(System.getProperty("user.home")).toFile().getFreeSpace() / (1024.0 * 1024.0 * 1024.0));
        pieData.add(new PieChart.Data("Free Space", Math.max(0.1, freeGb)));
        pieData.add(new PieChart.Data("Other Used Space", Math.max(0.1, totalGb - freeGb)));
        storagePieChart.setData(pieData);
        storagePieChart.setStartAngle(90);
        StringBuilder arrows = new StringBuilder();
        result.categoryMb().entrySet().stream().limit(5).forEach(e -> {
            if (arrows.length() > 0) arrows.append("   ");
            arrows.append("→ ").append(e.getKey()).append(": ").append(formatStorage(e.getValue()));
        });
        storageArrowLabel.setText(arrows.length() == 0 ? "→ Storage categories unavailable" : arrows.toString());
        var biggest = result.biggestItem();
        if (biggest != null) {
            largestItemTypeLabel.setText(biggest.isFolder() ? "Largest Folder" : "Largest File");
            largestFolderLabel.setText((biggest.isFolder() ? "Folder: " : "File: ") + biggest.path());
            largestFolderSizeLabel.setText(String.format("Size: %.1f MB", biggest.sizeMb()));
            largestFolderNoteLabel.setText(biggest.daysUnused() >= 60 ? "Not touched in " + biggest.daysUnused() + " days — review before cleaning." : "Recently used but large — review before cleaning.");
            cleanNowBtn.setVisible(true); cleanNowBtn.setManaged(true);
        } else {
            largestFolderLabel.setText("No large or unused items found."); largestFolderSizeLabel.setText(""); largestFolderNoteLabel.setText(""); cleanNowBtn.setVisible(false); cleanNowBtn.setManaged(false);
        }
        if (latestMetrics != null) updateHealthIntelligence(latestMetrics,
                latestMetrics.getTotalMemoryGb() <= 0 ? 0 : latestMetrics.getUsedMemoryGb() / latestMetrics.getTotalMemoryGb() * 100,
                latestMetrics.getCoreTemperature());
    }

    private String formatStorage(double mb) {
        if (mb >= 1024) return String.format("%.1f GB", mb / 1024.0);
        return String.format("%.0f MB", mb);
    }

    private void startWatcher() {
        Path downloadsFolder = Paths.get(System.getProperty("user.home"), "Downloads");
        organizerService = new DownloadOrganizerService(downloadsFolder);
        watcherService = new DownloadInterceptorService(downloadsFolder);
        watcherStatusLabel.setText("● WatchService active");
        interceptedCountLabel.setText("Downloads organized today: " + interceptedCount + " files");
        lastFileLabel.setText("Last file: — (waiting for event)");
        lastTimeLabel.setText("Last event: " + LocalTime.now().format(TIME_FMT) + " · watcher started");
        watcherBackendLabel.setText("Watching: " + downloadsFolder + " · event-driven");
        watcherService.startIntercepting((filePath, extension) -> {
            if (!autoOrganizeEnabled) { Platform.runLater(() -> logActivity("Detected (auto-organize off): " + filePath.getFileName())); return; }
            var result = organizerService.organize(filePath, extension);
            if (result.moved() && "pdf".equalsIgnoreCase(extension)) tryAiAutoRename(result.newLocation());
            Platform.runLater(() -> {
                interceptedCount++;
                if (result.moved()) {
                    String category = result.newLocation().getParent().getFileName().toString();
                    organizedByCategory.merge(category, 1, Integer::sum);
                }
                interceptedCountLabel.setText("Downloads organized today: " + interceptedCount + " files");
                lastFileLabel.setText("Last file: " + filePath.getFileName());
                lastTimeLabel.setText("Last event: " + LocalTime.now().format(TIME_FMT));
                String sizeText = "size unavailable";
                try { if (java.nio.file.Files.exists(result.newLocation())) sizeText = formatStorage(java.nio.file.Files.size(result.newLocation()) / (1024.0 * 1024.0)); } catch (Exception ignored) { }
                String policy = result.moved() ? "Policy: safe to organize" : "Policy: left unchanged";
                watcherBackendLabel.setText("Event details · " + sizeText + " · " + policy + " · malware safety not scanned");
                String msg = result.moved() ? "Moved " + filePath.getFileName() + " → " + result.reason() : result.reason();
                logActivity("Watcher: " + msg);
                ToastNotification.show(rootPane, "Watcher: " + msg, ToastNotification.Type.INFO);
            });
        });
    }

    private void tryAiAutoRename(Path pdfPath) {
        try {
            String text = documentEngine.extractTextFromPdf(pdfPath);
            aiService.suggestDownloadRename(text, pdfPath.getFileName().toString()).thenAccept(suggestion -> {
                if (!suggestion.aiGenerated()) return;
                try {
                    Path parent = pdfPath.getParent();
                    Path targetDir = suggestion.suggestedFolder() != null ? parent.getParent().resolve(suggestion.suggestedFolder().replace('\\', '/')) : parent;
                    java.nio.file.Files.createDirectories(targetDir);
                    Path renamed = targetDir.resolve(suggestion.suggestedName() + ".pdf");
                    java.nio.file.Files.move(pdfPath, renamed, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logActivity("AI renamed: " + pdfPath.getFileName() + " → " + renamed.getFileName());
                } catch (Exception e) { logActivity("AI rename suggested but move failed: " + e.getMessage()); }
            });
        } catch (Exception ignored) { }
    }

    @FXML private void handleRunScan() { showStorageHealerView(); }
    private Window owner() { return rootPane.getScene() != null ? rootPane.getScene().getWindow() : null; }
    @FXML private void openCpuDetail(MouseEvent e) { e.consume(); openCpuDetailInternal(); }
    private void openCpuDetailInternal() {
        if (latestMetrics != null) DetailDialogs.showCpuDetail(owner(), latestMetrics);
        else BackendInfoDialogs.showData(owner(), "CPU — Backend Data", "Telemetry is still starting.", java.util.List.of("Source: OSHI HardwareAbstractionLayer", "Sampling: 1 second"));
    }
    @FXML private void openThreadsDetail(MouseEvent e) { e.consume(); openThreadsDetailInternal(); }
    private void openThreadsDetailInternal() {
        if (latestMetrics != null) DetailDialogs.showThreadsDetail(owner(), latestMetrics);
        else BackendInfoDialogs.showData(owner(), "Threads — Backend Data", "Telemetry is still starting.", java.util.List.of("Source: OSHI process/thread telemetry"));
    }
    @FXML private void openTempDetail(MouseEvent e) { e.consume(); openTempDetailInternal(); }
    private void openTempDetailInternal() {
        if (latestMetrics != null) DetailDialogs.showTempDetail(owner(), latestMetrics, this::killProcess, () -> aiService.explainThermalAnomaly(latestMetrics));
        else BackendInfoDialogs.showData(owner(), "Temperature — Backend Data", "Thermal telemetry is still starting.", java.util.List.of("Source: OSHI sensors", "Some Macs may not expose CPU temperature to user-space."));
    }
    @FXML private void openRamDetail(MouseEvent e) { e.consume(); openRamDetailInternal(); }
    private void openRamDetailInternal() {
        if (latestMetrics != null) DetailDialogs.showRamDetail(owner(), latestMetrics, this::killProcess);
        else BackendInfoDialogs.showData(owner(), "RAM — Backend Data", "Telemetry is still starting.", java.util.List.of("Source: OSHI GlobalMemory + process telemetry"));
    }
    @FXML private void openBatteryDetail() {
        if (latestMetrics != null) DetailDialogs.showBatteryDetail(owner(), latestMetrics);
        else BackendInfoDialogs.showData(owner(), "Battery — Backend Data", "Battery telemetry is still starting.", java.util.List.of("Source: OSHI PowerSources"));
    }
    @FXML private void openStorageDetail() {
        if (latestStorageResult != null) DetailDialogs.showStorageDetail(owner(), latestStorageResult, this::showStorageHealerView);
        else BackendInfoDialogs.showData(owner(), "Storage — Backend Data", "Storage intelligence scan is still running.", java.util.List.of("Source: StorageCategoryScanner + BuildArtifactScanner", "Fast scan roots: Downloads, Desktop, Documents, Pictures, Projects, Developer"));
    }

    private void killProcess(long pid) {
        ProcessHandle.of(pid).ifPresentOrElse(handle -> logActivity(handle.destroy() ? "Requested termination of PID " + pid : "Could not terminate PID " + pid), () -> logActivity("PID " + pid + " no longer exists"));
    }

    public void stopServices() {
        if (telemetryService != null) telemetryService.stopMonitoring();
        if (watcherService != null) watcherService.stopIntercepting();
        if (healerService != null) { healerService.purgeQuarantine(7); healerService.shutdown(); }
    }
}
