package scratch;

import com.pulseos.DesktopTelemetryBridge;

public class BenchmarkPerformance {
    public static void main(String[] args) {
        System.out.println("Starting Benchmark...");
        DesktopTelemetryBridge bridge = new DesktopTelemetryBridge(null);
        bridge.start();

        try {
            Thread.sleep(1000); // let initial values settle
        } catch (InterruptedException ignored) {}

        // Test 1: getSnapshotJson() rapid calling (simulate UI polling)
        long t0 = System.nanoTime();
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            String s = bridge.getSnapshotJson();
        }
        long t1 = System.nanoTime();
        double msPerCall = (t1 - t0) / 1_000_000.0 / iterations;
        System.out.printf("getSnapshotJson() average latency: %.4f ms per call\n", msPerCall);

        // Test 2: getSystemWatcherJson() rapid calling
        long w0 = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            String w = bridge.getSystemWatcherJson();
        }
        long w1 = System.nanoTime();
        double msPerWatcher = (w1 - w0) / 1_000_000.0 / 50.0;
        System.out.printf("getSystemWatcherJson() average latency: %.4f ms per call\n", msPerWatcher);

        bridge.stop();
        System.out.println("Benchmark completed successfully.");
        System.exit(0);
    }
}
