package com.pulseos.telemetry;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Small cross-platform network probe that sends no user data. */
public final class NetworkTelemetryService {
    public record NetworkMetrics(boolean dnsAvailable, boolean internetReachable,
                                 long latencyMs, String resolvedAddress, Instant capturedAt) {}

    private ScheduledExecutorService executor;
    private volatile boolean running;
    private volatile Consumer<NetworkMetrics> listener = ignored -> {};

    public void start(Consumer<NetworkMetrics> listener, long intervalSeconds) {
        stop();
        running = true;
        this.listener = listener == null ? ignored -> {} : listener;
        executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("pulseos-network-telemetry").factory());
        executor.scheduleAtFixedRate(() -> {
            if (!running) return;
            try {
                listener.accept(probe());
            } catch (Exception error) {
                listener.accept(new NetworkMetrics(false, false, -1, "Unavailable", Instant.now()));
            }
        }, 0, Math.max(5, intervalSeconds), TimeUnit.SECONDS);
    }

    private NetworkMetrics probe() throws Exception {
        InetAddress dnsResult = InetAddress.getByName("example.com");
        boolean dnsAvailable = dnsResult.getHostAddress() != null;
        InetAddress probe = InetAddress.getByName("1.1.1.1");
        Instant started = Instant.now();
        boolean reachable = probe.isReachable(1800);
        long latency = reachable ? Duration.between(started, Instant.now()).toMillis() : -1;
        return new NetworkMetrics(dnsAvailable, reachable, latency, dnsResult.getHostAddress(), Instant.now());
    }

    public void probeNow() {
        if (running && executor != null) executor.execute(() -> {
            if (!running) return;
            try { listener.accept(probe()); }
            catch (Exception error) { listener.accept(new NetworkMetrics(false, false, -1, "Unavailable", Instant.now())); }
        });
    }

    public void stop() {
        running = false;
        if (executor != null) executor.shutdownNow();
    }
}