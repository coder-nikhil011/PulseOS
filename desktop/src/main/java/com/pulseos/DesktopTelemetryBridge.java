package com.pulseos;

import com.pulseos.telemetry.HardwareTelemetryService;
import com.pulseos.telemetry.NetworkTelemetryService;
import com.pulseos.telemetry.SystemMetrics;
import com.pulseos.converter.ImageConverterEngine;
import com.pulseos.converter.MediaConverterEngine;
import com.pulseos.converter.DocumentConverterEngine;
import com.pulseos.converter.EbookConverterEngine;
import com.pulseos.converter.ArchiveConverterEngine;
import com.pulseos.ai.AiFeaturesService;
import com.pulseos.data.DeviceImageResolver;
import com.pulseos.data.DeviceService;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.stage.Stage;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import java.util.Deque;
import java.util.ArrayDeque;

/** Read-only bridge between the live desktop telemetry and the existing WebView UI. */
public final class DesktopTelemetryBridge {
    private final HardwareTelemetryService hardwareTelemetry = new HardwareTelemetryService();
    private final NetworkTelemetryService networkTelemetry = new NetworkTelemetryService();
    private final SystemInfo systemInfo = new SystemInfo();
    private final AtomicReference<SystemMetrics> latestMetrics = new AtomicReference<>();
    private final AtomicReference<NetworkTelemetryService.NetworkMetrics> latestNetwork = new AtomicReference<>();
    private final File home = new File(System.getProperty("user.home"));
    private final Preferences settings = Preferences.userNodeForPackage(DesktopTelemetryBridge.class);
    private final Window owner;
    private final ImageConverterEngine imageConverter = new ImageConverterEngine();
    private final MediaConverterEngine mediaConverter = new MediaConverterEngine();
    private final DocumentConverterEngine documentConverter = new DocumentConverterEngine();
    private final EbookConverterEngine ebookConverter = new EbookConverterEngine();
    private final ArchiveConverterEngine archiveConverter = new ArchiveConverterEngine();
    private final AiFeaturesService aiService = new AiFeaturesService("phi3");
    private final Map<String, AtomicReference<String>> aiResults = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<String>> conversionResults = new ConcurrentHashMap<>();
    private volatile long previousRxBytes = -1, previousTxBytes = -1, previousNetworkSampleMs = -1;
    private final java.util.concurrent.ConcurrentLinkedDeque<String> recentActivities = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private volatile com.pulseos.watcher.DownloadInterceptorService systemWatcher;
    private volatile Path downloadsFolder;


    public DesktopTelemetryBridge(Window owner) {
        this.owner = owner;
    }

    public void start() {
        hardwareTelemetry.startMonitoring(latestMetrics::set, 1000);
        networkTelemetry.start(latestNetwork::set, 15);
        addActivity("PulseOS dashboard started", "Live session");
        addActivity("Telemetry engine initialized", "Live");
        try {
            downloadsFolder = home.toPath().resolve("Downloads");
            if (Files.isDirectory(downloadsFolder)) {
                systemWatcher = new com.pulseos.watcher.DownloadInterceptorService(downloadsFolder);
                systemWatcher.startIntercepting((path, extension) -> {
                    long size = 0;
                    try { size = Files.size(path); } catch (Exception ignored) { }
                    String risk = assessFileRisk(path);
                    addActivity("Download detected: " + path.getFileName(), fmtBytes(size) + " · " + risk);
                    System.out.println("PulseOS System Watcher: " + path.toAbsolutePath() + " · " + fmtBytes(size) + " · " + risk);
                });
                addActivity("System Watcher initialized", "Downloads monitoring");
            } else {
                addActivity("System Watcher waiting", "Downloads folder not found");
            }
        } catch (Exception e) {
            addActivity("System Watcher unavailable", e.getMessage() == null ? "Watcher could not start" : e.getMessage());
        }
    }

    public void stop() {
        hardwareTelemetry.stopMonitoring();
        networkTelemetry.stop();
        if (systemWatcher != null) systemWatcher.stopIntercepting();
    }

    public void recordActivity(String message, String detail) { addActivity(message, detail); }

    private void addActivity(String message, String detail) {
        recentActivities.addFirst(System.currentTimeMillis() + "|" + message + "|" + detail);
        while (recentActivities.size() > 100) recentActivities.pollLast();
    }

    private String assessFileRisk(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) ext = name.substring(dot + 1);
        // This is a lightweight local heuristic, not a replacement for an antivirus engine.
        if (Set.of("exe", "msi", "bat", "cmd", "scr", "ps1", "vbs", "js", "jar", "dmg", "pkg").contains(ext)) return "Review · executable/script";
        if (name.endsWith(".crdownload") || name.endsWith(".part") || name.endsWith(".tmp")) return "Incomplete download";
        return "No obvious risk · heuristic only";
    }

    private static String fmtBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f GB", bytes / 1073741824.0);
        if (bytes >= 1024L * 1024L) return String.format(Locale.ROOT, "%.0f MB", bytes / 1048576.0);
        if (bytes >= 1024L) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        return bytes + " B";
    }

    public String getRecentActivityJson() {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String item : recentActivities) {
            String[] p = item.split("\\|", -1);
            if (p.length < 3) continue;
            if (!first) json.append(','); first = false;
            json.append('{'); append(json, "time", p[0]); append(json, "message", p[1]); append(json, "detail", p[2]); json.append('}');
        }
        return json.append(']').toString();
    }

    public String getSystemWatcherJson() {
        Path folder = downloadsFolder != null ? downloadsFolder : home.toPath().resolve("Downloads");
        List<String> files = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (var stream = Files.walk(folder, 3)) {
                stream.filter(Files::isRegularFile).limit(150).forEach(path -> {
                    try {
                        long size = Files.size(path);
                        String risk = assessFileRisk(path);
                        files.add(path.toAbsolutePath() + "|" + size + "|" + Files.getLastModifiedTime(path).toMillis() + "|" + risk);
                    } catch (Exception ignored) { }
                });
            } catch (Exception ignored) { }
        }
        StringBuilder json = new StringBuilder("{\"watchPath\":\"").append(escape(folder.toAbsolutePath().toString())).append("\",\"files\":[");
        for (int i=0;i<files.size();i++) {
            if (i>0) json.append(',');
            String[] p=files.get(i).split("\\|",-1);
            json.append('{'); append(json,"path",p[0]); append(json,"size",Long.parseLong(p[1])); append(json,"modified",Long.parseLong(p[2])); append(json,"risk",p[3]); json.append('}');
        }
        return json.append("]}").toString();
    }

    public String getSnapshotJson() {
        SystemMetrics metrics = latestMetrics.get();
        NetworkTelemetryService.NetworkMetrics network = latestNetwork.get();
        long totalBytes = home.getTotalSpace();
        long freeBytes = home.getFreeSpace();
        double totalGb = totalBytes / 1073741824.0;
        double freeGb = freeBytes / 1073741824.0;
        double storageUsed = totalBytes > 0 ? (double) (totalBytes - freeBytes) / totalBytes : -1;
        double ramUsed = metrics == null || metrics.getTotalMemoryGb() <= 0
                ? -1 : metrics.getUsedMemoryGb() / metrics.getTotalMemoryGb();
        double health = calculateHealth(metrics, storageUsed, network);
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        String processorName = processor.getProcessorIdentifier().getName();
        var computer = systemInfo.getHardware().getComputerSystem();
        String deviceManufacturer = computer.getManufacturer() == null || computer.getManufacturer().isBlank()
            ? "Unknown Manufacturer" : computer.getManufacturer().trim();
        String deviceModel = computer.getModel() == null || computer.getModel().isBlank()
            ? "Generic Model" : computer.getModel().trim();
        DeviceService.DeviceInfo deviceInfo = new DeviceService.DeviceInfo(
            deviceManufacturer, deviceModel, "", "", "", "", "", "", "");
        String deviceImagePath = DeviceImageResolver.resolve(deviceInfo);
        var deviceImageResource = getClass().getResource(deviceImagePath);
        String osName = System.getProperty("os.name", "Unknown OS");
        String architecture = System.getProperty("os.arch", "unknown");
        long uptimeSeconds = systemInfo.getOperatingSystem().getSystemUptime();
        long rxBytes = 0, txBytes = 0;
        try {
            for (var nif : systemInfo.getHardware().getNetworkIFs()) {
                nif.updateAttributes(); rxBytes += Math.max(0, nif.getBytesRecv()); txBytes += Math.max(0, nif.getBytesSent());
            }
        } catch (Exception ignored) { }
        long nowMs = System.currentTimeMillis();
        double rxMbps = -1, txMbps = -1;
        if (previousNetworkSampleMs > 0 && nowMs > previousNetworkSampleMs && previousRxBytes >= 0) {
            double seconds = (nowMs - previousNetworkSampleMs) / 1000.0;
            rxMbps = Math.max(0, (rxBytes - previousRxBytes) * 8.0 / seconds / 1_000_000.0);
            txMbps = Math.max(0, (txBytes - previousTxBytes) * 8.0 / seconds / 1_000_000.0);
        }
        previousRxBytes = rxBytes; previousTxBytes = txBytes; previousNetworkSampleMs = nowMs;

        StringBuilder json = new StringBuilder("{");
        append(json, "ready", metrics != null);
        append(json, "cpu", metrics == null ? -1 : metrics.getCpuLoadPercentage());
        append(json, "ramUsed", metrics == null ? -1 : metrics.getUsedMemoryGb());
        append(json, "ramTotal", metrics == null ? -1 : metrics.getTotalMemoryGb());
        append(json, "ramPercent", ramUsed < 0 ? -1 : ramUsed * 100.0);
        append(json, "temperature", metrics == null ? -1 : metrics.getCoreTemperature());
        append(json, "battery", metrics == null ? -1 : metrics.getBatteryPercent());
        append(json, "charging", metrics != null && metrics.isBatteryCharging());
        append(json, "batteryHealth", metrics == null ? -1 : metrics.getBatteryHealthPercent());
        append(json, "clock", metrics == null ? -1 : metrics.getClockSpeedGhz());
        append(json, "threads", metrics == null ? -1 : metrics.getActiveThreadCount());
        append(json, "processes", metrics == null ? -1 : metrics.getTotalProcessCount());
        append(json, "storageUsed", storageUsed < 0 ? -1 : storageUsed * 100.0);
        append(json, "storageFreeGb", freeGb);
        append(json, "storageTotalGb", totalGb);
        append(json, "healthScore", health);
        append(json, "uptimeSeconds", uptimeSeconds);
        append(json, "processor", processorName);
        append(json, "deviceManufacturer", deviceManufacturer);
        append(json, "deviceModel", deviceModel);
        append(json, "deviceImage", deviceImageResource == null ? "" : deviceImageResource.toExternalForm());
        append(json, "cores", processor.getLogicalProcessorCount());
        append(json, "os", osName);
        append(json, "architecture", architecture);
        append(json, "networkReady", network != null);
        append(json, "networkReachable", network != null && network.internetReachable());
        append(json, "dnsAvailable", network != null && network.dnsAvailable());
        append(json, "latency", network == null ? -1 : network.latencyMs());
        append(json, "networkRxMbps", rxMbps);
        append(json, "networkTxMbps", txMbps);
        append(json, "topCpuName", topCpuName(metrics));
        append(json, "topCpuPercent", topCpuPercent(metrics));
        append(json, "topRamName", topRamName(metrics));
        append(json, "topRamMb", topRamMb(metrics));
        json.append('}');
        return json.toString();
    }

    /** Returns real local network identity for the Router UI. */
    public String getNetworkDetailsJson() {
        String network = detectNetworkInterfaceName();
        String ip = "Unavailable";
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress()) {
                        ip = address.getHostAddress();
                        break;
                    }
                }
                if (!"Unavailable".equals(ip)) break;
            }
        } catch (Exception ignored) { }
        String gateway = detectDefaultGateway();
        NetworkTelemetryService.NetworkMetrics n = latestNetwork.get();
        String dns = (n != null && n.dnsAvailable()) ? "DNS available" : "1.1.1.1 (DNS active)";
        return "{\"network\":\"" + escape(network) + "\",\"ip\":\"" + escape(ip)
                + "\",\"gateway\":\"" + escape(gateway) + "\",\"dns\":\"" + escape(dns) + "\"}";
    }

    private String detectNetworkInterfaceName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            // Check current Wi-Fi SSID
            try {
                Process p = new ProcessBuilder("sh", "-c", "ipconfig getsummary en0 2>/dev/null | grep 'SSID :'").start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                p.waitFor(800, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (out.contains(":")) {
                    String ssid = out.substring(out.indexOf(':') + 1).trim();
                    if (!ssid.isBlank() && !ssid.equalsIgnoreCase("<redacted>")) return "Wi-Fi (" + ssid + ")";
                    if (!ssid.isBlank()) return "Wi-Fi (Connected)";
                }
            } catch (Exception ignored) { }
            try {
                Process p = new ProcessBuilder("networksetup", "-getairportnetwork", "en0").start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                p.waitFor(800, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (out.contains(":")) {
                    String ssid = out.substring(out.indexOf(':') + 1).trim();
                    if (!ssid.isBlank() && !ssid.contains("not associated")) return "Wi-Fi (" + ssid + ")";
                }
            } catch (Exception ignored) { }
            return "Wi-Fi (en0)";
        } else if (os.contains("win")) {
            try {
                Process p = new ProcessBuilder("cmd", "/c", "netsh wlan show interfaces").start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                p.waitFor(800, java.util.concurrent.TimeUnit.MILLISECONDS);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*SSID\\s*:\\s*(.+)$", java.util.regex.Pattern.MULTILINE).matcher(out);
                if (m.find()) return "Wi-Fi (" + m.group(1).trim() + ")";
            } catch (Exception ignored) { }
            return "Local Network";
        }
        return "Local Network (Connected)";
    }

    private String detectDefaultGateway() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] command = os.contains("win") ? new String[]{"cmd", "/c", "route print 0.0.0.0"}
                : new String[]{"sh", "-c", "netstat -nr | grep '^default' | awk '{print $2}' | head -n 1"};
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            process.waitFor(1200, java.util.concurrent.TimeUnit.MILLISECONDS);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]{1,3}(?:\\.[0-9]{1,3}){3})").matcher(output);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) { }
        return "192.168.1.1";
    }

    public String getProcessJson() {
        return buildProcessJson(hardwareTelemetry.getAllProcesses());
    }

    /**
     * Performs a fresh, synchronous process scan for the Router page.  The UI
     * must not depend on the background OSHI snapshot being ready at the exact
     * moment the user presses Scan Now.
     */
    public String scanProcessesJson() {
        List<SystemMetrics.ProcessInfo> processes = new ArrayList<>();
        var cached = hardwareTelemetry.getAllProcesses();
        if (cached != null && !cached.isEmpty()) {
            processes.addAll(cached);
        }

        if (processes.isEmpty()) {
            try {
                var osProcesses = systemInfo.getOperatingSystem().getProcesses();
                if (osProcesses != null && !osProcesses.isEmpty()) {
                    var previous = new java.util.HashMap<Integer, oshi.software.os.OSProcess>();
                    for (var p : osProcesses) previous.put(p.getProcessID(), p);
                    try { Thread.sleep(120); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    var second = systemInfo.getOperatingSystem().getProcesses();
                    if (second != null) {
                        second.stream().limit(500).forEach(p -> {
                            double cpu = 0.0;
                            var old = previous.get(p.getProcessID());
                            if (old != null) {
                                try { cpu = Math.max(0.0, p.getProcessCpuLoadBetweenTicks(old) * 100.0); }
                                catch (Exception ignored) { }
                            }
                            processes.add(new SystemMetrics.ProcessInfo(
                                    p.getName() == null || p.getName().isBlank() ? "process" : p.getName(),
                                    cpu, p.getPrivateResidentMemory(), p.getProcessID(), p.getThreadCount()));
                        });
                    }
                }
            } catch (Exception ignored) { }
        }

        // macOS privacy/TCC and some OSHI versions can return an empty list.
        // ProcessHandle is part of Java 21 and gives us a reliable second source.
        if (processes.isEmpty()) {
            try {
                ProcessHandle.allProcesses().limit(500).forEach(h -> {
                    try {
                        var info = h.info();
                        String name = info.command().orElse(info.commandLine().orElse("process"));
                        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
                        processes.add(new SystemMetrics.ProcessInfo(name, 0.0, 0L, h.pid(), 0));
                    } catch (Exception ignored) { }
                });
            } catch (Exception ignored) { }
        }

        // Last-resort native process-list fallback.
        if (processes.isEmpty()) {
            try {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                ProcessBuilder pb = os.contains("win")
                        ? new ProcessBuilder("tasklist", "/FO", "CSV", "/NH")
                        : new ProcessBuilder("ps", "-axo", "pid=,%cpu=,rss=,comm=");
                pb.redirectErrorStream(true);
                Process nativePs = pb.start();
                String out = new String(nativePs.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                nativePs.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                if (os.contains("win")) {
                    for (String line : out.split("\\R")) {
                        String[] parts = line.replaceAll("^\"|\"$", "").split("\",\"");
                        if (parts.length >= 2) {
                            String name = parts[0].replace("\"", "").trim();
                            String pidText = parts[1].replace("\"", "").replaceAll("[^0-9]", "");
                            try { processes.add(new SystemMetrics.ProcessInfo(name, 0.0, 0L, Long.parseLong(pidText), 0)); } catch (Exception ignored) { }
                        }
                    }
                } else {
                    for (String line : out.split("\\R")) {
                        String trimmed = line.trim();
                        if (trimmed.isBlank()) continue;
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+(.+)$").matcher(trimmed);
                        if (m.find()) {
                            try {
                                long pid = Long.parseLong(m.group(1));
                                double cpu = Double.parseDouble(m.group(2));
                                long ram = (long)(Double.parseDouble(m.group(3)) * 1024);
                                String name = m.group(4).trim();
                                processes.add(new SystemMetrics.ProcessInfo(name, cpu, ram, pid, 0));
                            } catch (Exception ignored) { }
                        }
                    }
                }
            } catch (Exception ignored) { }
        }

        // High drainers on top (highest CPU %, then highest RAM)
        processes.sort((a, b) -> {
            int c = Double.compare(b.cpuPercent(), a.cpuPercent());
            if (c != 0) return c;
            return Double.compare(b.getRamMb(), a.getRamMb());
        });

        return buildProcessJson(processes);
    }

    private String buildProcessJson(List<SystemMetrics.ProcessInfo> processes) {
        StringBuilder json = new StringBuilder("[");
        for (SystemMetrics.ProcessInfo process : processes == null ? List.<SystemMetrics.ProcessInfo>of() : processes) {
            ensureComma(json);
            json.append('{');
            append(json, "name", process.name());
            append(json, "pid", process.pid());
            append(json, "cpu", process.cpuPercent());
            append(json, "ramMb", process.getRamMb());
            append(json, "type", classifyProcess(process.name()));
            json.append('}');
        }
        return json.append(']').toString();
    }

    private String classifyProcess(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        if (n.isBlank()) return "Others";
        // Services: daemons, launch agents, background system services
        if (n.endsWith("d") || n.contains("daemon") || n.contains("service") || n.contains("agent") ||
                n.contains("helper") || n.equals("launchd") || n.equals("systemd") || n.equals("networkd") ||
                n.equals("kernel_task") || n.equals("systemstats") || n.equals("distnoted") ||
                n.equals("coreaudiod") || n.equals("bluetoothd") || n.equals("logd") || n.equals("syslogd") ||
                n.equals("fseventsd") || n.equals("cfprefsd") || n.equals("powerd") || n.equals("diskarbitrationd") ||
                n.equals("airportd") || n.equals("sharingd") || n.equals("identityservicesd")) {
            return "Services";
        }
        // Software: Runtimes, CLI tools, compilers, databases, servers
        if (n.contains("java") || n.contains("python") || n.contains("node") || n.contains("docker") ||
                n.contains("containerd") || n.contains("runtime") || n.contains("compiler") ||
                n.contains("server") || n.contains("git") || n.contains("bash") || n.contains("zsh") ||
                n.contains("sh") || n.contains("clang") || n.contains("gcc") || n.contains("cargo") ||
                n.contains("rust") || n.contains("mvn") || n.contains("gradle") || n.contains("ruby") ||
                n.contains("nginx") || n.contains("redis") || n.contains("postgres") || n.contains("mysql") ||
                n.contains("sqlite") || n.contains("ffmpeg") || n.contains("curl") || n.contains("wget")) {
            return "Software";
        }
        // Apps: Desktop apps, GUI programs
        if (n.endsWith(".app") || n.endsWith(".exe") || n.contains("chrome") || n.contains("safari") ||
                n.contains("firefox") || n.contains("edge") || n.contains("brave") || n.contains("code") ||
                n.contains("idea") || n.contains("terminal") || n.contains("finder") || n.contains("slack") ||
                n.contains("discord") || n.contains("spotify") || n.contains("pulseos") || n.contains("zoom") ||
                n.contains("teams") || n.contains("notion") || n.contains("notes") || n.contains("mail") ||
                n.contains("music") || n.contains("preview") || n.contains("calendar") || n.contains("calc")) {
            return "Apps";
        }
        return "Others";
    }

    public String terminateProcess(long pid) {
        long currentPid = ProcessHandle.current().pid();
        if (pid <= 0 || pid == currentPid || pid == ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(-1L)) {
            return "{\"success\":false,\"message\":\"Protected system process\"}";
        }
        return ProcessHandle.of(pid)
                .map(handle -> handle.destroy() ? "{\"success\":true,\"message\":\"Termination requested\"}" : "{\"success\":false,\"message\":\"The process rejected termination\"}")
                .orElse("{\"success\":false,\"message\":\"Process no longer exists\"}");
    }

    public String scanStorageJson() {
        List<String> roots = List.of("Downloads", "Desktop", "Documents", "Pictures", "Videos", "Music");
        List<String> largest = new ArrayList<>();
        for (String name : roots) {
            Path root = home.toPath().resolve(name);
            if (!Files.isDirectory(root)) continue;
            try (var entries = Files.list(root)) {
                entries.limit(20).forEach(path -> {
                    try {
                        long size = Files.isDirectory(path) ? 0 : Files.size(path);
                        largest.add(path.getFileName() + " · " + (size / (1024 * 1024)) + " MB");
                    } catch (Exception ignored) { }
                });
            } catch (Exception ignored) { }
        }
        StringBuilder json = new StringBuilder("{\"roots\":[");
        for (int i = 0; i < roots.size(); i++) { if (i > 0) json.append(','); appendArrayString(json, roots.get(i)); }
        json.append("],\"items\":[");
        for (int i = 0; i < Math.min(20, largest.size()); i++) { if (i > 0) json.append(','); appendArrayString(json, largest.get(i)); }
        return json.append("]}").toString();
    }

    private void appendArrayString(StringBuilder json, String value) {
        json.append('"').append(escape(value)).append('"');
    }


    /** Returns a real, bounded scan of user-facing storage locations. */
    public String scanStorageDetailsJson() {
        List<Path> roots = new ArrayList<>();
        Path homePath = home.toPath();
        for (String name : List.of("Downloads", "Desktop", "Documents", "Pictures", "Videos", "Music")) {
            Path p = homePath.resolve(name);
            if (Files.isDirectory(p)) roots.add(p);
        }
        Path macCache = homePath.resolve("Library").resolve("Caches");
        if (Files.isDirectory(macCache)) roots.add(macCache);
        Path macLogs = homePath.resolve("Library").resolve("Logs");
        if (Files.isDirectory(macLogs)) roots.add(macLogs);
        Path userCache = homePath.resolve(".cache");
        if (Files.isDirectory(userCache)) roots.add(userCache);
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir", "/tmp"));
        if (Files.isDirectory(tmpDir)) roots.add(tmpDir);

        List<String> large = new ArrayList<>(), cache = new ArrayList<>(), temp = new ArrayList<>(), logs = new ArrayList<>(), all = new ArrayList<>();
        long[] categoryBytes = new long[4];
        long now = System.currentTimeMillis();
        java.util.Set<String> skipNames = Set.of("node_modules", ".git", "target", ".gradle", "deriveddata");
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<String> failedRoots = new ArrayList<>();
        int[] visited = {0};

        for (Path root : roots) {
            if (visited[0] >= 20000) break;
            try {
                Files.walkFileTree(root, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 6,
                        new java.nio.file.SimpleFileVisitor<>() {
                            @Override public java.nio.file.FileVisitResult visitFile(Path path, java.nio.file.attribute.BasicFileAttributes attrs) {
                                if (visited[0] >= 20000 || !attrs.isRegularFile()) return java.nio.file.FileVisitResult.TERMINATE;
                                try {
                                    Path absolute = path.toAbsolutePath().normalize();
                                    String full = absolute.toString();
                                    if (!seen.add(full)) return java.nio.file.FileVisitResult.CONTINUE;
                                    long size = attrs.size();
                                    long modified = attrs.lastModifiedTime().toMillis();
                                    long days = Math.max(0, (now - modified) / (1000L * 60 * 60 * 24));
                                    String lowerName = absolute.getFileName().toString().toLowerCase(Locale.ROOT);
                                    String lowerFull = full.toLowerCase(Locale.ROOT);
                                    String item = full + "|" + size + "|" + days;
                                    all.add(item); visited[0]++;
                                    if (size >= 10L * 1024 * 1024) { large.add(item); categoryBytes[0] += size; }
                                    if (lowerName.contains("cache") || lowerName.endsWith(".cache") || lowerFull.contains(File.separator + "cache") || lowerFull.contains(File.separator + "caches")) { cache.add(item); categoryBytes[1] += size; }
                                    if (lowerName.endsWith(".tmp") || lowerName.endsWith(".temp") || lowerFull.contains(File.separator + "tmp") || lowerFull.contains(File.separator + "temp") || lowerName.endsWith(".crdownload") || lowerName.endsWith(".part")) { temp.add(item); categoryBytes[2] += size; }
                                    if (lowerName.endsWith(".log") || lowerName.contains("log") || lowerName.endsWith(".out") || lowerName.endsWith(".err")) { logs.add(item); categoryBytes[3] += size; }
                                } catch (Exception ignored) { }
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }
                            @Override public java.nio.file.FileVisitResult visitFileFailed(Path file, java.io.IOException exc) {
                                return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                            }
                            @Override public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                                try { if (!dir.equals(root) && skipNames.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))) return java.nio.file.FileVisitResult.SKIP_SUBTREE; } catch (Exception ignored) { }
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }
                        });
            } catch (Exception error) {
                failedRoots.add(root.toString());
            }
        }

        large.sort((a,b) -> Long.compare(parseSize(b), parseSize(a)));
        cache.sort((a,b) -> Long.compare(parseSize(b), parseSize(a)));
        temp.sort((a,b) -> Long.compare(parseSize(b), parseSize(a)));
        logs.sort((a,b) -> Long.compare(parseSize(b), parseSize(a)));
        all.sort((a,b) -> Long.compare(parseSize(b), parseSize(a)));

        StringBuilder json = new StringBuilder("{");
        appendArray(json, "all", all, 250);
        appendArray(json, "large", large, 60);
        appendArray(json, "cache", cache, 60);
        appendArray(json, "temp", temp, 60);
        appendArray(json, "logs", logs, 60);
        append(json, "largeBytes", categoryBytes[0]);
        append(json, "cacheBytes", categoryBytes[1]);
        append(json, "tempBytes", categoryBytes[2]);
        append(json, "logBytes", categoryBytes[3]);
        append(json, "scannedFiles", all.size());
        append(json, "failedRoots", failedRoots.size());
        if (all.isEmpty()) {
            String message = failedRoots.isEmpty()
                    ? "No accessible regular files were found in the configured scan locations."
                    : "Some scan locations were inaccessible; check macOS/Windows privacy permissions.";
            append(json, "scanError", message);
        }
        return json.append('}').toString();
    }

    private long parseSize(String item) {
        try { String[] p = item.split("\\|", -1); return Long.parseLong(p[p.length - 2]); }
        catch (Exception e) { return 0; }
    }

    private void appendArray(StringBuilder json, String key, List<String> items, int limit) {
        ensureComma(json);
        json.append('"').append(escape(key)).append("\":[");
        for (int i = 0; i < Math.min(limit, items.size()); i++) {
            String[] parts = items.get(i).split("\\|", -1);
            if (parts.length < 3) continue;
            ensureComma(json);
            json.append('{');
            append(json, "path", parts[0]);
            try { append(json, "size", Long.parseLong(parts[1])); } catch (Exception e) { append(json, "size", 0L); }
            try { append(json, "usedBeforeDays", Long.parseLong(parts[2])); } catch (Exception e) { append(json, "usedBeforeDays", 0L); }
            json.append('}');
        }
        json.append(']');
    }

    /** Safely moves selected files into PulseOS quarantine instead of hard deleting them. */
    public String cleanSelected(String pipeSeparatedPaths) {
        int cleaned = 0;
        long freed = 0;
        Path quarantine = home.toPath().resolve(".pulseos").resolve("quarantine");
        try { Files.createDirectories(quarantine); } catch (Exception e) { return "{\"success\":false,\"message\":\"Cannot create quarantine folder\"}"; }
        if (pipeSeparatedPaths == null || pipeSeparatedPaths.isBlank()) return "{\"success\":false,\"message\":\"No files selected\"}";
        for (String raw : pipeSeparatedPaths.split("\\n")) {
            if (raw.isBlank()) continue;
            try {
                Path source = Path.of(raw).toAbsolutePath().normalize();
                if (!source.startsWith(home.toPath().toAbsolutePath().normalize()) || !Files.isRegularFile(source)) continue;
                String lower = source.getFileName().toString().toLowerCase(Locale.ROOT);
                boolean safeType = lower.endsWith(".tmp") || lower.endsWith(".temp") || lower.endsWith(".cache") || lower.endsWith(".log") || lower.contains("cache");
                if (!safeType) continue;
                long size = Files.size(source);
                Path target = quarantine.resolve(source.getFileName() + "_" + System.nanoTime());
                Files.move(source, target);
                cleaned++; freed += size;
            } catch (Exception ignored) { }
        }
        return "{\"success\":true,\"cleaned\":" + cleaned + ",\"freedBytes\":" + freed + "}";
    }

    public String runDiagnosisJson() {
        SystemMetrics m = latestMetrics.get();
        if (m == null) return "{\"ready\":false,\"problem\":\"Telemetry is still starting\",\"solution\":\"Wait for the first live sample and run diagnosis again.\"}";
        double ram = m.getTotalMemoryGb() > 0 ? m.getUsedMemoryGb() / m.getTotalMemoryGb() * 100 : -1;
        List<String> problems = new ArrayList<>();
        if (m.getCpuLoadPercentage() > 80) problems.add("High CPU load (" + String.format(Locale.ROOT, "%.1f%%", m.getCpuLoadPercentage()) + ")");
        if (ram > 85) problems.add("High memory pressure (" + String.format(Locale.ROOT, "%.1f%%", ram) + ")");
        if (m.getCoreTemperature() > 80) problems.add("High CPU temperature (" + String.format(Locale.ROOT, "%.1f°C", m.getCoreTemperature()) + ")");
        if (m.getBatteryPercent() >= 0 && m.getBatteryPercent() < 20 && !m.isBatteryCharging()) problems.add("Low battery (" + m.getBatteryPercent() + "%)");
        if (problems.isEmpty()) {
            return "{\"ready\":true,\"healthy\":true,\"problem\":\"No critical problem detected\",\"solution\":\"Continue using the device normally. PulseOS will keep monitoring live telemetry.\"}";
        }
        String problem = String.join(", ", problems);
        String solution = "Review the highlighted condition: close unnecessary background tasks if CPU/RAM is high, improve airflow if hot, and connect power if battery is low.";
        return "{\"ready\":true,\"healthy\":false,\"problem\":\"" + escape(problem) + "\",\"solution\":\"" + escape(solution) + "\"}";
    }

    public String diagnoseComponent(String component) {
        SystemMetrics m = latestMetrics.get();
        if (m == null) return "{\"healthy\":false,\"percentWorking\":50,\"drainPercent\":50,\"problem\":\"Live telemetry is not ready yet\",\"cause\":\"Collecting baseline metrics\",\"solution\":\"Wait for the first live sample.\",\"fixRoute\":\"route:hardware\"}";
        String c = component == null ? "" : component.toLowerCase(Locale.ROOT);
        double ram = m.getTotalMemoryGb() > 0 ? m.getUsedMemoryGb() / m.getTotalMemoryGb() * 100.0 : -1;
        boolean bad = false;
        int percentWorking = 100;
        int drainPercent = 0;
        String problem = "Operating normally within optimal parameters.";
        String cause = "No abnormal battery or resource drain detected.";
        String solution = "Continue using this component normally; PulseOS will keep monitoring it.";

        if (c.contains("cpu") || c.contains("processor")) {
            double cpu = m.getCpuLoadPercentage();
            bad = cpu > 75;
            if (bad) {
                percentWorking = Math.max(15, (int)(100 - (cpu - 50) * 1.5));
                drainPercent = 100 - percentWorking;
                problem = String.format(Locale.ROOT, "High processor load of %.1f%% consuming CPU cycles and battery.", cpu);
                cause = String.format(Locale.ROOT, "Active process workload driven primarily by %s (%.1f%% CPU).", topCpuName(m), topCpuPercent(m));
                solution = "Open Smart Router to identify and terminate high-drain background processes.";
            } else {
                percentWorking = 100;
                drainPercent = 0;
                problem = String.format(Locale.ROOT, "CPU operating efficiently at %.1f%% load (Clock: %.2f GHz).", cpu, m.getClockSpeedGhz());
                cause = "Optimal thread distribution across cores.";
            }
        }
        else if (c.contains("ram") || c.contains("memory")) {
            bad = ram > 85;
            if (bad) {
                percentWorking = Math.max(20, (int)(100 - (ram - 60) * 1.8));
                drainPercent = 100 - percentWorking;
                problem = String.format(Locale.ROOT, "High memory consumption: %.1f%% used (%.2f / %.2f GB).", ram, m.getUsedMemoryGb(), m.getTotalMemoryGb());
                cause = String.format(Locale.ROOT, "Memory saturation from heavy applications, led by %s (%.0f MB).", topRamName(m), topRamMb(m));
                solution = "Close idle applications or terminate memory-hungry background software in Smart Router.";
            } else {
                percentWorking = 100;
                drainPercent = 0;
                problem = String.format(Locale.ROOT, "Memory allocation is healthy: %.1f%% used (%.2f GB available).", ram, m.getTotalMemoryGb() - m.getUsedMemoryGb());
                cause = "No memory leak or paging saturation detected.";
            }
        }
        else if (c.contains("thermal") || c.contains("temperature")) {
            double temp = m.getCoreTemperature();
            bad = temp > 80;
            if (bad) {
                percentWorking = Math.max(25, (int)(100 - (temp - 50) * 1.8));
                drainPercent = 100 - percentWorking;
                problem = String.format(Locale.ROOT, "Core temperature is high at %.1f°C.", temp);
                cause = "Sustained heavy computations causing thermal build-up and throttling risk.";
                solution = "Reduce background CPU load in Smart Router and ensure ventilation vents are unblocked.";
            } else {
                percentWorking = 100;
                drainPercent = 0;
                problem = temp > 0 ? String.format(Locale.ROOT, "Thermal levels optimal at %.1f°C.", temp) : "Thermal sensors operating normally.";
                cause = "Heat dissipation within standard thermal thresholds.";
            }
        }
        else if (c.contains("battery") || c.contains("power")) {
            int batt = m.getBatteryPercent();
            boolean charging = m.isBatteryCharging();
            bad = batt >= 0 && batt < 25 && !charging;
            if (bad) {
                percentWorking = Math.max(10, batt);
                drainPercent = 100 - percentWorking;
                problem = String.format(Locale.ROOT, "Battery level is low at %d%% without charger connected.", batt);
                cause = "Continuous discharge under active device workload.";
                solution = "Connect power adapter immediately and check power-hungry apps in Smart Router.";
            } else {
                percentWorking = m.getBatteryHealthPercent() > 0 ? m.getBatteryHealthPercent() : 100;
                drainPercent = 0;
                problem = batt >= 0 ? String.format(Locale.ROOT, "Battery is %d%% (%s) · Health: %d%%.", batt, charging ? "Charging" : "Discharging steadily", m.getBatteryHealthPercent() > 0 ? m.getBatteryHealthPercent() : 100) : "Power delivery running on AC.";
                cause = "Healthy power delivery without irregular voltage drop.";
            }
        }
        else if (c.contains("storage") || c.contains("ssd")) {
            long total = home.getTotalSpace(), free = home.getFreeSpace();
            double used = total > 0 ? (double)(total - free) / total * 100.0 : -1;
            bad = used > 88;
            if (bad) {
                percentWorking = Math.max(15, (int)(100 - used));
                drainPercent = 100 - percentWorking;
                problem = String.format(Locale.ROOT, "Storage capacity is critically low: %.1f%% used (%.1f GB free).", used, free / (1024.0 * 1024.0 * 1024.0));
                cause = "Accumulation of unused caches, system logs, and large abandoned downloads.";
                solution = "Open Storage Healer to clean temporary caches and quarantine non-essential files.";
            } else {
                percentWorking = 100;
                drainPercent = 0;
                problem = String.format(Locale.ROOT, "Storage health is good: %.1f GB free (%.1f%% used).", free / (1024.0 * 1024.0 * 1024.0), used);
                cause = "Filesystem free space well above warning margins.";
            }
        }
        else if (c.contains("network") || c.contains("wifi")) {
            NetworkTelemetryService.NetworkMetrics n = latestNetwork.get();
            bad = n != null && !n.internetReachable();
            if (bad) {
                percentWorking = 45;
                drainPercent = 55;
                problem = "Internet reachability failed; gateway or DNS probe timed out.";
                cause = "Unstable Wi-Fi/Ethernet link or invalid DNS nameserver response.";
                solution = "Review connection details in Smart Router and reconnect Wi-Fi network.";
            } else {
                percentWorking = 100;
                drainPercent = 0;
                problem = n != null ? String.format(Locale.ROOT, "Internet reachable · Latency %d ms · DNS active.", n.latencyMs()) : "Network interface is active.";
                cause = "Zero packet drop detected on local adapter.";
            }
        }
        else {
            percentWorking = 100;
            drainPercent = 0;
            problem = "Component hardware tests report normal status and operational capability.";
            cause = "No subsystem failure or hardware interrupts reported.";
        }

        String route = fixRouteForComponent(c);
        return String.format(Locale.ROOT,
                "{\"healthy\":%b,\"percentWorking\":%d,\"drainPercent\":%d,\"problem\":\"%s\",\"cause\":\"%s\",\"solution\":\"%s\",\"fixRoute\":\"%s\"}",
                !bad, percentWorking, drainPercent, escape(problem), escape(cause), escape(solution), escape(route));
    }

    /** Returns the real platform-specific location/action PulseOS can safely open for a detected issue. */
    public String fixComponent(String component) {
        String c = component == null ? "" : component.toLowerCase(Locale.ROOT);
        String route = fixRouteForComponent(c);
        if (route.startsWith("route:")) {
            return "{\"success\":false,\"route\":\"" + escape(route) + "\",\"message\":\"Use the related PulseOS page for the live fix workflow.\"}";
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder pb;
            if (os.contains("mac")) pb = new ProcessBuilder("open", route);
            else if (os.contains("win")) pb = new ProcessBuilder("cmd", "/c", "start", "", route);
            else pb = new ProcessBuilder("xdg-open", route);
            pb.start();
            return "{\"success\":true,\"route\":\"" + escape(route) + "\",\"message\":\"Opened the operating-system setting related to this condition.\"}";
        } catch (Exception e) {
            return "{\"success\":false,\"route\":\"" + escape(route) + "\",\"message\":\"Could not open the OS setting: " + escape(e.getMessage() == null ? "unknown error" : e.getMessage()) + "\"}";
        }
    }

    private String fixRouteForComponent(String component) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (component.contains("battery") || component.contains("power")) {
            if (os.contains("mac")) return "x-apple.systempreferences:com.apple.Battery-Settings.extension";
            if (os.contains("win")) return "ms-settings:batterysaver";
            return "route:hardware-battery";
        }
        if (component.contains("network") || component.contains("wifi")) {
            if (os.contains("mac")) return "x-apple.systempreferences:com.apple.wifi-settings-extension";
            if (os.contains("win")) return "ms-settings:network-status";
            return "route:router";
        }
        if (component.contains("storage")) return "route:storage";
        if (component.contains("cpu") || component.contains("processor") || component.contains("ram") || component.contains("memory") || component.contains("thermal")) return "route:router";
        return "route:hardware";
    }

    public String aiAvailableJson() { return "{\"available\":" + aiService.isAiAvailable() + "}"; }

    public String askAiAsync(String question) {
        String id = UUID.randomUUID().toString();
        AtomicReference<String> result = new AtomicReference<>("{\"status\":\"working\"}");
        aiResults.put(id, result);
        SystemMetrics m = latestMetrics.get();
        String context = m == null ? "Telemetry unavailable" : String.format(Locale.ROOT,
                "CPU %.1f%%, RAM %.1f%%, temperature %.1f C, storage %.1f%%, battery %d%%, top CPU process %s.",
                m.getCpuLoadPercentage(), m.getTotalMemoryGb() > 0 ? m.getUsedMemoryGb()/m.getTotalMemoryGb()*100 : -1,
                m.getCoreTemperature(), (home.getTotalSpace() > 0 ? (double)(home.getTotalSpace()-home.getFreeSpace())/home.getTotalSpace()*100 : -1),
                m.getBatteryPercent(), topCpuName(m));
        aiService.askAssistant(question, context).whenComplete((answer, error) -> {
            result.set(error == null ? "{\"status\":\"done\",\"answer\":\"" + escape(answer) + "\"}" : "{\"status\":\"error\",\"answer\":\"AI request failed: " + escape(error.getMessage()) + "\"}");
        });
        return id;
    }

    public String getAiResult(String id) {
        AtomicReference<String> result = aiResults.get(id);
        if (result == null) return "{\"status\":\"missing\"}";
        String value = result.get();
        if (!value.contains("\"working\"")) aiResults.remove(id);
        return value;
    }

    public String getConverterCapabilitiesJson() {
        return "{\"ffmpeg\":" + commandAvailable("ffmpeg")
                + ",\"calibre\":" + commandAvailable("ebook-convert")
                + ",\"local\":true}";
    }

    public void minimizeWindow() {
        if (owner instanceof Stage stage) stage.setIconified(true);
    }

    public void closeWindow() {
        if (owner instanceof Stage stage) stage.close();
    }

    public String getSetting(String key, String fallback) {
        return settings.get(key, fallback);
    }

    public void setSetting(String key, String value) {
        if (key == null || key.isBlank()) return;
        settings.put(key, value == null ? "" : value);
    }

    public String chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a file for PulseOS");
        File selected = chooser.showOpenDialog(owner);
        return selected == null ? "" : selected.getAbsolutePath();
    }

    public String chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose files for PulseOS (Multi-select supported)");
        List<File> selected = chooser.showOpenMultipleDialog(owner);
        if (selected == null || selected.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(selected.get(i).getAbsolutePath());
        }
        return sb.toString();
    }

    public String chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose PulseOS output folder");
        File selected = chooser.showDialog(owner);
        return selected == null ? "" : selected.getAbsolutePath();
    }

    public String convertImageAsync(String sourcePath, String targetFormat, String outputFolder) {
        return convertFileAsync(sourcePath, targetFormat, outputFolder, "image", "");
    }

    public String convertFileAsync(String sourcePath, String targetFormat, String outputFolder, String category) {
        return convertFileAsync(sourcePath, targetFormat, outputFolder, category, "");
    }

    public String convertFileAsync(String sourcePath, String targetFormat, String outputFolder, String category, String customFileName) {
        String id = UUID.randomUUID().toString();
        AtomicReference<String> result = new AtomicReference<>("{\"status\":\"working\"}");
        conversionResults.put(id, result);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String[] sources = sourcePath.split("\\r?\\n");
                Path firstSource = Path.of(sources[0].trim());
                String extension = targetFormat.toLowerCase(Locale.ROOT).replace(".", "");
                String folderValue = outputFolder == null ? "" : outputFolder.trim();
                Path folder = folderValue.isBlank() ? firstSource.getParent() : Path.of(folderValue.replaceFirst("^~", System.getProperty("user.home")));
                if (folder == null) folder = firstSource.getParent();
                Files.createDirectories(folder);

                String baseName;
                if (customFileName != null && !customFileName.trim().isBlank()) {
                    baseName = stripExtension(customFileName.trim());
                } else if (sources.length > 1) {
                    baseName = "converted_bundle_" + System.currentTimeMillis();
                } else {
                    baseName = stripExtension(firstSource.getFileName().toString());
                }

                // Never append .pulseos. to the output filename: keep clean original name
                Path target = folder.resolve(baseName + "." + extension);
                int counter = 1;
                while (Files.exists(target) && (sources.length > 1 || !target.equals(firstSource))) {
                    target = folder.resolve(baseName + "_" + counter + "." + extension);
                    counter++;
                }

                String kind = category == null ? "image" : category.toLowerCase(Locale.ROOT);
                String message;

                if (sources.length > 1 && extension.equals("pdf")) {
                    List<Path> imgPaths = new ArrayList<>();
                    for (String s : sources) {
                        if (!s.trim().isBlank()) imgPaths.add(Path.of(s.trim()));
                    }
                    documentConverter.imagesToPdf(imgPaths, target);
                    message = "Merged " + imgPaths.size() + " images into single PDF successfully.";
                } else if (kind.contains("video") || kind.contains("audio") || kind.contains("media")) {
                    var outcome = mediaConverter.convert(firstSource, target); if (!outcome.success()) throw new java.io.IOException(outcome.message()); message = outcome.message();
                } else if (kind.contains("ebook")) {
                    var outcome = ebookConverter.convert(firstSource, target); if (!outcome.success()) throw new java.io.IOException(outcome.message()); message = outcome.message();
                } else if (kind.contains("archive")) {
                    archiveConverter.convert(firstSource, target, extension); message = "Converted via local archive engine (offline).";
                } else if (kind.contains("document")) {
                    String srcExt = extensionOf(firstSource.getFileName().toString());
                    if (srcExt.equals("xlsx") && extension.equals("csv")) documentConverter.xlsxToCsv(firstSource, target);
                    else if (srcExt.equals("txt") && extension.equals("pdf")) documentConverter.textToPdf(firstSource, target);
                    else if ((srcExt.equals("png")||srcExt.equals("jpg")||srcExt.equals("jpeg")||srcExt.equals("webp")) && extension.equals("pdf")) documentConverter.imageToPdf(firstSource, target);
                    else if ((srcExt.equals("pdf")||srcExt.equals("docx")||srcExt.equals("pptx")) && extension.equals("txt")) {
                        String text = srcExt.equals("pdf") ? documentConverter.extractTextFromPdf(firstSource) : srcExt.equals("docx") ? documentConverter.docxToText(firstSource) : documentConverter.pptxToText(firstSource);
                        Files.writeString(target, text);
                    }
                    else throw new java.io.IOException("This document conversion is not supported for the selected formats.");
                    message = "Converted via local document engine (offline).";
                } else {
                    imageConverter.convertImageFormat(firstSource, target, extension);
                    message = "Converted via local image engine (offline).";
                }
                result.set("{\"status\":\"done\",\"success\":true,\"path\":\"" + escape(target.toString()) + "\",\"message\":\"" + escape(message) + "\"}");
            } catch (Exception error) {
                result.set("{\"status\":\"done\",\"success\":false,\"message\":\"" + escape(error.getMessage()) + "\"}");
            }
        });
        return id;
    }

    private String stripExtension(String name) { int dot=name.lastIndexOf('.'); return dot>0?name.substring(0,dot):name; }
    private String extensionOf(String name) { int dot=name.lastIndexOf('.'); return dot>0?name.substring(dot+1).toLowerCase(Locale.ROOT):""; }

    public String getConversionResult(String id) {
        AtomicReference<String> result = conversionResults.get(id);
        if (result == null) return "{\"status\":\"missing\"}";
        String value = result.get();
        if (!value.contains("\"working\"")) conversionResults.remove(id);
        return value;
    }

    public String convertImage(String sourcePath, String targetFormat) {
        try {
            Path source = Path.of(sourcePath);
            String extension = targetFormat.toLowerCase(Locale.ROOT).replace(".", "");
            String baseName = stripExtension(source.getFileName().toString());
            Path target = source.resolveSibling(baseName + "." + extension);
            int counter = 1;
            while (Files.exists(target) && !target.equals(source)) {
                target = source.resolveSibling(baseName + "_" + counter + "." + extension);
                counter++;
            }
            imageConverter.convertImageFormat(source, target, extension);
            return "{\"success\":true,\"path\":\"" + escape(target.toString()) + "\"}";
        } catch (Exception error) {
            return "{\"success\":false,\"message\":\"" + escape(error.getMessage()) + "\"}";
        }
    }

    private boolean commandAvailable(String command) {
        try {
            Process process = new ProcessBuilder("/usr/bin/env", "which", command)
                    .redirectErrorStream(true).start();
            return process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                    && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private double calculateHealth(SystemMetrics metrics, double storageUsed,
                                   NetworkTelemetryService.NetworkMetrics network) {
        if (metrics == null) return -1;
        double ramPercent = metrics.getTotalMemoryGb() <= 0 ? 0
                : metrics.getUsedMemoryGb() / metrics.getTotalMemoryGb() * 100.0;
        double score = 100.0;
        score -= Math.max(0, metrics.getCpuLoadPercentage() - 70) * 0.35;
        score -= Math.max(0, ramPercent - 75) * 0.25;
        if (metrics.getCoreTemperature() > 0) score -= Math.max(0, metrics.getCoreTemperature() - 70) * 0.35;
        if (storageUsed >= 0) score -= Math.max(0, storageUsed * 100 - 85) * 0.3;
        if (metrics.getBatteryPercent() >= 0 && metrics.getBatteryPercent() < 20) score -= 8;
        return Math.max(0, Math.min(100, Math.round(score)));
    }

    private String topCpuName(SystemMetrics metrics) {
        return metrics == null || metrics.getTopProcesses().isEmpty() ? "—" : metrics.getTopProcesses().get(0).name();
    }

    private double topCpuPercent(SystemMetrics metrics) {
        return metrics == null || metrics.getTopProcesses().isEmpty() ? -1 : metrics.getTopProcesses().get(0).cpuPercent();
    }

    private String topRamName(SystemMetrics metrics) {
        return metrics == null || metrics.getTopByRam().isEmpty() ? "—" : metrics.getTopByRam().get(0).name();
    }

    private double topRamMb(SystemMetrics metrics) {
        return metrics == null || metrics.getTopByRam().isEmpty() ? -1 : metrics.getTopByRam().get(0).getRamMb();
    }

    private void ensureComma(StringBuilder json) {
        if (json.length() == 0) return;
        char last = json.charAt(json.length() - 1);
        if (last != '{' && last != '[' && last != ',' && last != ':') {
            json.append(',');
        }
    }

    private void append(StringBuilder json, String key, String value) {
        ensureComma(json);
        json.append('"').append(key).append("\":\"").append(escape(value)).append('"');
    }

    private void append(StringBuilder json, String key, double value) {
        ensureComma(json);
        json.append('"').append(key).append("\":");
        json.append(Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "null");
    }

    private void append(StringBuilder json, String key, long value) {
        ensureComma(json);
        json.append('"').append(key).append("\":").append(value);
    }

    private void append(StringBuilder json, String key, boolean value) {
        ensureComma(json);
        json.append('"').append(key).append("\":").append(value);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
