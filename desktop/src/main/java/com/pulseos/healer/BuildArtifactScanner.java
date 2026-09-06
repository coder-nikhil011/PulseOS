package com.pulseos.healer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BuildArtifactScanner {

    // Heavy build artifact signature directories
    private static final Set<String> ARTIFACT_NAMES = Set.of(
            "node_modules", "target", ".gradle", ".m2", ".venv", "bin", "obj"
    );

    // Problem 1 fix: below this many days-unused, a project is considered "active"
    // and PulseOS refuses to touch it — this is what makes us different from a blind cleaner.
    private static final long ACTIVE_PROJECT_THRESHOLD_DAYS = 14;
    private static final int MAX_SCAN_DEPTH = 6;
    private static final Set<String> SKIP_DIRS = Set.of("Library", ".Trash", ".cache", "Caches", "Cache", "Application Support", "Containers");

    public record ArtifactDetails(Path path, long sizeInBytes, long daysUnused, boolean isActiveProject) {
        public double getSizeInMb() {
            return sizeInBytes / (1024.0 * 1024.0);
        }
        public double getSizeInGb() {
            return sizeInBytes / (1024.0 * 1024.0 * 1024.0);
        }
    }

    /**
     * Scans root directory recursively for build artifact directories
     */
    public List<ArtifactDetails> scanDirectory(Path rootPath) {
        List<ArtifactDetails> results = new ArrayList<>();

        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            return results;
        }

        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    int relativeDepth = rootPath.relativize(dir).getNameCount();
                    String folderName = dir.getFileName().toString();
                    if (relativeDepth > MAX_SCAN_DEPTH || SKIP_DIRS.contains(folderName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (ARTIFACT_NAMES.contains(folderName.toLowerCase())) {
                        long folderSize = calculateFolderSize(dir);
                        long daysUnused = calculateDaysUnused(dir, attrs);
                        boolean isActive = isActiveProject(dir, daysUnused);

                        results.add(new ArtifactDetails(dir, folderSize, daysUnused, isActive));

                        // Skip scanning subdirectories inside the artifact folder
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Ignore system/permission restricted files silently
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Error during storage scan: " + e.getMessage());
        }

        return results;
    }

    private long calculateFolderSize(Path folderPath) {
        try (var stream = Files.walk(folderPath)) {
            return stream.filter(p -> p.toFile().isFile())
                         .mapToLong(p -> p.toFile().length())
                         .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * lastAccessTime is unreliable on many OSes/filesystems (NTFS often disables
     * access-time tracking by default), so we fall back to lastModifiedTime of the
     * parent project folder — a much more reliable "still being worked on" signal.
     */
    private long calculateDaysUnused(Path artifactDir, BasicFileAttributes attrs) {
        long referenceMillis;

        Path projectRoot = artifactDir.getParent();
        long parentModified = 0L;
        try {
            if (projectRoot != null && Files.exists(projectRoot)) {
                parentModified = Files.getLastModifiedTime(projectRoot).toMillis();
            }
        } catch (IOException ignored) {
        }

        long accessMillis = attrs.lastAccessTime().toMillis();
        long modifiedMillis = attrs.lastModifiedTime().toMillis();

        // Use whichever signal is most recent: artifact access, artifact modification,
        // or the parent project folder being touched.
        referenceMillis = Math.max(Math.max(accessMillis, modifiedMillis), parentModified);

        long diffMillis = System.currentTimeMillis() - referenceMillis;
        return Math.max(0, diffMillis / (1000 * 60 * 60 * 24));
    }

    /**
     * Core "intelligent, not blind" safety check (Problem 1).
     * A project is treated as ACTIVE — and therefore protected from cleanup — if:
     *  1) it was touched more recently than ACTIVE_PROJECT_THRESHOLD_DAYS, OR
     *  2) it has a .git folder whose HEAD was modified recently (still under active development).
     */
    private boolean isActiveProject(Path artifactDir, long daysUnused) {
        if (daysUnused < ACTIVE_PROJECT_THRESHOLD_DAYS) {
            return true;
        }

        Path projectRoot = artifactDir.getParent();
        if (projectRoot == null) return false;

        Path gitHead = projectRoot.resolve(".git").resolve("HEAD");
        try {
            if (Files.exists(gitHead)) {
                long gitModifiedMillis = Files.getLastModifiedTime(gitHead).toMillis();
                long gitDaysUnused = (System.currentTimeMillis() - gitModifiedMillis) / (1000 * 60 * 60 * 24);
                return gitDaysUnused < ACTIVE_PROJECT_THRESHOLD_DAYS;
            }
        } catch (IOException ignored) {
        }

        return false;
    }
}
