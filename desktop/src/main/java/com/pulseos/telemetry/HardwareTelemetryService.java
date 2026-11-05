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
    private volatile List<SystemMetrics.ProcessInfo> cachedAllProcesses = List.of();
    private volatile Consumer<List<SystemMetrics.ProcessInfo>> processListener = ignored -> {};
    private volatile Consumer<SystemMetrics> metricsListener = ignored -> {};

    private volatile long lastMacBatteryFallbackTime = 0;
    private volatile BatteryFallback cachedMacBatteryFallback = new BatteryFallback(-1, false, -1, -1);
    private volatile boolean macBatteryProbeRunning = false;

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
        this.metricsListener = metricsListener == null ? ignored -> {} : metricsListener;

        coreExecutor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("pulseos-core-telemetry").factory());
        processExecutor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("pulseos-process-snapshot").factory());

        // Hardware/UI values start immediately and continue every intervalMs.
        coreExecutor.scheduleAtFixedRate(() -> {
            if (!running) return;
            try {
                this.metricsListener.accept(collectCoreMetrics());
            } catch (Exception e) {
                System.err.println("PulseOS core telemetry error: " + e.getMessage());
            }
        }, 0, Math.max(1000, intervalMs), TimeUnit.MILLISECONDS);

        // Process enumeration is intentionally independent.
        // Refresh in background every 25 seconds (saves massive CPU)
        processExecutor.execute(this::refreshProcessSnapshotSafely);
        processExecutor.scheduleAtFixedRate(this::refreshProcessSnapshotSafely,
                5, 25, TimeUnit.SECONDS);
    }

    private SystemMetrics collectCoreMetrics() {
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(previousTicks) * 100.0;
        previousTicks = processor.getSystemCpuLoadTicks();

        double cpuTemp = 0;
        try { cpuTemp = sensors.getCpuTemperature(); } catch (Exception ignored) { }
        int[] fanSpeeds = new int[0];
        try { fanSpeeds = sensors.getFanSpeeds(); } catch (Exception ignored) { }
        double cpuVoltage = 0;
        try { cpuVoltage = sensors.getCpuVoltage(); } catch (Exception ignored) { }
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
        int batteryHealthPercent = -1;
        int batteryCycleCount = -1;

        // Primary source: OSHI. Some macOS versions/hardware combinations can
        // expose a PowerSource object but report an unusable remaining-capacity
        // value. In that case, fall back to macOS's own `pmset -g batt` / `ioreg` output.
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
                if (designCapacity > 0 && maxCapacity > 0) {
                    double ratio = 100.0 * maxCapacity / designCapacity;
                    if (Double.isFinite(ratio) && ratio >= 20.0 && ratio <= 110.0) {
                        batteryHealthPercent = (int) Math.round(Math.min(100.0, ratio));
                    }
                } else if (maxCapacity > 0 && maxCapacity <= 100) {
                    batteryHealthPercent = (int) maxCapacity;
                }
                batteryCycleCount = best.getCycleCount();
            }
        } catch (Exception e) {
            System.err.println("PulseOS OSHI battery read failed: " + e.getMessage());
        }

        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
            long now = System.currentTimeMillis();
            // Only probe ioreg/pmset if OSHI battery reading is missing or periodically (every 30s).
            // Spawning sub-processes every second causes massive CPU spikes.
            if ((batteryPercent < 0 || now - lastMacBatteryFallbackTime > 30000) && !macBatteryProbeRunning) {
                lastMacBatteryFallbackTime = now;
                macBatteryProbeRunning = true;
                Thread.ofVirtual().name("pulseos-mac-battery").start(() -> {
                    try {
                        cachedMacBatteryFallback = readMacBatteryFallback();
                    } catch (Exception ignored) {
                    } finally {
                        macBatteryProbeRunning = false;
                    }
                });
            }
            if (cachedMacBatteryFallback.percent >= 0) {
                batteryPercent = cachedMacBatteryFallback.percent;
                charging = cachedMacBatteryFallback.charging;
            }
            if (cachedMacBatteryFallback.healthPercent > 0) {
                batteryHealthPercent = cachedMacBatteryFallback.healthPercent;
            }
            if (cachedMacBatteryFallback.cycleCount >= 0) {
                batteryCycleCount = cachedMacBatteryFallback.cycleCount;
            }
        }

        return new SystemMetrics(cpuLoad, cpuTemp, threadCount, usedMemoryGb, totalMemoryGb,
                clockSpeedGhz, batteryPercent, charging, batteryHealthPercent, batteryCycleCount,
            cachedProcessCount, cachedTopByCpu, cachedTopByRam, fanSpeeds, cpuVoltage);
    }

    private static final class BatteryFallback {
        final int percent;
        final boolean charging;
        final int healthPercent;
        final int cycleCount;
        BatteryFallback(int percent, boolean charging, int healthPercent, int cycleCount) {
            this.percent = percent;
            this.charging = charging;
            this.healthPercent = healthPercent;
            this.cycleCount = cycleCount;
        }
    }

    /**
     * macOS fallback for battery charge, health and cycle telemetry.
     * Uses ioreg for AppleSmartBattery directly, with pmset as backup.
     */
    private BatteryFallback readMacBatteryFallback() {
        try {
            Process proc = new ProcessBuilder("/usr/sbin/ioreg", "-rn", "AppleSmartBattery")
                    .redirectErrorStream(true)
                    .start();

            boolean finished = proc.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
            } else {
                String out = new String(proc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (out.contains("AppleSmartBattery")) {
                    var mMax = java.util.regex.Pattern.compile("\"MaxCapacity\"\\s*=\\s*(\\d+)").matcher(out);
                    var mDesign = java.util.regex.Pattern.compile("\"DesignCapacity\"\\s*=\\s*(\\d+)").matcher(out);
                    var mNominal = java.util.regex.Pattern.compile("\"NominalChargeCapacity\"\\s*=\\s*(\\d+)").matcher(out);
                    var mCycles = java.util.regex.Pattern.compile("\"CycleCount\"\\s*=\\s*(\\d+)").matcher(out);
                    var mCurrent = java.util.regex.Pattern.compile("\"CurrentCapacity\"\\s*=\\s*(\\d+)").matcher(out);
                    var mCharging = java.util.regex.Pattern.compile("\"IsCharging\"\\s*=\\s*(Yes|No|true|false)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(out);

                    int max = mMax.find() ? Integer.parseInt(mMax.group(1)) : -1;
                    int design = mDesign.find() ? Integer.parseInt(mDesign.group(1)) : -1;
                    int nominal = mNominal.find() ? Integer.parseInt(mNominal.group(1)) : -1;
                    int cycles = mCycles.find() ? Integer.parseInt(mCycles.group(1)) : -1;
                    int current = mCurrent.find() ? Integer.parseInt(mCurrent.group(1)) : -1;
                    boolean charging = mCharging.find() && (mCharging.group(1).equalsIgnoreCase("yes") || mCharging.group(1).equalsIgnoreCase("true"));

                    int health = -1;
                    if (design > 0 && nominal > 0) {
                        health = (int) Math.round(Math.min(100.0, 100.0 * nominal / design));
                    } else if (max > 0 && max <= 100) {
                        health = max;
                    }

                    if (current >= 0) {
                        return new BatteryFallback(current, charging, health, cycles);
                    }
                }
            }
        } catch (Exception ignored) { }

        try {
            Process proc = new ProcessBuilder("/usr/bin/pmset", "-g", "batt")
                    .redirectErrorStream(true)
                    .start();

            boolean finished = proc.waitFor(1200, TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return new BatteryFallback(-1, false, -1, -1);
            }
            String output = new String(proc.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);

            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d{1,3})%")
                    .matcher(output);

            int percent = matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
            percent = percent >= 0 ? Math.max(0, Math.min(100, percent)) : -1;

            String lower = output.toLowerCase(java.util.Locale.ROOT);
            boolean charging = lower.contains("charging;")
                    || lower.contains("charged;")
                    || lower.contains("ac attached;");

            return new BatteryFallback(percent, charging, -1, -1);
        } catch (Exception e) {
            System.err.println("PulseOS macOS battery fallback unavailable: " + e.getMessage());
            return new BatteryFallback(-1, false, -1, -1);
        }
    }

    private void refreshProcessSnapshotSafely() {
        if (!running) return;
        try {
            List<OSProcess> current = os.getProcesses();
            // macOS privacy/adaptor failures can occasionally return an empty OSHI list.
            // Fall back to Java's own process API so the UI never reports a false 0.
            if (current == null || current.isEmpty()) {
                refreshProcessSnapshotFromProcessHandle();
                return;
            }
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

            // Sort and cap to top 100 drainers to eliminate memory bloat
            infos.sort((a, b) -> {
                int c = Double.compare(b.cpuPercent(), a.cpuPercent());
                if (c != 0) return c;
                return Double.compare(b.getRamMb(), a.getRamMb());
            });
            List<SystemMetrics.ProcessInfo> cappedInfos = infos.size() > 100
                    ? new ArrayList<>(infos.subList(0, 100))
                    : infos;

            cachedAllProcesses = List.copyOf(cappedInfos);
            cachedTopByCpu = cappedInfos.stream()
                    .sorted(Comparator.comparingDouble(SystemMetrics.ProcessInfo::cpuPercent).reversed())
                    .limit(8).collect(Collectors.toList());
            cachedTopByRam = cappedInfos.stream()
                    .sorted(Comparator.comparingDouble(SystemMetrics.ProcessInfo::getRamMb).reversed())
                    .limit(8).collect(Collectors.toList());
            processSnapshot = now;
            processListener.accept(cachedTopByCpu);
        } catch (Exception e) {
            System.err.println("PulseOS process snapshot unavailable: " + e.getMessage());
            // Keep the last good snapshot. Never replace live data with blanks.
        }
    }


    private void refreshProcessSnapshotFromProcessHandle() {
        try {
            List<SystemMetrics.ProcessInfo> infos = java.lang.ProcessHandle.allProcesses()
                    .map(h -> {
                        try {
                            var info = h.info();
                            String name = info.command().map(path -> java.nio.file.Paths.get(path).getFileName()).map(Object::toString)
                                    .orElse(info.commandLine().orElse("process"));
                            return new SystemMetrics.ProcessInfo(name, 0.0, 0L, h.pid(), 0);
                        } catch (Exception e) { return null; }
                    })
                    .filter(java.util.Objects::nonNull)
                    .limit(500)
                    .toList();
            cachedAllProcesses = List.copyOf(infos);
            cachedProcessCount = infos.size();
            cachedTopByCpu = infos.stream().limit(8).toList();
            cachedTopByRam = infos.stream().limit(8).toList();
            processListener.accept(cachedTopByCpu);
        } catch (Exception e) {
            System.err.println("PulseOS ProcessHandle fallback unavailable: " + e.getMessage());
        }
    }
    private Map<Integer, OSProcess> processSnapshot = new HashMap<>();

    public List<SystemMetrics.ProcessInfo> getAllProcesses() { return cachedAllProcesses; }

    public void requestProcessScan() { if (running) processExecutor.execute(this::refreshProcessSnapshotSafely); }

    public void setProcessListener(Consumer<List<SystemMetrics.ProcessInfo>> listener) {
        processListener = listener == null ? ignored -> {} : listener;
    }

    public void stopMonitoring() {
        running = false;
        metricsListener = ignored -> {};
        if (coreExecutor != null) coreExecutor.shutdownNow();
        if (processExecutor != null) processExecutor.shutdownNow();
    }
}
