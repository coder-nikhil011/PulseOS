package com.pulseos.ui;

import com.pulseos.data.DeviceService;
import com.pulseos.data.MockDeviceService;
import com.pulseos.ui.components.DeviceImage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class PdfReferenceDashboardController {
    @javafx.fxml.FXML private StackPane root;
    private final DeviceService deviceService = new MockDeviceService();
    private final BorderPane shell = new BorderPane();
    private final HBox navigation = new HBox(4);
    private final String[] pages = {"Overview", "Hardware Health", "Smart Router", "Storage Healer", "Converter", "Settings"};

    public void initialize() {
        root.getChildren().add(shell);
        root.getStyleClass().add("reference-root");
            root.setFocusTraversable(true);
            root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode().isDigitKey()) {
                    int index = event.getCode().getCode() - KeyCode.DIGIT1.getCode();
                    if (index >= 0 && index < pages.length) {
                        show(pages[index]);
                        event.consume();
                    }
                }
            });
        shell.setTop(header());
        show("Overview");
        root.requestFocus();
    }

    private Node header() {
        VBox header = new VBox();
        HBox brand = new HBox(12);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand-row");
        Label logo = new Label("P"); logo.getStyleClass().add("brand-logo");
        Label name = new Label("PulseOS"); name.getStyleClass().add("brand-name");
        Label descriptor = new Label("Intelligent Desktop Utility & Hardware Health"); descriptor.getStyleClass().add("brand-descriptor");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label live = new Label("● LIVE  ·  Last sample 12:52:19 pm"); live.getStyleClass().add("live-pill");
        Button refresh = topButton("⟳ Refresh");
        brand.getChildren().addAll(logo, name, descriptor, spacer, live, refresh, topButton("−"), topButton("×"));
        navigation.getStyleClass().add("top-nav");
        navigation.setPadding(new Insets(0, 14, 0, 14));
        header.getChildren().addAll(brand, navigation);
        return header;
    }

    private Button topButton(String value) { Button button = new Button(value); button.getStyleClass().add("top-button"); return button; }

    private void show(String page) {
        navigation.getChildren().clear();
        for (String item : pages) {
            Button button = new Button(item);
            button.getStyleClass().add("top-nav-button");
            if (item.equals(page)) button.getStyleClass().add("top-nav-active");
            button.setOnAction(event -> show(item));
            navigation.getChildren().add(button);
        }
        Node pageContent = buildPage(page);
        VBox body = new VBox(6);
        if (page.equals("Hardware Health") || page.equals("Storage Healer")) {
            body.getChildren().add(titleBlock(page, subtitle(page)));
        }
        body.getChildren().add(pageContent);
        body.getStyleClass().add("page-body");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(page.equals("Overview")
            ? ScrollPane.ScrollBarPolicy.NEVER
            : ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("reference-scroll");
        shell.setCenter(scroll);
    }

    private VBox titleBlock(String title, String subtitle) {
        VBox box = new VBox(2);
        Label heading = new Label(title); heading.getStyleClass().add("page-title");
        Label detail = new Label(subtitle); detail.getStyleClass().add("page-subtitle");
        box.getChildren().addAll(heading, detail);
        return box;
    }

    private String subtitle(String page) {
        return switch (page) {
            case "Hardware Health" -> "Monitor your device's hardware, sensors and system components in real time.";
            case "Smart Router" -> "Manage network services, processes and optimize your connection.";
            case "Storage Healer" -> "Scan, find and remove junk. Keep your system clean and fast.";
            case "Converter" -> "Convert your files quickly and easily. Supports multiple formats and works offline.";
            case "Settings" -> "Customize your PulseOS experience.";
            default -> "Monitor your device's hardware, sensors and system components in real time.";
        };
    }

    private Node buildPage(String page) {
        return switch (page) {
            case "Hardware Health" -> hardwareHealth();
            case "Smart Router" -> withSidebar("SMART ROUTER", "Smart Router", router());
            case "Storage Healer" -> storage();
            case "Converter" -> withSidebar("CONVERTER", "Converter", converter());
            case "Settings" -> withSidebar("SETTINGS", "Settings", settings());
            default -> overview();
        };
    }

    private Node withSidebar(String title, String active, Node content) {
        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("reference-sidebar");
        Label heading = new Label(title); heading.getStyleClass().add("sidebar-title"); sidebar.getChildren().add(heading);
        String[] items = title.equals("CONVERTER") ? new String[]{"Image", "Video", "Audio", "Document", "Ebook", "Archive", "Quick Tasks", "Recent Conversions"} : title.equals("SETTINGS") ? new String[]{"General", "System", "Privacy & Security", "App Management", "Network", "Appearance", "About"} : new String[]{"Overview", "Hardware Health", "Smart Router", "Storage Healer", "Converter", "Settings"};
        for (String item : items) { Button button = new Button(item); button.getStyleClass().add(item.equals(active) ? "sidebar-active" : "sidebar-button"); sidebar.getChildren().add(button); }
        VBox.setVgrow(sidebar, Priority.ALWAYS);
        HBox layout = new HBox(10, sidebar, content);
        HBox.setHgrow(content, Priority.ALWAYS);
        layout.getStyleClass().add("reference-page-layout");
        return layout;
    }

    private VBox overview() {
        DeviceService.DeviceInfo device = deviceService.getDeviceInfo();
        DeviceService.HealthInfo health = deviceService.getHealthInfo();
        DeviceService.PerformanceInfo performanceInfo = deviceService.getPerformanceInfo();

        GridPane grid = fixedGrid(25, 32, 18, 25);

        VBox score = card("DEVICE HEALTH SCORE",
                new HBox(10, score(health.score() / 100.0),
                        new VBox(4,
                                text(health.score() + " /100"),
                                badge("EXCELLENT"),
                                text("Your device is operating within the original health baseline."))),
                metric("Performance", "90%", .90),
                metric("Hardware", "83%", .83),
                metric("Storage", "100%", 1),
                metric("Battery", "98%", .98));
        score.getStyleClass().add("health-score-panel");

        VBox snapshot = card("LIVE DEVICE SNAPSHOT",
                singleLine("Real-time system information"),
                new HBox(12,
                        new DeviceImage(device),
                        new VBox(3,
                                snapshotRow("Device", device.model(), "CPU", device.cpu()),
                                snapshotRow("OS", device.operatingSystem(), "RAM", device.memory()),
                                snapshotRow("Uptime", device.uptime(), "Storage", device.storage()))));
        snapshot.getStyleClass().add("snapshot-panel");

        VBox quick = card("QUICK HEALTH STATUS",
                status("CPU", "Normal", false),
                status("Memory", "Normal", false),
                status("Storage", "Normal", false),
                status("Battery", "Normal", false),
                status("Network", "Problem", true));
        quick.getStyleClass().add("quick-status");

        VBox drainers = card("TOP RESOURCE DRAINERS",
                resource("CPU", "Apple M1 (8 cores)", "9.4%", .24),
                resource("Memory", "16 GB (LPDDR4)", "6.8%", .18),
                resource("Graphics", "Integrated GPU", "4.2%", .12),
                resource("Storage", "SSD", "2.3%", .08),
                resource("Battery", "Charging", "1.1%", .04));
        drainers.getStyleClass().add("drainers");

        HBox performanceCharts = new HBox(2,
                chart("CPU Load", performanceInfo.cpuLoad() + "%", false),
                chart("Memory Pressure", performanceInfo.memoryPressure() + "%", false),
                chart("CPU Temperature", performanceInfo.temperature() + "°C", true));
        for (Node chartNode : performanceCharts.getChildren()) {
            if (chartNode instanceof Region region) {
                region.setMinWidth(0);
                region.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(region, Priority.ALWAYS);
            }
        }
        VBox performance = card("LIVE PERFORMANCE",
                singleLine("Real-time hardware usage and temperature."),
                performanceCharts);
        performance.getStyleClass().add("live-performance");

        VBox assistant = card("ASSISTANT  BETA",
                text("Ask PulseOS anything about your device or system health."),
                new TextField("Type your question..."));
        assistant.getStyleClass().add("assistant");

        VBox hardware = card("HARDWARE INFORMATION",
                infoRow("Processor", "Apple M1 (8 cores)", "3.4 GHz"),
                infoRow("RAM", "16 GB (LPDDR4)", "10.1 GB used"),
                infoRow("Graphics", "Apple M1 (Integrated)", "9% usage"),
                infoRow("Storage", "512 GB SSD", "312 GB used"),
                infoRow("Battery", "98% (Charging)", "~4h 32m remaining"));
        hardware.getStyleClass().add("hardware-info");

        VBox predictive = card("PREDICTIVE HEALTH",
                new HBox(5, text("Outlook: stable - no immediate degradation pattern is visible"), badge("0 active")),
                text("from the current telemetry trend."),
                text("Outlook: CPU 43% · RAM 79% · Temp 62°C · Storage 29% used"),
                text("Trend: CPU rising · RAM stable · Thermal falling · Storage stable"),
                text("Rule-based local outlook · no cloud required"));
        predictive.getStyleClass().add("predictive");

        VBox watcher = card("SYSTEM WATCHER",
                status("Downloads", "0 files", false),
                status("Telemetry", "Initialized", false),
                status("Storage Analysis", "Running", false),
                status("System Watcher", "Initialized", false));
        watcher.getStyleClass().add("system-watcher");

        VBox recent = card("RECENT ACTIVITY",
                infoRow("●", "PulseOS dashboard started", "12:51:30 pm"),
                infoRow("●", "Telemetry engine initialized", "12:51:30 pm"),
                infoRow("●", "Storage analysis started", "12:51:30 pm"));
        recent.getStyleClass().add("activity");

        VBox network = card("NETWORK HEALTH",
                badge("PROBLEM"),
                text("DNS or internet reachability failed"),
                action("Diagnose →"));
        network.getStyleClass().add("network-health");

        VBox storagePanel = card("STORAGE INTELLIGENCE",
                new HBox(7, score(.68), new VBox(3,
                        singleLine("68% Used"),
                        infoRow("Free Space", "156 GB", ""),
                        infoRow("Apps", "124 GB", ""),
                        infoRow("Media", "96 GB", ""))));
        storagePanel.getStyleClass().add("storage-intelligence");

        place(grid, score, 0, 0);
        place(grid, snapshot, 1, 0, 2, 1);
        place(grid, quick, 3, 0);
        place(grid, drainers, 0, 1);
        place(grid, performance, 1, 1, 2, 1);
        place(grid, assistant, 3, 1);
        place(grid, hardware, 0, 2, 2, 1);
        place(grid, predictive, 2, 2, 2, 1);
        place(grid, watcher, 0, 3);
        place(grid, recent, 1, 3);
        place(grid, network, 2, 3);
        place(grid, storagePanel, 3, 3);

        VBox overview = new VBox(grid);
        VBox.setVgrow(grid, Priority.NEVER);
        overview.setFillWidth(true);
        return overview;
    }

    private Node hardwareHealth() {
        GridPane grid = pageGrid(new double[]{50, 50});
        String[] names = {"CPU / Processor", "RAM / Memory", "GPU / Graphics", "Storage / SSD", "Battery / Power", "Fan / Cooling", "Thermal Sensors", "Network", "Wi-Fi / Bluetooth", "Display / Input", "Audio / Camera", "USB / Peripherals", "Motherboard / Firmware", "Startup / Crashes", "OS / Drivers", "Security"};
        String[] states = {"GOOD", "GOOD", "GOOD", "GOOD", "GOOD", "LIMITED", "GOOD", "PROBLEM", "GOOD", "GOOD", "GOOD", "GOOD", "GOOD", "LIMITED", "LIMITED", "LIMITED"};
        String[] detail = {"Load 14.5% · Clock 2.96 GHz", "Used 79.6% · 8.87 / 8.00 GB", "Apple A18 Pro · health sensors limited", "Capacity 29.1% used · SMART health unavailable", "Charge 98% · charging", "Fan RPM is not exposed by this device", "CPU temperature 54.1°C", "DNS or internet reachability failed", "Network: 9 · Bluetooth: 2 · signal and disconnect history need a platform adapter", "Display detected · input tests not run", "Apple Inc. · camera/mic tests require permission", "1 USB device(s) detected · health tests not run", "Apple Inc. · firmware Apple Inc. 700", "Windows Event Viewer and macOS unified-log adapter are not connected yet", "Service, update and driver-event adapter is not connected yet", "Protection-state adapter is not connected; PulseOS will not claim malware from heuristics."};
        for (int i = 0; i < names.length; i++) { VBox item = card(names[i], new HBox(7, badge(states[i]), text(detail[i])), action("Diagnose  ›")); place(grid, item, i % 2, i / 2); }
        return grid;
    }

    private Node router() {
        GridPane grid = pageGrid(new double[]{58, 24, 18});
        place(grid, card("SMART ROUTER", text("Manage network services, processes and optimize your connection.")), 0, 0);
        place(grid, card("NETWORK HEALTH", badge("GOOD"), text("Internet Status     ● Connected"), text("Download ↓ 48.6 Mbps   Upload ↑ 12.3 Mbps   Latency ◷ 18 ms")), 1, 0);
        place(grid, card("CONNECTION DETAILS", text("Network       Wi-Fi (Heme_5G)"), text("IP Address    192.168.1.24"), text("Gateway       192.168.1.1"), text("DNS           8.8.8.8")), 2, 0);
        place(grid, card("SCAN NETWORK & PROCESSES", text("Detect running services, optimize network usage and find unnecessary background processes."), action("⌕ Scan Now")), 0, 1);
        place(grid, card("QUICK ACTIONS", action("Optimize Network"), action("Kill Unwanted"), action("View Logs")), 1, 1);
        place(grid, card("ROUTER PERFORMANCE", ring("CPU", "9%"), ring("RAM", "32%"), text("Network I/O 48.6 / 12.3 Mbps"), text("Uptime 24 h 10m")), 2, 1);
        place(grid, card("RUNNING SERVICES & PROCESSES",
            processRow("NetworkManager", "597", "2.1%", "13.8%"),
            processRow("wpa_supplicant", "738", "1.3%", "6.5%"),
            processRow("dnsmasq", "821", "0.8%", "4.2%"),
            processRow("firewalld", "642", "0.6%", "3.1%"),
            processRow("nginx", "1032", "0.4%", "2.7%"),
            processRow("unbound", "756", "0.4%", "3.1%"),
            processRow("cupsys", "756", "0.2%", "1.4%"),
            processRow("cspod", "721", "0.2%", "1.0%"),
            processRow("ntpd", "640", "0.1%", "0.8%"),
            processRow("cron", "543", "0.0%", "0.5%"),
            processRow("systemd-journald", "432", "0.4%", "0.4%"),
            processRow("httpd", "1188", "0.3%", "0.3%")), 0, 2, 2, 2);
        place(grid, card("RECENT ACTIVITY", text("● Network scan completed       12:50:17 pm"), text("● Optimized services              14:46:32 pm"), text("● Connection stable                12:41:03 pm")), 2, 2);
        place(grid, card("GUIDED ACTIONS", action("Speed up your connection"), action("Reduce background usage"), action("Check for firmware updates")), 2, 3);
        return grid;
    }

    private Node storage() {
        GridPane grid = pageGrid(new double[]{67, 33});
        place(grid, card("STORAGE USAGE", text("C: Local Disk                         180 GB Free"), meter(0.62), text("Total 476 GB  ·  Used 296 GB (62%)")), 0, 0);
        place(grid, card("STORAGE BREAKDOWN", ring("USED", "62%"), text("Apps & System      142 GB"), text("Images                    58 GB"), text("Videos                    76 GB"), text("Audio                      19 GB"), text("Documents              34 GB"), text("Other                       67 GB")), 0, 1);
        place(grid, card("QUICK ACTIONS", action("Free Space  ~12.4 GB"), action("Clear Cache  ~3.8 GB"), action("Clear Logs  ~1.2 GB"), action("Safe Cleanup")), 1, 0, 1, 2);
        place(grid, card("LARGE FILES", fileRow("ubuntu-27.04-desktop-amd64.iso", "4.7 GB", "12 days", "Likely Stale"), fileRow("video-editing-project.tmp", "1.2 GB", "7 days", "Safe"), fileRow("game-installers.zip", "892 MB", "20 days", "Safe"), fileRow("system_log_2025-06-08.log", "642 MB", "2 days", "Safe"), fileRow("cache_0ab12.tmp", "521 MB", "3 days", "Safe"), fileRow("node_modules.cache", "348 MB", "3 days", "Safe"), fileRow("build_artifacts", "291 MB", "5 days", "Ghost Build"), fileRow("flutter_plugins-dependencies", "176 MB", "8 days", "Safe"), fileRow("old_backup.zip", "176 MB", "45 days", "Stale"), fileRow("temp_video.mp4", "142 MB", "6 days", "Safe")), 0, 2);
        place(grid, card("CLEANUP CANDIDATES", text("Cache Files                         3.8 GB     Safe to remove"), text("Log Files                            1.2 GB     Safe to remove"), text("Temp Files                           892 MB   Safe to remove"), text("Protected Items                  Active projects protected")), 1, 2);
        return grid;
    }

    private Node converter() {
        GridPane grid = pageGrid(new double[]{70, 30});
        place(grid, card("FILE CONVERTER", text("Convert your files quickly and easily. Supports multiple formats and works offline."), badge("100% Local & Offline")), 0, 0, 1, 1);
        HBox categories = new HBox(7); for (String item : new String[]{"Image", "Video", "Audio", "Document", "Ebook", "Archive"}) { Button b = new Button(item); b.getStyleClass().add(item.equals("Image") ? "category-active" : "category-button"); categories.getChildren().add(b); }
        place(grid, card("", categories, dropZone("+\n\nDrag & Drop Your Files Here\nor click to browse from your device\n\nSupports: JPG, PNG, WEBP, BMP, TIFF, GIF"), action("Choose Files")), 0, 1);
        place(grid, card("CONVERSION PROGRESS", progressRow("landscape.png", "JPG → PNG", "Completed", 1), progressRow("movie.mp4", "MP4 → WEBM", "Converting", .64), progressRow("song.mp3", "MP3 → WAV", "Queued", 0)), 1, 0, 1, 2);
        place(grid, card("TARGET FORMAT / QUALITY / OUTPUT", text("Target Format       PNG"), new Slider(0, 100, 55), text("Quality (for JPG/WebP)       55%"), text("Output Folder       ~/PulseOS/Converted"), action("Convert →")), 0, 2);
        place(grid, card("RECENT CONVERSIONS", fileRow("mountain.jpg", "2.4 MB", "JPG → PNG", "Completed"), fileRow("product_video.mp4", "48.7 MB", "MP4 → WEBM", "Completed"), fileRow("voice_note.mp4", "5.2 MB", "MP3 → WAV", "Completed"), fileRow("document.pdf", "1.8 MB", "PDF → DOCX", "Completed"), fileRow("archive.zip", "12.6 MB", "ZIP → RAR", "Completed")), 0, 3);
        return grid;
    }

    private Node settings() {
        GridPane grid = pageGrid(new double[]{50, 50});
        place(grid, settingsCard("GENERAL SETTINGS", "Launch on startup", "Show in system tray", "Automatic updates", "Update channel       Stable (Recommended)"), 0, 0);
        place(grid, settingsCard("SYSTEM SETTINGS", "Language       English (United States)", "System font size       14px (Default)", "Start minimized", "Notifications"), 1, 0);
        place(grid, settingsCard("PRIVACY & SECURITY", "Data collection", "Crash reports", "Permissions                                      ›", "Firewall status                              ● Enabled"), 0, 1);
        place(grid, settingsCard("APPEARANCE", "Theme       Dark     Light     Auto", "Accent color       ●  ●  ●  ●  ●  ●", "Font size       14px", "Compact mode"), 1, 1);
        place(grid, settingsCard("APP MANAGEMENT", "Default apps                                      ›", "Installed apps                                   ›", "App permissions                            ›", "Auto-start apps                               ›"), 0, 2);
        place(grid, settingsCard("ABOUT PULSEOS", "PulseOS", "Intelligent Desktop Utility & Hardware Health", "Version                                      v1.0.0", "Update status                            ● Up to date"), 1, 2);
        return grid;
    }

    private VBox settingsCard(String title, String... rows) { Node[] content = new Node[rows.length]; for (int i = 0; i < rows.length; i++) content[i] = settingRow(rows[i]); return card(title, content); }
    private Node settingRow(String value) {
        HBox row = new HBox(7);
        row.setAlignment(Pos.CENTER_LEFT);
        if (value.contains("›")) {
            Button button = action(value);
            row.getChildren().add(button);
        } else if (value.startsWith("Language") || value.startsWith("System font size") || value.startsWith("Update channel")) {
            ComboBox<String> select = new ComboBox<>();
            select.getItems().add(value.substring(value.indexOf(" ") + 1));
            select.getSelectionModel().selectFirst();
            row.getChildren().add(select);
        } else {
            CheckBox toggle = new CheckBox();
            toggle.setSelected(!value.startsWith("Start minimized") && !value.startsWith("Compact mode"));
            row.getChildren().addAll(toggle, text(value));
        }
        return row;
    }
    private GridPane pageGrid(double[] widths) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("grid-pane");
        grid.setHgap(3);
        grid.setVgap(3);
        grid.setMinWidth(0);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setPrefWidth(Region.USE_COMPUTED_SIZE);
        for (double width : widths) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(width);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }
        for (Node node : grid.getChildren()) {
            if (node instanceof Region region) {
                region.setMinWidth(0);
                region.setMaxWidth(Double.MAX_VALUE);
            }
        }
        return grid;
    }

    /**
     * Overview uses percentage columns instead of fixed pixel widths.
     * This prevents large empty areas and keeps all cards packed into one viewport.
     */
    private GridPane fixedGrid(double... widths) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("grid-pane");
        grid.setHgap(3);
        grid.setVgap(3);
        grid.setMinWidth(0);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setPrefWidth(Region.USE_COMPUTED_SIZE);

        for (double width : widths) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(width);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }
        for (Node node : grid.getChildren()) {
            if (node instanceof Region region) {
                region.setMinWidth(0);
                region.setMaxWidth(Double.MAX_VALUE);
            }
        }
        return grid;
    }
    private void place(GridPane grid, Node node, int column, int row) { place(grid, node, column, row, 1, 1); }
    private void place(GridPane grid, Node node, int column, int row, int columnSpan, int rowSpan) {
        grid.add(node, column, row, columnSpan, rowSpan);
        GridPane.setHgrow(node, Priority.ALWAYS);
        GridPane.setVgrow(node, Priority.ALWAYS);
        for (Node child : grid.getChildren()) {
            if (child instanceof Region region) {
                region.setMinWidth(0);
                region.setMaxWidth(Double.MAX_VALUE);
            }
        }
    }
    private VBox card(String title, Node... content) {
        VBox box = new VBox(3);
        box.getStyleClass().addAll("reference-card", "panel");
        if (!title.isBlank()) {
            Label heading = new Label(title);
            heading.getStyleClass().addAll("card-heading", "panel-heading");
            box.getChildren().add(heading);
        }
        box.getChildren().addAll(content);
        double height = switch (title) {
            case "DEVICE HEALTH SCORE", "LIVE DEVICE SNAPSHOT", "QUICK HEALTH STATUS" -> 125;
            case "TOP RESOURCE DRAINERS", "LIVE PERFORMANCE", "ASSISTANT  BETA" -> 150;
            case "HARDWARE INFORMATION", "PREDICTIVE HEALTH" -> 105;
            case "SYSTEM WATCHER", "RECENT ACTIVITY", "NETWORK HEALTH", "STORAGE INTELLIGENCE" -> 90;
            default -> 0;
        };
        if (height > 0) {
            box.setMinHeight(height);
            box.setPrefHeight(height);
        }
        return box;
    }
    private Label text(String value) { Label label = new Label(value); label.setWrapText(true); label.getStyleClass().add("card-text"); return label; }
    private Label badge(String value) { Label label = new Label(value); label.getStyleClass().add("status-badge"); return label; }
    private Button action(String value) { Button button = new Button(value); button.getStyleClass().add("action-button"); return button; }
    private ProgressBar meter(double value) { ProgressBar bar = new ProgressBar(value); bar.setMaxWidth(Double.MAX_VALUE); bar.getStyleClass().add("reference-meter"); return bar; }
    private StackPane score(double value) {
        ProgressIndicator indicator = new ProgressIndicator(value);
        indicator.setPrefSize(74, 74);
        indicator.setMinSize(74, 74);
        indicator.setMaxSize(74, 74);
        indicator.getStyleClass().add("score-ring");
        Label valueLabel = text(Math.round(value * 100) + "%");
        valueLabel.getStyleClass().add("score-value");
        StackPane gauge = new StackPane(indicator, valueLabel);
        gauge.getStyleClass().add("score-gauge");
        return gauge;
    }
    private HBox metric(String name, String value, double progress) { Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); return new HBox(7, text(name), meter(progress), text(value)); }
    private HBox status(String name, String value, boolean problem) {
        Label dot = new Label("●");
        dot.getStyleClass().add(problem ? "status-problem" : "status-good");
        HBox row = dataRow(fixed(dot, 18), fixed(singleLine(name), 92), fixed(singleLine(value), 72), fixed(new Label("›"), 16));
        row.getStyleClass().add("data-row");
        return row;
    }
    private HBox resource(String name, String detail, String value, double progress) {
        HBox row = dataRow(fixed(text("◉"), 18), fixed(new VBox(2, singleLine(name), singleLine(detail)), 150), fixed(meter(progress), 82), fixed(singleLine(value), 42));
        row.getStyleClass().add("data-row");
        return row;
    }
    private VBox chart(String title, String value, boolean warm) {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis(0, 100, 25);
        xAxis.setTickLabelsVisible(false);
        xAxis.setOpacity(0);
        yAxis.setTickLabelsVisible(false);
        yAxis.setOpacity(0);
        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setLegendVisible(false);
        lineChart.setCreateSymbols(false);
        lineChart.setAnimated(false);
        lineChart.setHorizontalGridLinesVisible(true);
        lineChart.setVerticalGridLinesVisible(true);
        lineChart.setMinWidth(0);
        lineChart.setPrefHeight(82);
        lineChart.setMinHeight(72);
        lineChart.setMaxHeight(88);
        lineChart.getStyleClass().add(warm ? "warm-line-chart" : "cool-line-chart");
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        int[] values = warm ? new int[]{48, 53, 51, 57, 54, 61, 58, 64, 62, 68, 65, 72} : new int[]{35, 31, 34, 29, 32, 27, 30, 26, 29, 25, 28, 27};
        for (int i = 0; i < values.length; i++) series.getData().add(new XYChart.Data<>(i, values[i]));
        lineChart.getData().add(series);
        VBox chart = new VBox(3);
        chart.getStyleClass().add("mini-chart");
        HBox heading = new HBox(new Label(title), new Region(), new Label(value));
        HBox.setHgrow(heading.getChildren().get(1), Priority.ALWAYS);
        chart.getChildren().addAll(heading, lineChart);
        VBox.setVgrow(lineChart, Priority.ALWAYS);
        return chart;
    }
    private HBox ring(String name, String value) { Label circle = new Label(value); circle.getStyleClass().add("ring-value"); return new HBox(7, circle, text(name)); }
    private HBox processRow(String name, String pid, String cpu, String ram) {
        HBox row = dataRow(text(name), text("PID " + pid), text("CPU " + cpu), text("RAM " + ram), badge("Running"));
        row.getStyleClass().add("data-row");
        return row;
    }
    private HBox fileRow(String name, String size, String age, String state) {
        HBox row = dataRow(singleLine("▣  " + name), singleLine(size), singleLine(age), badge(state));
        row.getStyleClass().add("data-row");
        return row;
    }
    private HBox infoRow(String label, String value, String reading) {
        HBox row = dataRow(fixed(singleLine(label), 92), fixed(singleLine(value), 155), fixed(singleLine(reading), 115));
        row.getStyleClass().add("data-row");
        return row;
    }
    private HBox snapshotRow(String leftLabel, String leftValue, String rightLabel, String rightValue) {
        HBox row = dataRow(
                fixed(singleLine(leftLabel), 58),
                fixed(singleLine(leftValue), 142),
                fixed(singleLine(rightLabel), 58),
                fixed(singleLine(rightValue), 155));
        row.getStyleClass().add("data-row");
        return row;
    }
    private Label singleLine(String value) {
        Label label = new Label(value);
        label.setWrapText(false);
        label.getStyleClass().add("card-text");
        return label;
    }
    private Node fixed(Node node, double width) {
        if (node instanceof Region region) {
            region.setMinWidth(width);
            region.setPrefWidth(width);
            region.setMaxWidth(width);
        }
        return node;
    }
    private HBox dataRow(Node... cells) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        for (int i = 0; i < cells.length; i++) {
            Node cell = cells[i];
            row.getChildren().add(cell);
        }
        return row;
    }
    private Label dropZone(String value) { Label label = new Label(value); label.setWrapText(true); label.setAlignment(Pos.CENTER); label.getStyleClass().add("converter-drop-zone"); return label; }
    private HBox progressRow(String file, String conversion, String state, double progress) { return new HBox(7, text(file), text(conversion), meter(progress), badge(state)); }
}
