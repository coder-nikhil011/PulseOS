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
import java.util.function.Consumer;

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

        int batteryPercent = 0;
        boolean charging = false;
        int batteryHealthPercent = 0;
        int batteryCycleCount = -1;
        try {
            List<PowerSource> sources = hardware.getPowerSources();
            if (!sources.isEmpty()) {
                PowerSource ps = sources.get(0);
                batteryPercent = (int) Math.round(ps.getRemainingCapacityPercent() * 100.0);
                charging = ps.isCharging();
                if (ps.getDesignCapacity() > 0 && ps.getMaxCapacity() > 0) {
                    batteryHealthPercent = (int) Math.round(100.0 * ps.getMaxCapacity() / ps.getDesignCapacity());
                }
                batteryCycleCount = ps.getCycleCount();
            }
        } catch (Exception ignored) { }

        return new SystemMetrics(cpuLoad, cpuTemp, threadCount, usedMemoryGb, totalMemoryGb,
                clockSpeedGhz, batteryPercent, charging, batteryHealthPercent, batteryCycleCount,
                cachedProcessCount, cachedTopByCpu, cachedTopByRam);
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
                        p.getName(), cpu, p.getResidentSetSize(), p.getProcessID(), p.getThreadCount()));
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
