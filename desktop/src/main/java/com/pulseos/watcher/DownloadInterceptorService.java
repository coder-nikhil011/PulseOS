package com.pulseos.watcher;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadInterceptorService {

    private final Path watchPath;
    private WatchService watchService;
    private ExecutorService executor;
    private volatile boolean running = false;
    private final Map<Path, Long> recentDispatches = new ConcurrentHashMap<>();

    public DownloadInterceptorService(Path watchPath) {
        this.watchPath = watchPath;
    }

    public void startIntercepting(FileRouteListener listener) {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.watchPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            this.running = true;

            // Running WatchService loop inside a lightweight Java 21 Virtual Thread
            this.executor = Executors.newSingleThreadExecutor(
                    Thread.ofVirtual().name("pulseos-file-watcher").factory()
            );

            executor.submit(() -> pollEvents(listener));
            System.out.println("PulseOS Watcher active on: " + watchPath.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Failed to initialize WatchService: " + e.getMessage());
        }
    }

    private void pollEvents(FileRouteListener listener) {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take(); // Blocks until an event occurs
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path fileName = ev.context();
                Path fullPath = watchPath.resolve(fileName);

                // Filter out browser temporary download extensions
                String fileNameStr = fileName.toString();
                if (fileNameStr.endsWith(".crdownload") || fileNameStr.endsWith(".tmp") || fileNameStr.startsWith(".")) {
                    continue;
                }

                // Ignore duplicate CREATE/MODIFY bursts for the same file.
                long now = System.currentTimeMillis();
                Long previousDispatch = recentDispatches.get(fullPath);
                if (previousDispatch != null && now - previousDispatch < 4000) continue;

                // Extract file extension
                String extension = getFileExtension(fileNameStr);
                if (!extension.isEmpty()) {
                    recentDispatches.put(fullPath, now);
                    listener.onFileDetected(fullPath, extension.toLowerCase());
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                break; // Directory key is no longer valid
            }
        }
    }

    private String getFileExtension(String fileName) {
        int lastIndex = fileName.lastIndexOf('.');
        if (lastIndex > 0 && lastIndex < fileName.length() - 1) {
            return fileName.substring(lastIndex + 1);
        }
        return "";
    }

    public void stopIntercepting() {
        this.running = false;
        recentDispatches.clear();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
    }
}