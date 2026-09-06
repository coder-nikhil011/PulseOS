package com.pulseos.converter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Real audio/video transcoding requires a codec engine — pure Java cannot decode/encode
 * MP3, AAC, MP4, MKV etc. This engine shells out to a locally-installed `ffmpeg` binary
 * if present on the user's machine. This is still 100% offline/privacy-safe (nothing
 * leaves the machine, unlike cloud converters) — it just depends on ffmpeg being
 * installed, same as most legitimate desktop media converters (HandBrake, etc.).
 * If ffmpeg isn't found, we say so clearly instead of pretending to convert.
 */
public class MediaConverterEngine {

    public record ConversionOutcome(boolean success, String message) {}

    public boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public ConversionOutcome convert(Path sourcePath, Path targetPath) {
        if (!isFfmpegAvailable()) {
            return new ConversionOutcome(false,
                    "ffmpeg not found on this machine. Install ffmpeg (ffmpeg.org) and add it to PATH " +
                    "to enable offline audio/video conversion.");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", sourcePath.toString(), targetPath.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return new ConversionOutcome(false, "Conversion timed out after 120s.");
            }
            if (process.exitValue() != 0) {
                return new ConversionOutcome(false, "ffmpeg reported an error (exit code " + process.exitValue() + ").");
            }
            return new ConversionOutcome(true, "Converted via local ffmpeg (offline).");
        } catch (IOException | InterruptedException e) {
            return new ConversionOutcome(false, "Conversion failed: " + e.getMessage());
        }
    }
}
