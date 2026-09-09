package com.pulseos.converter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * eBook format conversion (epub/mobi/azw3/pdb etc.) needs a dedicated engine.
 * Shells out to Calibre's `ebook-convert` CLI if installed — same honesty policy as
 * MediaConverterEngine: real conversion when the tool exists, clear message when it doesn't.
 */
public class EbookConverterEngine {

    public record ConversionOutcome(boolean success, String message) {}

    public boolean isCalibreAvailable() {
        try {
            Process process = new ProcessBuilder("ebook-convert", "--version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            return finished && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public ConversionOutcome convert(Path sourcePath, Path targetPath) {
        if (!isCalibreAvailable()) {
            return new ConversionOutcome(false,
                    "Calibre not found on this machine. Install Calibre (calibre-ebook.com) — it ships the " +
                    "'ebook-convert' CLI tool — to enable offline eBook conversion.");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("ebook-convert", sourcePath.toString(), targetPath.toString());
            pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return new ConversionOutcome(false, "Conversion timed out after 120s.");
            }
            if (process.exitValue() != 0) {
                return new ConversionOutcome(false, "ebook-convert reported an error (exit code " + process.exitValue() + ").");
            }
            return new ConversionOutcome(true, "Converted via local Calibre (offline).");
        } catch (IOException e) {
            return new ConversionOutcome(false, "Conversion failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ConversionOutcome(false, "Conversion interrupted.");
        }
    }
}
