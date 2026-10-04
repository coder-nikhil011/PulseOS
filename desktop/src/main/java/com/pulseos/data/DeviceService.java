package com.pulseos.data;

public interface DeviceService {
    DeviceInfo getDeviceInfo();
    HealthInfo getHealthInfo();
    PerformanceInfo getPerformanceInfo();

    record DeviceInfo(
            String manufacturer,
            String model,
            String family,
            String image,
            String operatingSystem,
            String uptime,
            String cpu,
            String memory,
            String storage) {}

    record HealthInfo(
            int score,
            String grade,
            int performance,
            int hardware,
            int storage,
            int battery,
            int storageUsed,
            int storageFree,
            int appsStorage,
            int mediaStorage) {}

    record PerformanceInfo(
            int cpuLoad,
            int memoryPressure,
            int temperature,
            int[] cpuHistory,
            int[] memoryHistory,
            int[] temperatureHistory) {}
}
