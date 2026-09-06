package com.pulseos.watcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

/**
 * Problem 3 fix: DownloadInterceptorService only *detected* new downloads and showed a
 * toast — nothing ever moved. This service actually sorts files into destination folders
 * based on extension, right after they finish downloading.
 */
public class DownloadOrganizerService {

    private final Path downloadsRoot;

    private static final Map<String, String> EXTENSION_TO_FOLDER = Map.ofEntries(
            Map.entry("pdf", "Documents"),
            Map.entry("doc", "Documents"),
            Map.entry("docx", "Documents"),
            Map.entry("txt", "Documents"),
            Map.entry("xls", "Documents"),
            Map.entry("xlsx", "Documents"),
            Map.entry("ppt", "Documents"),
            Map.entry("pptx", "Documents"),
            Map.entry("png", "Images"),
            Map.entry("jpg", "Images"),
            Map.entry("jpeg", "Images"),
            Map.entry("webp", "Images"),
            Map.entry("gif", "Images"),
            Map.entry("svg", "Images"),
            Map.entry("zip", "Archives"),
            Map.entry("rar", "Archives"),
            Map.entry("7z", "Archives"),
            Map.entry("tar", "Archives"),
            Map.entry("gz", "Archives"),
            Map.entry("exe", "Installers"),
            Map.entry("msi", "Installers"),
            Map.entry("dmg", "Installers"),
            Map.entry("pkg", "Installers"),
            Map.entry("mp4", "Videos"),
            Map.entry("mkv", "Videos"),
            Map.entry("mov", "Videos"),
            Map.entry("mp3", "Audio"),
            Map.entry("wav", "Audio")
    );

    // Never auto-move these even if they show up in Downloads — safety net.
    private static final Set<String> NEVER_MOVE = Set.of("crdownload", "tmp", "part", "download");

    public DownloadOrganizerService(Path downloadsRoot) {
        this.downloadsRoot = downloadsRoot;
    }

    public record OrganizeResult(boolean moved, Path newLocation, String reason) {}

    /**
     * Moves a freshly-downloaded file into its category subfolder under Downloads.
     * e.g. Downloads/Project_Report.pdf -> Downloads/Documents/Project_Report.pdf
     */
    public OrganizeResult organize(Path filePath, String extension) {
        String ext = extension.toLowerCase();

        if (NEVER_MOVE.contains(ext)) {
            return new OrganizeResult(false, filePath, "Incomplete/temp file — left alone");
        }

        String category = EXTENSION_TO_FOLDER.getOrDefault(ext, "Others");

        try {
            // Give the browser a brief moment to finish writing before we move it.
            waitUntilStable(filePath);

            Path destFolder = downloadsRoot.resolve(category);
            Files.createDirectories(destFolder);

            Path destFile = resolveNameCollision(destFolder, filePath.getFileName().toString());
            Files.move(filePath, destFile, StandardCopyOption.REPLACE_EXISTING);

            return new OrganizeResult(true, destFile, "Sorted into " + category);
        } catch (IOException e) {
            return new OrganizeResult(false, filePath, "Move failed: " + e.getMessage());
        }
    }

    private void waitUntilStable(Path file) {
        try {
            long lastSize = -1;
            for (int i = 0; i < 5; i++) {
                if (!Files.exists(file)) return;
                long size = Files.size(file);
                if (size == lastSize) return;
                lastSize = size;
                Thread.sleep(200);
            }
        } catch (IOException | InterruptedException ignored) {
        }
    }

    private Path resolveNameCollision(Path destFolder, String fileName) {
        Path candidate = destFolder.resolve(fileName);
        if (!Files.exists(candidate)) return candidate;

        String base = fileName;
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            ext = fileName.substring(dot);
        }

        int counter = 1;
        Path unique;
        do {
            unique = destFolder.resolve(base + " (" + counter + ")" + ext);
            counter++;
        } while (Files.exists(unique));

        return unique;
    }
}
