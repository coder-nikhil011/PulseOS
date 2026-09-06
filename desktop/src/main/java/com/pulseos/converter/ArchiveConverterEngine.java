package com.pulseos.converter;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Real, offline archive re-packaging: extracts a source archive fully in memory/temp
 * then re-packs into the target format. Supports ZIP, TAR, TAR.GZ, TAR.BZ2, 7Z.
 * RAR is intentionally NOT supported for writing — the format is proprietary and no
 * legally redistributable pure-Java encoder exists.
 */
public class ArchiveConverterEngine {

    public record ExtractedEntry(String name, byte[] data) {}

    public void convert(Path sourcePath, Path targetPath, String targetFormat) throws IOException {
        List<ExtractedEntry> entries = extractAll(sourcePath);
        repack(entries, targetPath, targetFormat.toLowerCase());
    }

    private List<ExtractedEntry> extractAll(Path sourcePath) throws IOException {
        List<ExtractedEntry> results = new ArrayList<>();
        String lowerName = sourcePath.getFileName().toString().toLowerCase();

        if (lowerName.endsWith(".7z")) {
            try (SevenZFile sevenZFile = new SevenZFile(sourcePath.toFile())) {
                SevenZArchiveEntry entry;
                while ((entry = sevenZFile.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    byte[] data = new byte[(int) entry.getSize()];
                    sevenZFile.read(data);
                    results.add(new ExtractedEntry(entry.getName(), data));
                }
            }
            return results;
        }

        try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(sourcePath));
             ArchiveInputStream<? extends ArchiveEntry> in = new ArchiveStreamFactory()
                     .createArchiveInputStream(autoDetectFormat(lowerName), fileIn)) {

            ArchiveEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                results.add(new ExtractedEntry(entry.getName(), buffer.toByteArray()));
            }
        } catch (org.apache.commons.compress.archivers.ArchiveException e) {
            throw new IOException("Unsupported or corrupt archive: " + e.getMessage(), e);
        }
        return results;
    }

    private String autoDetectFormat(String lowerName) {
        if (lowerName.endsWith(".zip")) return ArchiveStreamFactory.ZIP;
        if (lowerName.endsWith(".tar")) return ArchiveStreamFactory.TAR;
        if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz")) return ArchiveStreamFactory.TAR;
        if (lowerName.endsWith(".tar.bz2")) return ArchiveStreamFactory.TAR;
        if (lowerName.endsWith(".cab")) return ArchiveStreamFactory.CPIO;
        return ArchiveStreamFactory.ZIP;
    }

    private void repack(List<ExtractedEntry> entries, Path targetPath, String targetFormat) throws IOException {
        switch (targetFormat) {
            case "zip" -> {
                try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(targetPath.toFile())) {
                    for (ExtractedEntry e : entries) {
                        ZipArchiveEntry zipEntry = new ZipArchiveEntry(e.name());
                        zipEntry.setSize(e.data().length);
                        out.putArchiveEntry(zipEntry);
                        out.write(e.data());
                        out.closeArchiveEntry();
                    }
                }
            }
            case "tar" -> {
                try (TarArchiveOutputStream out = new TarArchiveOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(targetPath)))) {
                    writeTarEntries(out, entries);
                }
            }
            case "tar.gz", "tgz" -> {
                try (TarArchiveOutputStream out = new TarArchiveOutputStream(
                        new GzipCompressorOutputStream(new BufferedOutputStream(Files.newOutputStream(targetPath))))) {
                    writeTarEntries(out, entries);
                }
            }
            case "tar.bz2" -> {
                try (TarArchiveOutputStream out = new TarArchiveOutputStream(
                        new BZip2CompressorOutputStream(new BufferedOutputStream(Files.newOutputStream(targetPath))))) {
                    writeTarEntries(out, entries);
                }
            }
            case "7z" -> {
                try (SevenZOutputFile out = new SevenZOutputFile(targetPath.toFile())) {
                    for (ExtractedEntry e : entries) {
                        SevenZArchiveEntry entry = new SevenZArchiveEntry();
                        entry.setName(e.name());
                        entry.setSize(e.data().length);
                        out.putArchiveEntry(entry);
                        out.write(e.data());
                        out.closeArchiveEntry();
                    }
                }
            }
            default -> throw new IOException("Target archive format not supported: " + targetFormat
                    + " (RAR write is not supported — proprietary format)");
        }
    }

    private void writeTarEntries(TarArchiveOutputStream out, List<ExtractedEntry> entries) throws IOException {
        out.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
        for (ExtractedEntry e : entries) {
            TarArchiveEntry entry = new TarArchiveEntry(e.name());
            entry.setSize(e.data().length);
            out.putArchiveEntry(entry);
            out.write(e.data());
            out.closeArchiveEntry();
        }
    }
}
