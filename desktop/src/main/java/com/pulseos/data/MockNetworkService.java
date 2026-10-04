package com.pulseos.data;

import java.util.List;

public final class MockNetworkService implements NetworkService {
    @Override public ConnectionInfo getConnectionInfo() { return new ConnectionInfo("Wi-Fi (Heme_5G)", "192.168.1.24", "192.168.1.1", "8.8.8.8", 48.6, 12.3, 18); }
    @Override public List<ProcessInfo> getProcesses() { return List.of(
            new ProcessInfo("NetworkManager", 597, 2.1, 13.8, "Running"),
            new ProcessInfo("wpa_supplicant", 738, 1.3, 6.5, "Running"),
            new ProcessInfo("dnsmasq", 821, .8, 4.2, "Running"),
            new ProcessInfo("firewalld", 642, .6, 3.1, "Running"),
            new ProcessInfo("nginx", 1032, .4, 2.7, "Running")); }
}
