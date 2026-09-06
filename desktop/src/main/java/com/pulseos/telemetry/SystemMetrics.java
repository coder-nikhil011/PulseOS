package com.pulseos.telemetry;

import java.util.List;

public class SystemMetrics {
    private final double cpuLoadPercentage;
    private final double coreTemperature;
    private final long activeThreadCount;
    private final double usedMemoryGb;
    private final double totalMemoryGb;
    private final double clockSpeedGhz;
    private final int batteryPercent;
    private final boolean batteryCharging;
    private final int batteryHealthPercent;
    private final int batteryCycleCount;
    private final int totalProcessCount;
    private final List<ProcessInfo> topByCpu;
    private final List<ProcessInfo> topByRam;

    public record ProcessInfo(String name, double cpuPercent, long ramBytes, long pid, int threadCount) {
        public double getRamMb() { return ramBytes / (1024.0 * 1024.0); }
    }

    public SystemMetrics(double cpuLoadPercentage, double coreTemperature,
                         long activeThreadCount, double usedMemoryGb, double totalMemoryGb,
                         double clockSpeedGhz, int batteryPercent, boolean batteryCharging,
                         int batteryHealthPercent, int batteryCycleCount,
                         int totalProcessCount, List<ProcessInfo> topByCpu, List<ProcessInfo> topByRam) {
        this.cpuLoadPercentage = cpuLoadPercentage;
        this.coreTemperature = coreTemperature;
        this.activeThreadCount = activeThreadCount;
        this.usedMemoryGb = usedMemoryGb;
        this.totalMemoryGb = totalMemoryGb;
        this.clockSpeedGhz = clockSpeedGhz;
        this.batteryPercent = batteryPercent;
        this.batteryCharging = batteryCharging;
        this.batteryHealthPercent = batteryHealthPercent;
        this.batteryCycleCount = batteryCycleCount;
        this.totalProcessCount = totalProcessCount;
        this.topByCpu = topByCpu;
        this.topByRam = topByRam;
    }

    public double getCpuLoadPercentage() { return cpuLoadPercentage; }
    public double getCoreTemperature() { return coreTemperature; }
    public long getActiveThreadCount() { return activeThreadCount; }
    public double getUsedMemoryGb() { return usedMemoryGb; }
    public double getTotalMemoryGb() { return totalMemoryGb; }
    public double getClockSpeedGhz() { return clockSpeedGhz; }
    public int getBatteryPercent() { return batteryPercent; }
    public boolean isBatteryCharging() { return batteryCharging; }
    public int getBatteryHealthPercent() { return batteryHealthPercent; }
    public int getBatteryCycleCount() { return batteryCycleCount; }
    public int getTotalProcessCount() { return totalProcessCount; }
    public List<ProcessInfo> getTopProcesses() { return topByCpu; }
    public List<ProcessInfo> getTopByRam() { return topByRam; }

    @Override
    public String toString() {
        return String.format("CPU: %.1f%% | Temp: %.1f°C | Threads: %d | RAM: %.2f/%.2f GB | Batt: %d%%",
                cpuLoadPercentage, coreTemperature, activeThreadCount, usedMemoryGb, totalMemoryGb, batteryPercent);
    }
}
