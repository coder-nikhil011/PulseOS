package com.pulseos.watcher;

import java.nio.file.Path;

@FunctionalInterface
public interface FileRouteListener {
    void onFileDetected(Path filePath, String fileExtension);
}