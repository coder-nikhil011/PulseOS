package com.pulseos.healer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Problem 2 expansion: the old pie chart only ever showed hardcoded fake numbers.
 * This scans the common "clutter" folders (Downloads, Desktop, Documents, Pictures,
 * Videos) for individually large or long-untouched files/folders, and combines that
 * with the build-artifact scan into real pie-chart categories: Free Space, Build
 * Artifacts, Large Files, Unused Clutter, Other Used Space.
 *
 * This intentionally does NOT walk the entire disk (too slow for a live dashboard) —
 * it scans the folders where clutter/large files actually accumulate in practice.
 */
public class StorageCategoryScanner {

    private static final long LARGE_FILE_THRESHOLD_MB = 200;
    private static final long UNUSED_DAYS_THRESHOLD = 60;
    private static final List<String> SCAN_FOLDERS = List.of(
            "Downloads", "Desktop", "Documents", "Pictures", "Videos", "Music");

    public record LargeItem(Path path, boolean isFolder, double sizeMb, long daysUnused) {}

    public record CategoryResult(
            Map<String, Double> categoryMb,
            List<LargeItem> largeItems,
            LargeItem biggestItem
    ) {}

    public CategoryResult scan(Path home, List<BuildArtifactScanner.ArtifactDetails> buildArtifacts) {
        List<LargeItem> largeItems = new ArrayList<>();
        Set<Path> artifactRoots = buildArtifacts.stream()
                .map(BuildArtifactScanner.ArtifactDetails::path)
                .map(p -> p.toAbsolutePath().normalize())
                .collect(java.util.stream.Collectors.toSet());

        for (String folderName : SCAN_FOLDERS) {
            Path folder = home.resolve(folderName);
            if (!Files.isDirectory(folder)) continue;
            scanFolderShallow(folder, largeItems, artifactRoots);
        }

        double buildArtifactMb = buildArtifacts.stream()
                .mapToDouble(BuildArtifactScanner.ArtifactDetails::getSizeInMb).sum();
        // Build artifacts are a more specific category. Do not count the same
        // directory again as a large/unused item.
        double largeItemsMb = largeItems.stream().mapToDouble(LargeItem::sizeMb).sum();

        long totalSpaceMb = home.toFile().getTotalSpace() / (1024 * 1024);
        long freeSpaceMb = home.toFile().getFreeSpace() / (1024 * 1024);
        double usedMb = Math.max(0, totalSpaceMb - freeSpaceMb);
        double otherUsedMb = Math.max(0, usedMb - buildArtifactMb - largeItemsMb);

        Map<String, Double> categories = new LinkedHashMap<>();
        categories.put("Free Space", (double) freeSpaceMb);
        if (buildArtifactMb > 1) categories.put("Build Artifacts (Ghost Bloat)", buildArtifactMb);
        if (largeItemsMb > 1) categories.put("Large / Unused Files", largeItemsMb);
        categories.put("Other Used Space", otherUsedMb);

        LargeItem biggest = largeItems.stream()
                .max((a, b) -> Double.compare(a.sizeMb(), b.sizeMb()))
                .orElse(null);

        // If nothing large found in clutter folders, fall back to the biggest build artifact.
        if (biggest == null && !buildArtifacts.isEmpty()) {
            var b = buildArtifacts.stream().max((a, c) -> Double.compare(a.getSizeInMb(), c.getSizeInMb())).get();
            biggest = new LargeItem(b.path(), true, b.getSizeInMb(), b.daysUnused());
        }

        return new CategoryResult(categories, largeItems, biggest);
    }

    private void scanFolderShallow(Path folder, List<LargeItem> results, Set<Path> artifactRoots) {
        try (var stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    double sizeMb;
                    if (attrs.isDirectory()) {
                        sizeMb = folderSizeMb(entry);
                    } else {
                        sizeMb = attrs.size() / (1024.0 * 1024.0);
                    }

                    long daysUnused = (System.currentTimeMillis() - attrs.lastAccessTime().toMillis()) / (1000L * 60 * 60 * 24);

                    Path normalized = entry.toAbsolutePath().normalize();
                    boolean isBuildArtifact = artifactRoots.stream().anyMatch(
                            artifact -> normalized.startsWith(artifact));
                    if (!isBuildArtifact && (sizeMb >= LARGE_FILE_THRESHOLD_MB || daysUnused >= UNUSED_DAYS_THRESHOLD)) {
                        results.add(new LargeItem(entry, attrs.isDirectory(), sizeMb, Math.max(0, daysUnused)));
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private double folderSizeMb(Path folder) {
        try (var stream = Files.walk(folder, 4)) {
            long bytes = stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException ignored) { return 0L; }
                    }).sum();
            return bytes / (1024.0 * 1024.0);
        } catch (IOException e) {
            return 0;
        }
    }
}
