package com.pulseos.services;

import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live system-wide thread and concurrency monitoring service.
 * <p>
 * Tracks total OS threads (user + kernel), per-process thread counts, and
 * detects potential thread leaks when any single process holds an abnormally
 * large number of threads (&gt;= {@link #THREAD_LEAK_THRESHOLD}).
 * <p>
 * Designed for Java 21 virtual-thread environments – the sampling loop
 * should be submitted to a {@code Thread.ofVirtual()} carrier so it adds
 * essentially zero CPU cost.
 */
public final class ThreadConcurrencyService {

    /** Any process with at least this many threads is flagged as a possible leak. */
    public static final int THREAD_LEAK_THRESHOLD = 150;

    /** A snapshot of one process's thread usage. */
    public record ProcessThreadInfo(long pid, String name, int threadCount, boolean possibleLeak) {}

    /** A complete concurrency snapshot of the whole system. */
    public record ConcurrencySnapshot(
            int totalThreads,
            int processCount,
            List<ProcessThreadInfo> top,   // Top 20 by thread count
            boolean leakDetected,
            String leakSummary
    ) {}

    private final OperatingSystem os;
    private final java.util.function.Supplier<List<com.pulseos.telemetry.SystemMetrics.ProcessInfo>> processSupplier;
    private final AtomicReference<ConcurrencySnapshot> latestSnapshot =
            new AtomicReference<>(new ConcurrencySnapshot(0, 0, List.of(), false, ""));

    public ThreadConcurrencyService(OperatingSystem os) {
        this(os, null);
    }

    public ThreadConcurrencyService(OperatingSystem os, java.util.function.Supplier<List<com.pulseos.telemetry.SystemMetrics.ProcessInfo>> processSupplier) {
        this.os = os;
        this.processSupplier = processSupplier;
    }

    /**
     * Performs a lightweight sample with near-zero CPU cost.
     * Total threads are fetched directly via kernel syscall (os.getThreadCount()),
     * and process threads are inspected from cached telemetry without re-querying the OS.
     */
    public synchronized ConcurrencySnapshot sample() {
        try {
            int totalThreads = os.getThreadCount();
            int processCount = os.getProcessCount();

            List<ProcessThreadInfo> infos = new ArrayList<>();
            boolean leakDetected = false;
            StringBuilder leakBuf = new StringBuilder();

            List<com.pulseos.telemetry.SystemMetrics.ProcessInfo> sourceList =
                    processSupplier != null ? processSupplier.get() : null;

            if (sourceList != null && !sourceList.isEmpty()) {
                for (var p : sourceList) {
                    int tc = p.threadCount();
                    boolean leak = tc >= THREAD_LEAK_THRESHOLD;
                    if (leak) {
                        leakDetected = true;
                        if (leakBuf.length() > 0) leakBuf.append(", ");
                        leakBuf.append(p.name()).append(" (").append(tc).append(" threads)");
                    }
                    infos.add(new ProcessThreadInfo(p.pid(), p.name(), tc, leak));
                }
            } else {
                // If no cached supplier, only fetch top processes if needed
                var processes = os.getProcesses(null, Comparator.comparingInt(oshi.software.os.OSProcess::getThreadCount).reversed(), 20);
                if (processes != null) {
                    for (var p : processes) {
                        int tc = p.getThreadCount();
                        boolean leak = tc >= THREAD_LEAK_THRESHOLD;
                        if (leak) {
                            leakDetected = true;
                            if (leakBuf.length() > 0) leakBuf.append(", ");
                            leakBuf.append(p.getName()).append(" (").append(tc).append(" threads)");
                        }
                        infos.add(new ProcessThreadInfo(p.getProcessID(), p.getName(), tc, leak));
                    }
                }
            }

            infos.sort(Comparator.comparingInt(ProcessThreadInfo::threadCount).reversed());
            List<ProcessThreadInfo> top = infos.size() > 20 ? infos.subList(0, 20) : infos;

            ConcurrencySnapshot snapshot = new ConcurrencySnapshot(
                    totalThreads > 0 ? totalThreads : (processCount * 8),
                    processCount > 0 ? processCount : infos.size(),
                    new ArrayList<>(top),
                    leakDetected, leakBuf.toString());
            latestSnapshot.set(snapshot);
            return snapshot;
        } catch (Exception ignored) {
            return latestSnapshot.get();
        }
    }

    /** Returns the most recently computed snapshot without triggering a new sample. */
    public ConcurrencySnapshot getLatest() {
        return latestSnapshot.get();
    }

    /**
     * Builds a compact JSON string for the telemetry bridge to forward to JS.
     *
     * <pre>{@code
     * {
     *   "totalThreads": 2347,
     *   "processCount": 312,
     *   "leakDetected": false,
     *   "leakSummary": "",
     *   "top": [{"pid":1,"name":"kernel_task","threads":512,"leak":false}, ...]
     * }
     * }</pre>
     */
    public String toJson() {
        ConcurrencySnapshot s = latestSnapshot.get();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalThreads\":").append(s.totalThreads());
        sb.append(",\"processCount\":").append(s.processCount());
        sb.append(",\"leakDetected\":").append(s.leakDetected());
        sb.append(",\"leakSummary\":\"").append(escape(s.leakSummary())).append('"');
        sb.append(",\"top\":[");
        boolean first = true;
        for (ProcessThreadInfo p : s.top()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"pid\":").append(p.pid())
              .append(",\"name\":\"").append(escape(p.name())).append('"')
              .append(",\"threads\":").append(p.threadCount())
              .append(",\"leak\":").append(p.possibleLeak()).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
