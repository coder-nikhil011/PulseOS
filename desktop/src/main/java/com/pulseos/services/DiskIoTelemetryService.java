package com.pulseos.services;

import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-performance, low-overhead disk I/O throughput monitoring service.
 * Tracks live read/write speeds in MB/s across system storage controllers.
 */
public final class DiskIoTelemetryService {
    public record DiskSpeed(double readMbPerSec, double writeMbPerSec, long totalReads, long totalWrites) {}

    private final HardwareAbstractionLayer hardware;
    private final AtomicReference<DiskSpeed> latestSpeed = new AtomicReference<>(new DiskSpeed(0, 0, 0, 0));
    private long previousSampleTime = -1;
    private long previousReadBytes = -1;
    private long previousWriteBytes = -1;

    public DiskIoTelemetryService(HardwareAbstractionLayer hardware) {
        this.hardware = hardware;
    }

    public synchronized DiskSpeed sample() {
        try {
            List<HWDiskStore> disks = hardware.getDiskStores();
            if (disks == null || disks.isEmpty()) return latestSpeed.get();

            long totalRead = 0, totalWrite = 0, reads = 0, writes = 0;
            for (HWDiskStore d : disks) {
                try {
                    d.updateAttributes();
                    totalRead += d.getReadBytes();
                    totalWrite += d.getWriteBytes();
                    reads += d.getReads();
                    writes += d.getWrites();
                } catch (Exception ignored) { }
            }

            long now = System.currentTimeMillis();
            if (previousSampleTime > 0 && now > previousSampleTime && previousReadBytes >= 0) {
                double seconds = (now - previousSampleTime) / 1000.0;
                if (seconds > 0.2) {
                    double readMb = Math.max(0, (totalRead - previousReadBytes) / (1024.0 * 1024.0) / seconds);
                    double writeMb = Math.max(0, (totalWrite - previousWriteBytes) / (1024.0 * 1024.0) / seconds);
                    DiskSpeed speed = new DiskSpeed(readMb, writeMb, reads, writes);
                    latestSpeed.set(speed);
                }
            }

            previousSampleTime = now;
            previousReadBytes = totalRead;
            previousWriteBytes = totalWrite;
        } catch (Exception ignored) { }

        return latestSpeed.get();
    }

    public DiskSpeed getLatest() {
        return latestSpeed.get();
    }
}
