package com.pulseos.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Thin client for a local Ollama instance (http://localhost:11434). 100% offline —
 * nothing here ever calls out to the internet. No JSON library dependency is added;
 * Ollama's /api/generate (stream:false) response is a single flat JSON object, so a
 * small hand-rolled extractor for the "response" field is enough and keeps the build
 * lightweight for a hackathon.
 *
 * Honesty policy: if Ollama isn't running, callers get a clear "not available" signal
 * instead of a fabricated answer — see AiFeaturesService for how each feature degrades.
 */
public class OllamaClient {

    private static final String BASE_URL = "http://localhost:11434";
    private final HttpClient httpClient;
    private final String model;

    public OllamaClient(String model) {
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public CompletableFuture<String> generate(String prompt) {
        String jsonBody = "{"
                + "\"model\":\"" + escape(model) + "\","
                + "\"prompt\":\"" + escape(prompt) + "\","
                + "\"stream\":false"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/generate"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Ollama returned HTTP " + response.statusCode());
                    }
                    return extractResponseField(response.body());
                });
    }

    private String extractResponseField(String json) {
        // Find "response":"....." allowing for escaped quotes inside.
        int keyIdx = json.indexOf("\"response\":\"");
        if (keyIdx < 0) return "";
        int start = keyIdx + "\"response\":\"".length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(next);
                }
                i++;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
