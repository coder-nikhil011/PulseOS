package com.pulseos.healer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class StorageHealerService {

    private final BuildArtifactScanner scanner;
    private final ExecutorService executor;

    public StorageHealerService() {
        this.scanner = new BuildArtifactScanner();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Executes non-blocking async storage scan for heavy build artifacts
     */
    public CompletableFuture<List<BuildArtifactScanner.ArtifactDetails>> scanAsync(Path searchPath) {
        return CompletableFuture.supplyAsync(() -> scanner.scanDirectory(searchPath), executor);
    }


    /** Fast dashboard scan: only user-facing folders, never the whole macOS home/Library tree. */
    public CompletableFuture<List<BuildArtifactScanner.ArtifactDetails>> analyzeCommonLocations(Path home) {
        List<Path> roots = List.of("Downloads", "Desktop", "Documents", "Pictures", "Projects", "Developer")
                .stream().map(home::resolve).filter(Files::isDirectory).toList();
        if (roots.isEmpty()) return CompletableFuture.completedFuture(List.of());

        List<CompletableFuture<List<BuildArtifactScanner.ArtifactDetails>>> jobs = roots.stream()
                .map(this::scanAsync)
                .map(job -> job.exceptionally(error -> {
                    System.err.println("PulseOS storage scan failed: " + error.getMessage());
                    return List.of();
                }))
                .toList();
        return CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<BuildArtifactScanner.ArtifactDetails> all = new ArrayList<>();
                    jobs.forEach(j -> all.addAll(j.join()));
                    return all;
                });
    }
    public void analyzeAndReport(Path searchPath, Consumer<List<BuildArtifactScanner.ArtifactDetails>> callback) {
        scanAsync(searchPath).thenAccept(callback);
    }

    /**
     * Safe delete: never touches a folder flagged as an active project.
     * Moves the artifact into a dated Quarantine folder first (so a user can recover
     * within a grace period) instead of a hard, irreversible delete — this is the
     * key difference from a "blind" cleaner like CCleaner.
     */
    public CompletableFuture<Long> quarantineAndClean(List<BuildArtifactScanner.ArtifactDetails> targets) {
        return CompletableFuture.supplyAsync(() -> {
            long freedBytes = 0L;
            Path quarantineRoot = getQuarantineRoot();

            for (BuildArtifactScanner.ArtifactDetails artifact : targets) {
                if (artifact.isActiveProject()) {
                    // Refuse to touch active projects even if explicitly passed in.
                    continue;
                }
                try {
                    Path dest = quarantineRoot.resolve(
                            artifact.path().getFileName() + "_" + System.nanoTime());
                    Files.createDirectories(quarantineRoot);
                    moveRecursively(artifact.path(), dest);
                    freedBytes += artifact.sizeInBytes();
                } catch (IOException e) {
                    System.err.println("Failed to quarantine " + artifact.path() + ": " + e.getMessage());
                }
            }
            return freedBytes;
        }, executor);
    }

    /**
     * Permanently deletes anything sitting in Quarantine older than the given grace period.
     * Call this on app startup to actually reclaim the space after the user had a chance to undo.
     */
    public void purgeQuarantine(int graceDays) {
        Path quarantineRoot = getQuarantineRoot();
        if (!Files.exists(quarantineRoot)) return;

        try (var entries = Files.list(quarantineRoot)) {
            entries.forEach(entry -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    long ageDays = (System.currentTimeMillis() - attrs.creationTime().toMillis()) / (1000L * 60 * 60 * 24);
                    if (ageDays >= graceDays) {
                        deleteRecursively(entry);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private Path getQuarantineRoot() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".pulseos", "quarantine");
    }

    private void moveRecursively(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteRecursively(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                  .forEach(p -> {
                      try {
                          Files.deleteIfExists(p);
                      } catch (IOException ignored) {
                      }
                  });
        }
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}
