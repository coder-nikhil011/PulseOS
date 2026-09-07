package com.pulseos.telemetry;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PowerSource;
import oshi.hardware.Sensors;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Live telemetry engine. Hardware metrics are deliberately isolated from process
 * enumeration: a slow macOS process snapshot must never prevent the dashboard's
 * CPU/RAM/temperature/battery values from rendering.
 */
public class HardwareTelemetryService {
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final Sensors sensors;
    private final OperatingSystem os;

    private volatile int cachedProcessCount = 0;
    private volatile List<SystemMetrics.ProcessInfo> cachedTopByCpu = List.of();
    private volatile List<SystemMetrics.ProcessInfo> cachedTopByRam = List.of();
    private volatile Consumer<List<SystemMetrics.ProcessInfo>> processListener = ignored -> {};

    private ScheduledExecutorService coreExecutor;
    private ScheduledExecutorService processExecutor;
    private long[] previousTicks;
    private volatile boolean running;

    public HardwareTelemetryService() {
        systemInfo = new SystemInfo();
        hardware = systemInfo.getHardware();
        processor = hardware.getProcessor();
        memory = hardware.getMemory();
        sensors = hardware.getSensors();
        os = systemInfo.getOperatingSystem();
        previousTicks = processor.getSystemCpuLoadTicks();
    }

    public void startMonitoring(Consumer<SystemMetrics> metricsListener, long intervalMs) {
        stopMonitoring();
        running = true;

        coreExecutor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("pulseos-core-telemetry").factory());
        processExecutor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("pulseos-process-snapshot").factory());

        // Hardware/UI values start immediately and continue every second.
        coreExecutor.scheduleAtFixedRate(() -> {
            if (!running) return;
            try {
                metricsListener.accept(collectCoreMetrics());
            } catch (Exception e) {
                System.err.println("PulseOS core telemetry error: " + e.getMessage());
            }
        }, 0, Math.max(500, intervalMs), TimeUnit.MILLISECONDS);

        // Process enumeration is intentionally independent. A first macOS snapshot
        // can be slow, but it can no longer block the rest of the dashboard.
        // Populate the router immediately, then refresh independently every 3 seconds.
        processExecutor.execute(this::refreshProcessSnapshotSafely);
        processExecutor.scheduleAtFixedRate(this::refreshProcessSnapshotSafely,
                3, 3, TimeUnit.SECONDS);
    }

    private SystemMetrics collectCoreMetrics() {
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(previousTicks) * 100.0;
        previousTicks = processor.getSystemCpuLoadTicks();

        double cpuTemp = 0;
        try { cpuTemp = sensors.getCpuTemperature(); } catch (Exception ignored) { }
        long threadCount = 0;
        try { threadCount = os.getThreadCount(); } catch (Exception ignored) { }

        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        double usedMemoryGb = (totalMemory - availableMemory) / (1024.0 * 1024.0 * 1024.0);
        double totalMemoryGb = totalMemory / (1024.0 * 1024.0 * 1024.0);

        double clockSpeedGhz = 0;
        try {
            long[] currentFreqs = processor.getCurrentFreq();
            if (currentFreqs != null && currentFreqs.length > 0) {
                clockSpeedGhz = java.util.Arrays.stream(currentFreqs).average().orElse(0) / 1_000_000_000.0;
            }
            if (clockSpeedGhz <= 0) clockSpeedGhz = processor.getMaxFreq() / 1_000_000_000.0;
        } catch (Exception ignored) { }

        int batteryPercent = -1;
        boolean charging = false;
        int batteryHealthPercent = 0;
        int batteryCycleCount = -1;

        // Primary source: OSHI. Some macOS versions/hardware combinations can
        // expose a PowerSource object but report an unusable remaining-capacity
        // value. In that case, fall back to macOS's own `pmset -g batt` output.
        try {
            List<PowerSource> sources = hardware.getPowerSources();
            double bestCharge = -1.0;
            PowerSource best = null;

            for (PowerSource ps : sources) {
                try { ps.updateAttributes(); } catch (Exception ignored) { }
                double charge = ps.getRemainingCapacityPercent();
                if (Double.isFinite(charge) && charge >= 0.0 && charge <= 1.0) {
                    if (charge > bestCharge) {
                        bestCharge = charge;
                        best = ps;
                    }
                }
            }

            if (best != null && bestCharge >= 0.0) {
                batteryPercent = Math.max(0, Math.min(100, (int) Math.round(bestCharge * 100.0)));
                charging = best.isCharging();

                long designCapacity = best.getDesignCapacity();
                long maxCapacity = best.getMaxCapacity();

                // Only trust the OSHI capacity ratio when it is internally sane.
                // Some macOS/Apple Silicon combinations can expose tiny/odd raw
                // capacity values; treating that as 1% health would be misleading.
                if (designCapacity > 0 && maxCapacity > 0) {
                    double ratio = 100.0 * maxCapacity / designCapacity;
                    if (Double.isFinite(ratio) && ratio >= 20.0 && ratio <= 110.0) {
                        batteryHealthPercent = (int) Math.round(Math.min(100.0, ratio));
                    }
                }
                batteryCycleCount = best.getCycleCount();
            }
        } catch (Exception e) {
            System.err.println("PulseOS OSHI battery read failed: " + e.getMessage());
        }

        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
            BatteryFallback fallback = readMacBatteryFallback();
            if (fallback.percent >= 0) {
                // Prefer macOS's own power-management percentage when available.
                // This fixes Macs where the OSHI PowerSource percentage can be 0.
                batteryPercent = fallback.percent;
                charging = fallback.charging;
            }
        }

        return new SystemMetrics(cpuLoad, cpuTemp, threadCount, usedMemoryGb, totalMemoryGb,
                clockSpeedGhz, batteryPercent, charging, batteryHealthPercent, batteryCycleCount,
                cachedProcessCount, cachedTopByCpu, cachedTopByRam);
    }

    private static final class BatteryFallback {
        final int percent;
        final boolean charging;
        BatteryFallback(int percent, boolean charging) {
            this.percent = percent;
            this.charging = charging;
        }
    }

    /**
     * macOS fallback for battery charge state. `pmset -g batt` is backed by
     * macOS's own power-management framework and is useful on Macs where OSHI
     * exposes a PowerSource object but its remaining-capacity field is stale or 0.
     */
    private BatteryFallback readMacBatteryFallback() {
        try {
            Process proc = new ProcessBuilder("/usr/bin/pmset", "-g", "batt")
                    .redirectErrorStream(true)
                    .start();

            String output = new String(
                    proc.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
            proc.waitFor(2, TimeUnit.SECONDS);

            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d{1,3})%")
                    .matcher(output);

            int percent = matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
            percent = percent >= 0 ? Math.max(0, Math.min(100, percent)) : -1;

            String lower = output.toLowerCase(java.util.Locale.ROOT);
            boolean charging = lower.contains("charging;")
                    || lower.contains("charged;")
                    || lower.contains("ac attached;");

            return new BatteryFallback(percent, charging);
        } catch (Exception e) {
            System.err.println("PulseOS macOS battery fallback unavailable: " + e.getMessage());
            return new BatteryFallback(-1, false);
        }
    }

    private void refreshProcessSnapshotSafely() {
        if (!running) return;
        try {
            List<OSProcess> current = os.getProcesses();
            cachedProcessCount = current.size();

            // OSHI's cumulative CPU value is not a current CPU percentage. Compare
            // consecutive snapshots so the dashboard can identify the actual app/service.
            Map<Integer, OSProcess> previous = processSnapshot;
            Map<Integer, OSProcess> now = new HashMap<>();
            for (OSProcess p : current) now.put(p.getProcessID(), p);

            List<SystemMetrics.ProcessInfo> infos = new ArrayList<>(current.size());
            for (OSProcess p : current) {
                double cpu = 0;
                OSProcess old = previous.get(p.getProcessID());
                if (old != null) {
                    try { cpu = Math.max(0, p.getProcessCpuLoadBetweenTicks(old) * 100.0); }
                    catch (Exception ignored) { }
                }
                infos.add(new SystemMetrics.ProcessInfo(
                        p.getName(), cpu, p.getPrivateResidentMemory(), p.getProcessID(), p.getThreadCount()));
            }

            cachedTopByCpu = infos.stream()
                    .sorted(Comparator.comparingDouble(SystemMetrics.ProcessInfo::cpuPercent).reversed())
                    .limit(8).collect(Collectors.toList());
            cachedTopByRam = infos.stream()
                    .sorted(Comparator.comparingDouble(SystemMetrics.ProcessInfo::getRamMb).reversed())
                    .limit(8).collect(Collectors.toList());
            processSnapshot = now;
            processListener.accept(cachedTopByCpu);
        } catch (Exception e) {
            System.err.println("PulseOS process snapshot unavailable: " + e.getMessage());
            // Keep the last good snapshot. Never replace live data with blanks.
        }
    }

    private Map<Integer, OSProcess> processSnapshot = new HashMap<>();

    public void setProcessListener(Consumer<List<SystemMetrics.ProcessInfo>> listener) {
        processListener = listener == null ? ignored -> {} : listener;
    }

    public void stopMonitoring() {
        running = false;
        if (coreExecutor != null) coreExecutor.shutdownNow();
        if (processExecutor != null) processExecutor.shutdownNow();
    }
}
