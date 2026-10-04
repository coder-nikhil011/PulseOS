package com.pulseos.data;

public final class MockDeviceService implements DeviceService {
    private static final DeviceInfo DEVICE = new DeviceInfo(
            "Apple", "MacBook Pro", "MacBook Pro", "/devices/macbook-pro.svg",
            "macOS 14.6", "2d 1h 15m", "Apple M1 (8 cores)",
            "16 GB (LPDDR4)", "512 GB SSD");

    private static final HealthInfo HEALTH = new HealthInfo(
            91, "EXCELLENT", 90, 83, 100, 98, 68, 156, 124, 96);

    private static final PerformanceInfo PERFORMANCE = new PerformanceInfo(
            24, 62, 62,
            new int[]{24, 31, 27, 34, 29, 32, 26, 30, 28, 31, 29, 30},
            new int[]{50, 51, 50, 52, 51, 53, 52, 54, 53, 55, 54, 55},
            new int[]{50, 51, 50, 52, 51, 53, 52, 54, 53, 55, 54, 56});

    @Override public DeviceInfo getDeviceInfo() { return DEVICE; }
    @Override public HealthInfo getHealthInfo() { return HEALTH; }
    @Override public PerformanceInfo getPerformanceInfo() { return PERFORMANCE; }
}
