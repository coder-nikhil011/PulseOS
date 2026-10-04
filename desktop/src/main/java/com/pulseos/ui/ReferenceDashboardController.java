package com.pulseos.ui;

import com.pulseos.data.DeviceService;
import com.pulseos.data.RealDeviceService;
import com.pulseos.ui.components.DeviceImage;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

public final class ReferenceDashboardController {
    @javafx.fxml.FXML private StackPane root;
    private final BorderPane shell = new BorderPane();
    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();
    private final HBox topNav = new HBox(4);
    private final DeviceService deviceService = new RealDeviceService();
    private final String[] pages = {"Overview", "Hardware Health", "Smart Router", "Storage Healer", "Converter", "Settings"};

    public void initialize() {
        root.getChildren().add(shell);
        root.getStyleClass().add("reference-root");
        shell.setTop(buildHeader());
        show("Overview");
    }

    private Node buildHeader() {
        VBox header = new VBox();
        HBox brandRow = new HBox(12);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("brand-row");
        Label logo = new Label("P"); logo.getStyleClass().add("brand-logo");
        Label brand = new Label("PulseOS"); brand.getStyleClass().add("brand-name");
        Label descriptor = new Label("Intelligent Desktop Utility & Hardware Health"); descriptor.getStyleClass().add("brand-descriptor");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label live = new Label("●  LIVE  ·  Last sample 12:52:19 pm"); live.getStyleClass().add("live-pill");
        Button refresh = new Button("⟳ Refresh"); refresh.getStyleClass().add("top-button");
        Button optimizeBtn = new Button("Execute Optimization"); optimizeBtn.getStyleClass().add("optimize-btn"); brandRow.getChildren().addAll(logo, brand, descriptor, spacer, live, refresh, optimizeBtn, new Button("−"), new Button("×"));
        topNav.getStyleClass().add("top-nav");
        topNav.setPadding(new Insets(0, 14, 0, 14));
        header.getChildren().addAll(brandRow, topNav);
        return header;
    }

    private void show(String page) {
        pageTitle.setText(page.equals("Overview") ? "Device Health & Healing Center" : page);
        pageSubtitle.setText(page.equals("Overview") ? "Monitor your device's hardware, sensors and system components in real time." : subtitle(page));
        topNav.getChildren().clear();
        for (String item : pages) {
            Button button = new Button(item);
            button.getStyleClass().add("top-nav-button");
            if (item.equals(page)) button.getStyleClass().add("top-nav-active");
            button.setOnAction(e -> show(item));
            topNav.getChildren().add(button);
        }
        VBox pageBody = new VBox(10, titleBlock());
        pageBody.getStyleClass().add("page-body");
        Node pageNode = buildPage(page);
        pageBody.setFillWidth(true);
        VBox.setVgrow(pageNode, Priority.ALWAYS);
        pageBody.getChildren().add(pageNode);
        ScrollPane scroll = new ScrollPane(pageBody);
        scroll.setFitToWidth(true); scroll.setFitToHeight(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("reference-scroll");
        shell.setCenter(scroll);
    }

    private VBox titleBlock() { VBox block = new VBox(2, pageTitle, pageSubtitle); pageTitle.getStyleClass().setAll("page-title"); pageSubtitle.getStyleClass().setAll("page-subtitle"); return block; }
    private String subtitle(String page) { return switch (page) { case "Hardware Health" -> "Monitor your device's hardware, sensors and system components in real time."; case "Smart Router" -> "Manage network services, processes and optimize your connection."; case "Storage Healer" -> "Scan, find and remove junk. Keep your system clean and fast."; case "Converter" -> "Convert your files quickly and easily. Supports multiple formats and works offline."; default -> "Customize your PulseOS experience."; }; }

    private Node buildPage(String page) { return switch (page) { case "Hardware Health" -> hardware(); case "Smart Router" -> router(); case "Storage Healer" -> storage(); case "Converter" -> converter(); case "Settings" -> settings(); default -> imageLikeOverview(); }; }
    private GridPane grid() { GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setMinHeight(0); grid.setMaxHeight(Double.MAX_VALUE); return grid; }
    private void columns(GridPane grid, double... widths) { for (double width : widths) { javafx.scene.layout.ColumnConstraints column = new javafx.scene.layout.ColumnConstraints(); column.setPercentWidth(width); column.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().add(column); } }
    private void add(GridPane g, Node n, int c, int r) { g.add(n,c,r); GridPane.setHgrow(n, Priority.ALWAYS); }
    private void add(GridPane g, Node n, int c, int r, int colspan, int rowspan) { g.add(n,c,r,colspan,rowspan); GridPane.setHgrow(n, Priority.ALWAYS); GridPane.setVgrow(n, Priority.ALWAYS); }
    private VBox card(String heading, Node... content) { VBox box = new VBox(7); box.getStyleClass().add("reference-card"); Label h = new Label(heading); h.getStyleClass().add("card-heading"); box.getChildren().add(h); box.getChildren().addAll(content); return box; }
    private Label text(String value) { Label l = new Label(value); l.setWrapText(true); l.getStyleClass().add("card-text"); return l; }
    private Button action(String value) { Button b = new Button(value); b.getStyleClass().add("action-button"); return b; }
    private Label heading(String value) { Label label = new Label(value); label.getStyleClass().add("card-heading"); return label; }
    private ProgressBar meter(double value) { ProgressBar bar = new ProgressBar(value); bar.setMaxWidth(Double.MAX_VALUE); bar.getStyleClass().add("reference-meter"); return bar; }
    private HBox metric(String name, String value, double progress) { Label title = new Label(name); title.getStyleClass().add("card-text"); Label reading = new Label(value); reading.getStyleClass().add("metric-reading"); HBox row = new HBox(8, title, meter(progress), reading); row.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(title, Priority.NEVER); HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS); return row; }
    private HBox status(String name, String state, boolean problem) { Label dot = new Label("●"); dot.getStyleClass().add(problem ? "status-problem" : "status-good"); Label label = new Label(name); label.getStyleClass().add("card-text"); Label value = new Label(state); value.getStyleClass().add(problem ? "status-problem" : "status-good"); HBox row = new HBox(7, dot, label, value); row.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(label, Priority.ALWAYS); return row; }
    private VBox imageLikeOverview() {
        DeviceService.DeviceInfo device = deviceService.getDeviceInfo();
        DeviceService.HealthInfo health = deviceService.getHealthInfo();
        DeviceService.PerformanceInfo performance = deviceService.getPerformanceInfo();
        GridPane grid = new GridPane(); grid.setHgap(7); grid.setVgap(7); grid.setMaxHeight(Double.MAX_VALUE); grid.setMinHeight(0);
        double[] rowHeights = {175, 205, 145, 100};
        for (double rowHeight : rowHeights) { RowConstraints row = new RowConstraints(rowHeight, rowHeight, rowHeight); row.setVgrow(Priority.NEVER); row.setFillHeight(true); grid.getRowConstraints().add(row); }
        for (int i = 0; i < 4; i++) { javafx.scene.layout.ColumnConstraints column = new javafx.scene.layout.ColumnConstraints(); column.setPercentWidth(i == 1 ? 34 : 22); column.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().add(column); }

        VBox score = card("DEVICE HEALTH SCORE", new HBox(12, scoreIndicator(health.score() / 100.0), new VBox(4, new Label(health.score() + " /100"), badge("NOMINAL"), text("System state is within defined operating parameters. No critical bottlenecks identified."))), metric("Performance", health.performance() + "%", health.performance() / 100.0), metric("Hardware", health.hardware() + "%", health.hardware() / 100.0), metric("Storage", health.storage() + "%", health.storage() / 100.0), metric("Battery", health.battery() + "%", health.battery() / 100.0));
        VBox snapshot = card("LIVE DEVICE SNAPSHOT", text("Real-time system information"), new HBox(14, new DeviceImage(device), new VBox(5, text("Device     " + device.model()), text("OS              " + device.operatingSystem()), text("Uptime       " + device.uptime())), new VBox(5, text("CPU          " + device.cpu()), text("RAM          " + device.memory()), text("Storage     " + device.storage()))));
        VBox quick = card("QUICK HEALTH STATUS", status("CPU", "Normal", false), status("Memory", "Normal", false), status("Storage", "Normal", false), status("Battery", "Normal", false), status("Network", "Problem", true));
        add(grid, score, 0, 0); add(grid, snapshot, 1, 0, 2, 1); add(grid, quick, 3, 0);

        VBox drainers = card("TOP RESOURCE DRAINERS", resource("CPU", "Apple M1 (8 cores)", "9.4%", .24), resource("Memory", "16 GB (LPDDR4)", "6.8%", .18), resource("Graphics", "Integrated GPU", "4.2%", .12), resource("Storage", "SSD", "2.3%", .08), resource("Battery", "Charging", "1.1%", .04));
        VBox performanceCard = card("LIVE PERFORMANCE", text("Real-time hardware usage and temperature."), chartRow("CPU Load", performance.cpuLoad() + "%", performance.cpuLoad() / 100.0, false), chartRow("Memory Pressure", performance.memoryPressure() + "%", performance.memoryPressure() / 100.0, false), chartRow("CPU Temperature", performance.temperature() + "°C", performance.temperature() / 100.0, true));
        VBox assistant = card("ASSISTANT  BETA", text("Ask PulseOS anything about your device or system health."), new Label("Type your question...                                      ➤"));
        add(grid, drainers, 0, 1); add(grid, performanceCard, 1, 1, 2, 1); add(grid, assistant, 3, 1);

        VBox hardware = card("HARDWARE INFORMATION", text("▣   Processor                 Apple M1 (8 cores)             3.4 GHz"), text("▤   RAM                         16 GB (LPDDR4)                 10.1 GB used"), text("▥   Graphics                  Apple M1 (Integrated)          9% usage"), text("▦   Storage                    512 GB SSD                       312 GB used"), text("▣   Battery                    98% (Charging)                  ~4h 32m remaining"));
        VBox prediction = card("PREDICTIVE ANALYSIS", new HBox(8, heading("System State: Stable. No deviations from baseline detected."), badge("0 alerts")), text("Metric Analysis: Thermal trends and memory pressure are within standard operating ranges."), text("Telemetry: CPU 43% · RAM 79% · Temp 62°C · Storage 29% used"), text("Trend: CPU rising (Standard) · RAM stable · Thermal falling · Storage stable"), text("Heuristic Engine v1.2 · Local Analysis Active"));
        add(grid, hardware, 0, 2, 2, 1); add(grid, prediction, 2, 2, 2, 1);
        add(grid, card("SYSTEM WATCHER", status("Downloads", "0 files", false), status("Telemetry", "Initialized", false), status("Storage Analysis", "Running", false), status("System Watcher", "Initialized", false)), 0, 3, 1, 1);
        add(grid, card("RECENT ACTIVITY", text("● PulseOS dashboard started        12:51:30 pm"), text("● Telemetry engine initialized        12:51:30 pm"), text("● Storage analysis started             12:51:30 pm")), 1, 3, 1, 1);
        add(grid, card("NETWORK HEALTH", badge("PROBLEM"), text("DNS or internet reachability failed"), action("Diagnose"), text("Connection: Not reachable"), text("DNS: Unavailable")), 2, 3); add(grid, card("STORAGE INTELLIGENCE", new HBox(8, scoreIndicator(.68), new VBox(4, text("68% Used"), text("Free Space             156 GB"), text("Apps                         124 GB"), text("Media                       96 GB"))), action("Manage Storage →")), 3, 3);
        VBox wrapper = new VBox(grid); wrapper.setFillWidth(true); VBox.setVgrow(grid, Priority.ALWAYS); return wrapper;
    }
    private ProgressIndicator scoreIndicator() { return scoreIndicator(.91); }
    private ProgressIndicator scoreIndicator(double progress) { ProgressIndicator indicator = new ProgressIndicator(progress); indicator.setPrefSize(58, 58); indicator.getStyleClass().add("score-indicator"); return indicator; }
    private Label badge(String value) { Label label = new Label(value); label.getStyleClass().add("status-badge"); return label; }
    private HBox resource(String name, String detail, String value, double progress) { return new HBox(6, new Label("◉"), new VBox(2, new Label(name), text(detail)), meter(progress), new Label(value)); }
    private VBox chartRow(String name, String value, double progress, boolean warm) { Label title = new Label(name); title.getStyleClass().add("card-text"); Label reading = new Label(value); reading.getStyleClass().add("metric-reading"); Region spacer = new Region(); HBox header = new HBox(title, spacer, reading); HBox.setHgrow(spacer, Priority.ALWAYS); VBox row = new VBox(3, header, sparkline(warm)); row.getStyleClass().add(warm ? "warm-chart" : "cool-chart"); return row; }
    private HBox sparkline(boolean warm) { HBox line = new HBox(2); line.getStyleClass().add(warm ? "sparkline warm-sparkline" : "sparkline"); int[] heights = warm ? new int[]{18, 21, 20, 23, 22, 26, 24, 28, 27, 30, 29, 32} : new int[]{14, 20, 17, 23, 18, 19, 16, 22, 20, 21, 19, 22}; for (int height : heights) { Region bar = new Region(); bar.setPrefHeight(height); bar.setMinHeight(height); bar.setMaxHeight(height); HBox.setHgrow(bar, Priority.ALWAYS); bar.getStyleClass().add("spark-bar"); line.getChildren().add(bar); } return line; }
    private VBox overview() { GridPane g=grid(); add(g,card("DEVICE HEALTH SCORE", text("91 /100     EXCELLENT"), text("Your device is operating within the original health baseline."), action("LIVE DEVICE SNAPSHOT")),0,0); add(g,card("LIVE DEVICE SNAPSHOT",text("Device   Apple MacBook Pro"),text("OS        macOS 14.6"),text("CPU       Apple M1 (8 cores)"),text("RAM       16 GB (LPDDR4)"),text("Storage   512 GB SSD")),1,0); add(g,card("QUICK HEALTH STATUS",text("● CPU                         Normal"),text("● Memory                  Normal"),text("● Storage                   Normal"),text("● Battery                   Normal"),text("● Network                 Problem")),2,0); add(g,card("TOP RESOURCE DRAINERS",text("CPU       Apple M1 (8 cores)     9.4%"),text("Memory   16 GB (LPDDR4)          6.8%"),text("Graphics  Integrated GPU             4.2%"),text("Storage   SSD                              2.3%"),text("Battery   Charging                    1.1%")),0,1); add(g,card("LIVE PERFORMANCE",text("CPU Load                                      24%"),text("▂▃▂▃▃▂▃▂▃▂▃▂▃▂▃▂▃▂"),text("Memory Pressure                         62%"),text("▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃"),text("CPU Temperature                         62°C")),1,1); add(g,card("ASSISTANT  BETA",text("Ask PulseOS anything about your device or system health."),text("Type your question...                         ➤")),2,1); add(g,card("HARDWARE INFORMATION",text("Processor       Apple M1 (8 cores)   3.4 GHz"),text("RAM                 16 GB (LPDDR4)       10.1 GB used"),text("Graphics          Apple M1 (Integrated)  9% usage"),text("Storage            512 GB SSD              312 GB used"),text("Battery             98% (Charging)")),0,2); add(g,card("PREDICTIVE HEALTH",text("Outlook: stable — no immediate degradation pattern is visible."),text("CPU 43% · RAM 79% · Temp 62°C · Storage 29% used"),text("Trend: CPU rising · RAM stable · Thermal falling")),1,2); add(g,card("NETWORK HEALTH",text("PROBLEM"),text("DNS or internet reachability failed"),action("Diagnose")),2,2); add(g,card("STORAGE INTELLIGENCE",text("68% Used"),text("Free Space                    156 GB"),text("Apps                            124 GB"),text("Media                           96 GB"),action("Manage Storage →")),2,3); VBox wrapper = new VBox(g); wrapper.setFillWidth(true); VBox.setVgrow(g, Priority.ALWAYS); return wrapper; }
    private Node hardware(){ GridPane g=grid(); columns(g,50,50); String[] names={"CPU / Processor","RAM / Memory","GPU / Graphics","Storage / SSD","Battery / Power","Fan / Cooling","Thermal Sensors","Network","Wi-Fi / Bluetooth","Display / Input","Audio / Camera","USB / Peripherals","Motherboard / Firmware","Startup / Crashes","OS / Drivers","Security"}; String[] states={"GOOD","GOOD","GOOD","GOOD","GOOD","LIMITED","GOOD","PROBLEM","GOOD","GOOD","GOOD","GOOD","GOOD","LIMITED","LIMITED","LIMITED"}; String[] details={"Load 14.5% · Clock 2.96 GHz","Used 79.6% · 8.87 / 8.00 GB","Apple A18 Pro · (0x1068) · health sensors limited","Capacity 29.1% used · SMART health unavailable","Charge 98% · charging","Fan RPM is not exposed by this device","CPU temperature 54.1°C","DNS or internet reachability failed","Network: 9 · Bluetooth: 2 · signal and disconnect history need a platform adapter","Display detected · input tests not run","Apple Inc. · camera/mic tests require permission","1 USB device(s) detected · health tests not run","Apple Inc. · firmware Apple Inc. 700","Windows Event Viewer and macOS unified-log adapter are not connected yet","Service, update and driver-event adapter is not connected yet","Protection-state adapter is not connected; PulseOS will not claim malware from heuristics."}; for(int i=0;i<names.length;i++) { VBox item=card("◉  "+names[i], new HBox(8, badge(states[i]), text(details[i])), action("Diagnose  ›")); add(g,item,i%2,i/2); } return g; }
    private Node router(){ GridPane g=grid(); columns(g,58,24,18); add(g,card("⌁  SMART ROUTER",text("Manage network services, processes and optimize your connection.")),0,0); add(g,card("NETWORK HEALTH     GOOD",text("Internet Status   ● Connected   Download ↓ 48.6 Mbps   Upload ↑ 12.3 Mbps   Latency ◷ 18 ms")),1,0); add(g,card("CONNECTION DETAILS",text("Network       Wi-Fi (Heme_5G)\nIP Address    192.168.1.24\nGateway       192.168.1.1\nDNS           8.8.8.8")),2,0); add(g,card("SCAN NETWORK & PROCESSES",text("Detect running services, optimize network usage and find unnecessary background processes."),action("⌕ Scan Now")),0,1); add(g,card("QUICK ACTIONS",action("Optimize Network"),action("Kill Unwanted"),action("View Logs")),1,1); add(g,card("ROUTER PERFORMANCE",text("CPU 9%       RAM 32%\nNetwork I/O 48.6 / 12.3 Mbps\nUptime 24 1h 30m")),2,1); add(g,card("RUNNING SERVICES & PROCESSES",text("NetworkManager      PID 597     CPU 2.1%     RAM 13.8%     Running"),text("wPa_supplicant          PID 738     CPU 1.3%     RAM 6.5%     Running"),text("dnsmasq                    PID 821     CPU 0.8%     RAM 4.2%     Running"),text("firewalld                    PID 642     CPU 0.6%     RAM 3.1%     Running"),text("nginx                         PID 1032   CPU 0.4%     RAM 2.7%     Running")),0,2); add(g,card("RECENT ACTIVITY",text("● Network scan completed       12:50:17 pm"),text("● Optimized services              14:46:32 pm"),text("● Connection stable                12:41:03 pm")),2,2); return g; }
    private Node storage(){ GridPane g=grid(); columns(g,67,33); add(g,card("STORAGE USAGE",text("C: Local Disk                         180 GB Free"),text("Total 476 GB  ·  Used 296 GB (62%)"),text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")),0,0); add(g,card("STORAGE BREAKDOWN",text("62% Used"),text("Apps & System 142 GB · Images 58 GB · Videos 76 GB"),text("Audio 19 GB · Documents 34 GB · Other 67 GB")),0,1); add(g,card("QUICK ACTIONS",action("Free Space  ~12.4 GB"),action("Clear Cache  ~3.8 GB"),action("Clear Logs  ~1.2 GB"),action("Safe Cleanup")),1,0,1,2); add(g,card("LARGE FILES / CLEANUP CANDIDATES",text("☑ ubuntu-27.04-desktop-amd64.iso    4.7 GB    12 days    Likely Stale    Review"),text("☑ video-editing-project.tmp             1.2 GB    7 days      Safe              Review"),text("☐ game-installers.zip                       892 MB  20 days    Safe              Review"),text("☑ system_log_2025-06-08.log          642 MB  2 days      Safe              Review")),0,2); return g; }
    private Node converter(){ GridPane g=grid(); columns(g,70,30); add(g,card("▧  FILE CONVERTER",text("Convert your files quickly and easily. Supports multiple formats and works offline."),text("100% Local & Offline")),0,0); add(g,card("IMAGE   VIDEO   AUDIO   DOCUMENT   EBOOK   ARCHIVE",text("＋\n\nDrag & Drop Your Files Here\nor click to browse from your device\n\nSupports: JPG, PNG, WEBP, BMP, TIFF, GIF"),action("Choose Files")),0,1); add(g,card("TARGET FORMAT / QUALITY / OUTPUT",text("Target Format       PNG"),text("Quality (for JPG/WebP)       55%"),text("Output Folder       ~/PulseOS/Converted"),action("Convert →")),0,2); add(g,card("CONVERSION PROGRESS",text("landscape.png     JPG → PNG       Completed 100%"),text("movie.mp4            MP4 → WEBM   Converting 64%"),text("song.mp3              MP3 → WAV       Queued")),1,0,1,3); add(g,card("RECENT CONVERSIONS",text("mountain.jpg       JPG → PNG       2.4 MB       Completed"),text("product_video.mp4  MP4 → WEBM  48.7 MB     Completed"),text("document.pdf        PDF → DOCX      1.8 MB       Completed")),0,3); return g; }
    private Node settings(){ GridPane g=grid(); columns(g,50,50); add(g,card("GENERAL SETTINGS",text("Launch on startup                                      ●"),text("Show in system tray                              ●"),text("Automatic updates                                  ●"),text("Update channel       Stable (Recommended)")),0,0); add(g,card("SYSTEM SETTINGS",text("Language       English (United States)"),text("System font size       14px (Default)"),text("Start minimized                                      ○"),text("Notifications                                             ●")),1,0); add(g,card("PRIVACY & SECURITY",text("Data collection                                        ●"),text("Crash reports                                           ●"),text("Permissions                                            ›"),text("Firewall status                                   ● Enabled")),0,1); add(g,card("APPEARANCE",text("Theme       Dark     Light     Auto"),text("Accent color       ●  ●  ●  ●  ●  ●"),text("Font size       14px"),text("Compact mode                                       ○")),1,1); add(g,card("APP MANAGEMENT",text("Default apps                                             ›"),text("Installed apps                                          ›"),text("App permissions                                   ›"),text("Auto-start apps                                      ›")),0,2); add(g,card("ABOUT PULSEOS",text("PulseOS\nIntelligent Desktop Utility & Hardware Health"),text("Version                                                v1.0.0"),text("Update status                                      ● Up to date")),1,2); return g; }
}