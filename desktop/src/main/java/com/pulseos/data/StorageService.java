package com.pulseos.data;

import java.util.List;

public interface StorageService {
    UsageInfo getUsage();
    List<LargeFile> getLargeFiles();
    record UsageInfo(long totalGb, long usedGb, long freeGb, int usedPercent) {}
    record LargeFile(String name, String size, String age, String safety) {}
}
