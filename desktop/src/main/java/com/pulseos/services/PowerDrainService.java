package com.pulseos.services;

import oshi.hardware.PowerSource;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live battery wattage and power-drain estimator.
 * <p>
 * Reads the system's power-source telemetry (OSHI {@link PowerSource}) and
 * exposes:
 * <ul>
 *   <li>Live power draw in Watts (e.g. "5.2 W").</li>
 *   <li>Estimated battery time remaining (e.g. "6h 45m").</li>
 *   <li>Design vs. actual capacity health.</li>
 *   <li>Charge cycle count.</li>
 * </ul>
 * When OSHI cannot measure a specific field the service returns −1 (numeric)
 * or an empty string, allowing the UI to hide missing values rather than
 * showing stale / fake numbers.
 */
public final class PowerDrainService {

    /** A single power-state snapshot. */
    public record PowerSnapshot(
            double powerWatts,          // Live power draw (W); −1 if unavailable
            double amperage,            // Live amperage (A); −1 if unavailable
            double voltage,             // Battery voltage (V); −1 if unavailable
            double batteryPercent,      // 0-100; −1 if unavailable
            boolean charging,
            boolean pluggedIn,
            double remainingHours,      // Estimated hours left; −1 if unknown/charging
            double designCapacityWh,    // Design capacity in Wh; −1 if unavailable
            double currentCapacityWh,   // Current max capacity in Wh; −1 if unavailable
            int cycleCount,             // Charge cycles; −1 if unavailable
            String formattedRemaining   // "Xh Ym" or "Charging" or "Calculating…"
    ) {}

    private final oshi.hardware.HardwareAbstractionLayer hardware;
    private final AtomicReference<PowerSnapshot> latest = new AtomicReference<>(emptySnapshot());

    public PowerDrainService(oshi.hardware.HardwareAbstractionLayer hardware) {
        this.hardware = hardware;
    }

    private static PowerSnapshot emptySnapshot() {
        return new PowerSnapshot(-1, -1, -1, -1, false, false, -1, -1, -1, -1, "");
    }

    /**
     * Samples hardware power sources and updates the cached snapshot.
     * Safe to call from a background thread every 2–5 seconds.
     */
    public synchronized PowerSnapshot sample() {
        try {
            List<PowerSource> sources = hardware.getPowerSources();
            if (sources == null || sources.isEmpty()) {
                latest.set(emptySnapshot());
                return latest.get();
            }

            // Use the first non-trivially-named source (primary battery)
            PowerSource src = sources.stream()
                    .filter(s -> s.getName() != null && !s.getName().isBlank()
                            && !s.getName().equalsIgnoreCase("Unknown"))
                    .findFirst().orElse(sources.get(0));

            // Update attributes for fresh readings
            try { src.updateAttributes(); } catch (Exception ignored) { }

            double watts    = src.getPowerUsageRate();       // may be 0 or NaN on some HW
            double amps     = src.getAmperage();
            double volts    = src.getVoltage();
            double pct      = src.getRemainingCapacityPercent() * 100.0;
            boolean charg   = src.isCharging();
            boolean plugged = src.isPowerOnLine();

            // Capacity in Wh
            double designWh  = src.getDesignCapacity() / 1000.0;   // mWh → Wh
            double currentWh = src.getCurrentCapacity() / 1000.0;

            // Cycle count
            int cycles = (int) src.getCycleCount();

            // Time remaining
            double remainHours = src.getTimeRemainingEstimated() / 3600.0; // seconds → hours

            // If OSHI watts reading is unavailable, estimate from amps × volts
            if (!Double.isFinite(watts) || watts <= 0) {
                if (Double.isFinite(amps) && amps > 0 && Double.isFinite(volts) && volts > 0) {
                    watts = Math.abs(amps) * volts;
                } else {
                    watts = -1;
                }
            } else {
                watts = Math.abs(watts);
            }

            if (!Double.isFinite(pct) || pct < 0) pct = -1;
            if (!Double.isFinite(amps) || amps == 0) amps = -1;
            if (!Double.isFinite(volts) || volts <= 0) volts = -1;
            if (!Double.isFinite(designWh) || designWh <= 0) designWh = -1;
            if (!Double.isFinite(currentWh) || currentWh <= 0) currentWh = -1;
            if (!Double.isFinite(remainHours) || remainHours < 0) remainHours = -1;

            // Format remaining time as "Xh Ym" / "Charging" / "Calculating…"
            String formatted;
            if (charg || plugged) {
                formatted = "Charging";
            } else if (remainHours < 0) {
                formatted = "Calculating…";
            } else {
                int h = (int) remainHours;
                int m = (int) Math.round((remainHours - h) * 60);
                formatted = h > 0 ? h + "h " + m + "m" : m + "m";
            }

            PowerSnapshot snap = new PowerSnapshot(
                    watts, Double.isFinite(amps) && amps > 0 ? amps : -1,
                    volts, pct, charg, plugged,
                    remainHours, designWh, currentWh, cycles < 0 ? -1 : cycles,
                    formatted);
            latest.set(snap);
            return snap;
        } catch (Exception ignored) {
            return latest.get();
        }
    }

    /** Returns the most recently sampled snapshot without triggering hardware I/O. */
    public PowerSnapshot getLatest() {
        return latest.get();
    }

    /**
     * Returns a compact JSON string for the telemetry bridge.
     *
     * <pre>{@code
     * {
     *   "powerWatts": 5.2,
     *   "amperage": 1.44,
     *   "voltage": 12.6,
     *   "batteryPercent": 78.0,
     *   "charging": false,
     *   "pluggedIn": false,
     *   "remainingHours": 4.5,
     *   "formattedRemaining": "4h 30m",
     *   "designCapacityWh": 58.0,
     *   "currentCapacityWh": 51.2,
     *   "cycleCount": 243
     * }
     * }</pre>
     */
    public String toJson() {
        PowerSnapshot s = latest.get();
        return String.format(Locale.ROOT,
                "{\"powerWatts\":%.2f,\"amperage\":%.3f,\"voltage\":%.2f," +
                "\"batteryPercent\":%.1f,\"charging\":%b,\"pluggedIn\":%b," +
                "\"remainingHours\":%.2f,\"formattedRemaining\":\"%s\"," +
                "\"designCapacityWh\":%.1f,\"currentCapacityWh\":%.1f,\"cycleCount\":%d}",
                s.powerWatts(), s.amperage(), s.voltage(),
                s.batteryPercent(), s.charging(), s.pluggedIn(),
                s.remainingHours(), escape(s.formattedRemaining()),
                s.designCapacityWh(), s.currentCapacityWh(), s.cycleCount());
    }

    private static String escape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
