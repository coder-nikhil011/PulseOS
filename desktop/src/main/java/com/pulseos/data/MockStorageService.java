package com.pulseos.data;

import java.util.List;

public final class MockStorageService implements StorageService {
    @Override public UsageInfo getUsage() { return new UsageInfo(476, 296, 180, 62); }
    @Override public List<LargeFile> getLargeFiles() { return List.of(
            new LargeFile("ubuntu-27.04-desktop-amd64.iso", "4.7 GB", "12 days", "Likely Stale"),
            new LargeFile("video-editing-project.tmp", "1.2 GB", "7 days", "Safe"),
            new LargeFile("game-installers.zip", "892 MB", "20 days", "Safe"),
            new LargeFile("system_log_2025-06-08.log", "642 MB", "2 days", "Safe")); }
}
