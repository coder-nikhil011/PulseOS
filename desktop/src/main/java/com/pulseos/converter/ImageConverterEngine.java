package com.pulseos.converter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;

import net.coobird.thumbnailator.Thumbnails;

public class ImageConverterEngine {

    /**
     * Converts local image formats (PNG, JPG, WEBP) and compresses completely in-memory
     */
    public void convertImageFormat(Path sourcePath, Path targetPath, String targetFormat) throws IOException {
        BufferedImage image = ImageIO.read(sourcePath.toFile());
        if (image == null) {
            throw new IOException("Unsupported image format or corrupt file: " + sourcePath);
        }

        String format = targetFormat == null ? "" : targetFormat.toLowerCase(Locale.ROOT);
        if (format.isBlank()) throw new IOException("Choose an image target format.");
        File targetFile = targetPath.toFile();
        if (!ImageIO.write(image, format, targetFile)) {
            throw new IOException("No image writer is installed for ." + format + ".");
        }
    }

    /**
     * Compresses image quality and resizes offline
     */
    public void compressAndResize(Path sourcePath, Path targetPath, double scaleFactor, double quality) throws IOException {
        Thumbnails.of(sourcePath.toFile())
                .scale(scaleFactor)
                .outputQuality(quality)
                .toFile(targetPath.toFile());
    }
}