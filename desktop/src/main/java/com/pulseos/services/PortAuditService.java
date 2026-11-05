package com.pulseos.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Open listening ports and local network sockets security auditing service.
 * Discovers active listening servers, daemons, and background services.
 */
public final class PortAuditService {
    public record ListeningPort(int port, String processName, long pid, String protocol, String address) {}

    public List<ListeningPort> scanListeningPorts() {
        List<ListeningPort> results = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (os.contains("mac") || os.contains("linux")) {
            try {
                ProcessBuilder pb = new ProcessBuilder("/usr/sbin/lsof", "-iTCP", "-sTCP:LISTEN", "-P", "-n");
                Process process = pb.start();
                boolean finished = process.waitFor(2, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        // Skip header
                        reader.readLine();
                        Set<Integer> seenPorts = new HashSet<>();
                        while ((line = reader.readLine()) != null) {
                            String[] parts = line.trim().split("\\s+");
                            if (parts.length >= 9) {
                                String cmd = parts[0];
                                long pid = 0;
                                try { pid = Long.parseLong(parts[1]); } catch (Exception ignored) { }
                                String nameCol = parts[8]; // e.g. *:5000 or 127.0.0.1:49227
                                int colonIdx = nameCol.lastIndexOf(':');
                                if (colonIdx >= 0) {
                                    try {
                                        int port = Integer.parseInt(nameCol.substring(colonIdx + 1));
                                        if (seenPorts.add(port)) {
                                            String addr = nameCol.substring(0, colonIdx);
                                            results.add(new ListeningPort(port, cmd, pid, "TCP", addr));
                                        }
                                    } catch (Exception ignored) { }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) { }
        } else if (os.contains("win")) {
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "netstat -ano -p tcp | findstr LISTENING");
                Process process = pb.start();
                boolean finished = process.waitFor(2, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        Pattern pat = Pattern.compile("TCP\\s+([^:]+):(\\d+)\\s+[^\\s]+\\s+LISTENING\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
                        Set<Integer> seenPorts = new HashSet<>();
                        while ((line = reader.readLine()) != null) {
                            Matcher m = pat.matcher(line.trim());
                            if (m.find()) {
                                String addr = m.group(1);
                                int port = Integer.parseInt(m.group(2));
                                long pid = Long.parseLong(m.group(3));
                                if (seenPorts.add(port)) {
                                    results.add(new ListeningPort(port, "Process (" + pid + ")", pid, "TCP", addr));
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) { }
        }

        results.sort(Comparator.comparingInt(ListeningPort::port));
        return results;
    }
}
