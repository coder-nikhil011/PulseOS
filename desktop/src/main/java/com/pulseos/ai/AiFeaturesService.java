package com.pulseos.ai;

import com.pulseos.healer.BuildArtifactScanner.ArtifactDetails;
import com.pulseos.telemetry.SystemMetrics;

import java.util.concurrent.CompletableFuture;

/**
 * Wraps the 4 pitched hackathon AI features on top of OllamaClient. Every method
 * degrades to an honest, still-useful rule-based fallback if Ollama isn't running —
 * never a fabricated "AI" answer pretending to be a model output.
 */
public class AiFeaturesService {

    private final OllamaClient client;

    public AiFeaturesService(String model) {
        this.client = new OllamaClient(model);
    }

    public boolean isAiAvailable() {
        return client.isAvailable();
    }

    /** Feature 2 (AI Code Build & Dependency Pruner): natural-language safety explanation. */
    public CompletableFuture<String> explainCleanupSafety(ArtifactDetails artifact) {
        if (!isAiAvailable()) {
            return CompletableFuture.completedFuture(fallbackCleanupExplanation(artifact));
        }
        String prompt = String.format(
                "You are a desktop cleanup assistant. In 2 short sentences, explain to a non-technical " +
                "user whether it's safe to delete this build-artifact folder. Be reassuring but honest. " +
                "Folder: %s | Size: %.0f MB | Days since last touched: %d | Currently an active project: %b.",
                artifact.path(), artifact.getSizeInMb(), artifact.daysUnused(), artifact.isActiveProject());
        return client.generate(prompt).exceptionally(ex -> fallbackCleanupExplanation(artifact));
    }

    private String fallbackCleanupExplanation(ArtifactDetails artifact) {
        if (artifact.isActiveProject()) {
            return "This project was touched recently, so PulseOS is keeping it protected — deleting it now could break your current work.";
        }
        return String.format(
                "This folder hasn't been touched in %d days and isn't part of an active project — " +
                "safe to clean. Your source code and git history stay untouched; only the rebuildable " +
                "dependency cache is removed.", artifact.daysUnused());
    }

    /** Feature 4 (Smart Thermal & Anomaly Diagnosis). */
    public CompletableFuture<String> explainThermalAnomaly(SystemMetrics metrics) {
        if (metrics.getTopProcesses().isEmpty()) {
            return CompletableFuture.completedFuture("No process data available yet.");
        }
        var top = metrics.getTopProcesses().get(0);
        if (!isAiAvailable()) {
            return CompletableFuture.completedFuture(String.format(
                    "%s (PID %d) is the top CPU consumer at %.1f%% — likely responsible for the load spike. " +
                    "Consider closing it if you don't need it right now.",
                    top.name(), top.pid(), top.cpuPercent()));
        }
        String prompt = String.format(
                "You are a system performance assistant. In 2 short sentences, explain to a user why their " +
                "CPU/temperature might be spiking, given this top process: name=%s, pid=%d, cpu=%.1f%%, " +
                "ramMb=%.0f, threads=%d. Suggest whether to terminate it.",
                top.name(), top.pid(), top.cpuPercent(), top.getRamMb(), top.threadCount());
        return client.generate(prompt).exceptionally(ex -> String.format(
                "%s (PID %d) is the top CPU consumer at %.1f%% — likely responsible for the load spike.",
                top.name(), top.pid(), top.cpuPercent()));
    }

    /** Feature 1 (Smart Download Auto-Naming & Semantic Routing). */
    public CompletableFuture<RenameSuggestion> suggestDownloadRename(String extractedText, String originalFileName) {
        if (extractedText == null || extractedText.isBlank() || !isAiAvailable()) {
            return CompletableFuture.completedFuture(RenameSuggestion.unchanged(originalFileName));
        }
        String snippet = extractedText.length() > 1500 ? extractedText.substring(0, 1500) : extractedText;
        String prompt = "Read this document text and suggest a clean, descriptive filename (no extension) " +
                "and a short 1-2 level folder path to file it under (like 'University/Receipts'). " +
                "Reply ONLY in this exact format on one line: FILENAME: <name> | FOLDER: <path>\n\n" +
                "Document text:\n" + snippet;

        return client.generate(prompt)
                .thenApply(response -> parseRenameSuggestion(response, originalFileName))
                .exceptionally(ex -> RenameSuggestion.unchanged(originalFileName));
    }

    private RenameSuggestion parseRenameSuggestion(String response, String originalFileName) {
        try {
            String nameMarker = "FILENAME:";
            String folderMarker = "FOLDER:";
            int nameIdx = response.indexOf(nameMarker);
            int folderIdx = response.indexOf(folderMarker);
            if (nameIdx < 0 || folderIdx < 0) return RenameSuggestion.unchanged(originalFileName);

            String name = response.substring(nameIdx + nameMarker.length(), folderIdx)
                    .replace("|", "").trim().replaceAll("[\\\\/:*?\"<>|]", "_");
            String folder = response.substring(folderIdx + folderMarker.length()).trim()
                    .lines().findFirst().orElse("").trim();

            if (name.isBlank()) return RenameSuggestion.unchanged(originalFileName);
            return new RenameSuggestion(name, folder.isBlank() ? null : folder, true);
        } catch (Exception e) {
            return RenameSuggestion.unchanged(originalFileName);
        }
    }

    public record RenameSuggestion(String suggestedName, String suggestedFolder, boolean aiGenerated) {
        static RenameSuggestion unchanged(String originalName) {
            return new RenameSuggestion(originalName, null, false);
        }
    }

    /** Feature 3 (Natural Language File Search & Command Assistant). */
    public CompletableFuture<SearchIntent> parseSearchIntent(String userQuery) {
        if (!isAiAvailable()) {
            return CompletableFuture.completedFuture(fallbackParseIntent(userQuery));
        }
        String prompt = "Parse this file-search request into keywords and an optional target format. " +
                "Reply ONLY in this exact format on one line: KEYWORDS: <space separated words> | FORMAT: <extension or NONE>\n\n" +
                "Request: " + userQuery;

        return client.generate(prompt)
                .thenApply(this::parseSearchIntentResponse)
                .exceptionally(ex -> fallbackParseIntent(userQuery));
    }

    private SearchIntent parseSearchIntentResponse(String response) {
        try {
            int kIdx = response.indexOf("KEYWORDS:");
            int fIdx = response.indexOf("FORMAT:");
            if (kIdx < 0) return fallbackParseIntent(response);
            String keywords = fIdx > kIdx
                    ? response.substring(kIdx + "KEYWORDS:".length(), fIdx)
                    : response.substring(kIdx + "KEYWORDS:".length());
            keywords = keywords.replace("|", "").trim();
            String format = null;
            if (fIdx >= 0) {
                format = response.substring(fIdx + "FORMAT:".length()).lines().findFirst().orElse("").trim();
                if (format.equalsIgnoreCase("NONE") || format.isBlank()) format = null;
            }
            return new SearchIntent(keywords, format);
        } catch (Exception e) {
            return fallbackParseIntent(response);
        }
    }

    private SearchIntent fallbackParseIntent(String userQuery) {
        // Rule-based fallback: strip common stopwords/verbs, keep the rest as keywords.
        String cleaned = userQuery.toLowerCase()
                .replaceAll("\\b(find|search|show|me|that|the|a|an|and|convert|it|to|from|please|last|my)\\b", " ")
                .replaceAll("\\s+", " ").trim();
        String format = null;
        for (String ext : new String[]{"pdf", "docx", "doc", "png", "jpg", "xlsx", "txt", "zip", "mp4", "mp3"}) {
            if (userQuery.toLowerCase().contains(ext)) { format = ext; break; }
        }
        return new SearchIntent(cleaned, format);
    }

    public record SearchIntent(String keywords, String targetFormat) {}
}
