package com.pulseos.data;

import java.util.List;

public interface NetworkService {
    ConnectionInfo getConnectionInfo();
    List<ProcessInfo> getProcesses();
    record ConnectionInfo(String network, String ipAddress, String gateway, String dns, double downloadMbps, double uploadMbps, int latencyMs) {}
    record ProcessInfo(String name, int pid, double cpuPercent, double memoryPercent, String status) {}
}
