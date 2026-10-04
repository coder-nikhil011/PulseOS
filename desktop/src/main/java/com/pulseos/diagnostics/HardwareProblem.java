package com.pulseos.diagnostics;

import java.time.Instant;
import java.util.List;

public record HardwareProblem(
        String id,
        String component,
        String classification,
        String title,
        String state,
        String severity,
        int confidence,
        List<String> evidence,
        Instant firstDetected,
        Instant lastSeen,
        int occurrenceCount,
        String capability) {
}