package com.pulseos.diagnostics;

import com.pulseos.telemetry.SystemMetrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Converts repeated telemetry signals into cautious, explainable problem records. */
public final class HardwareProblemDetector {
    private HardwareProblemDetector() {}

    public static List<HardwareProblem> detect(SystemMetrics metrics, double storageUsed,
                                                Deque<Double> temperatureHistory,
                                                int networkFailures,
                                                Instant now) {
        List<HardwareProblem> problems = new ArrayList<>();
        int hotSamples = (int) temperatureHistory.stream().filter(value -> value > 78).count();
        if (metrics.getCoreTemperature() > 78) {
            String state = hotSamples >= 3 ? "Active" : "Observed";
            int confidence = Math.min(95, 60 + hotSamples * 5);
            problems.add(new HardwareProblem(
                    "thermal-temperature-high", "Cooling / Thermal", "Hardware signal",
                    "Temperature is above the configured safe range", state, "Warning", confidence,
                    List.of(String.format("Current CPU temperature: %.1f°C", metrics.getCoreTemperature()),
                            hotSamples + " elevated samples in the recent history",
                            "CPU load: " + String.format("%.1f%%", metrics.getCpuLoadPercentage())),
                    now.minusSeconds(Math.max(0, hotSamples - 1)), now, hotSamples,
                    "Temperature sensor is exposed by the operating system; fan and GPU sensors may be unavailable."));
        }

        int[] fanSpeeds = metrics.getFanSpeeds();
        if (metrics.getCoreTemperature() > 78 && fanSpeeds.length > 0
                && java.util.Arrays.stream(fanSpeeds).allMatch(speed -> speed <= 0)) {
            problems.add(new HardwareProblem(
                    "cooling-fan-no-response", "Fan / Cooling", "Hardware correlation",
                    "Fan response is low while temperature is elevated", "Suspected", "Warning", 82,
                    List.of(String.format("CPU temperature: %.1f°C", metrics.getCoreTemperature()),
                            "Fan RPM: " + java.util.Arrays.toString(fanSpeeds),
                            "Clock/load correlation requires continued samples"),
                    now, now, 1,
                    "A zero RPM reading can be normal at idle; this signal is reported only together with elevated temperature."));
        }

        if (metrics.getBatteryHealthPercent() >= 20 && metrics.getBatteryHealthPercent() < 80) {
            problems.add(new HardwareProblem(
                    "battery-capacity-low", "Battery", "Hardware signal",
                    "Battery capacity is below the healthy range", "Suspected", "Warning", 78,
                    List.of("Reported maximum capacity: " + metrics.getBatteryHealthPercent() + "%",
                            "Charge cycles: " + (metrics.getBatteryCycleCount() >= 0 ? metrics.getBatteryCycleCount() : "unavailable")),
                    now, now, 1,
                    "Capacity comes from OSHI/OS power data; this does not confirm physical cell damage."));
        }

        if (storageUsed > .88) {
            problems.add(new HardwareProblem(
                    "storage-capacity-pressure", "Storage", "System condition",
                    "Storage capacity is under pressure", "Active", "Warning", 90,
                    List.of(String.format("Used space: %.1f%%", storageUsed * 100),
                            "Storage health is indirect; SMART data is not currently exposed by this adapter."),
                    now, now, 1,
                    "Full storage can cause failed writes without proving SSD/HDD hardware degradation."));
        }

        if (networkFailures >= 3) {
            problems.add(new HardwareProblem(
                    "network-reachability-repeat", "Network", "System signal",
                    "Network reachability has failed repeatedly", "Suspected", "Warning", Math.min(95, 60 + networkFailures * 5),
                    List.of(networkFailures + " consecutive failed probes",
                            "DNS or internet reachability did not recover"),
                    now.minusSeconds((long) (networkFailures - 1) * 15), now, networkFailures,
                    "This cannot distinguish Wi-Fi, router, ISP or DNS faults without platform-specific adapters."));
        }
        return problems;
    }
}