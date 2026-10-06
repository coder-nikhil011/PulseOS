import 'dart:io';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:flutter/foundation.dart';

class StorageItem {
  final String name;
  final String path;
  final int size;
  final bool isDirectory;
  final String category; // CACHE, DOCS, MEDIA, OTHER

  StorageItem(this.name, this.path, this.size, this.isDirectory, this.category);

  double get sizeInMb => size / (1024 * 1024);
}

class StorageScan {
  final String state;
  final int files;
  final int bytes;
  final int? totalStorage;
  final int? freeStorage;
  final List<StorageItem> largeFiles;

  const StorageScan(this.state, this.files, this.bytes, 
      {this.totalStorage, this.freeStorage, this.largeFiles = const []});

  double get fraction {
    if (totalStorage == null || freeStorage == null || totalStorage == 0) return 0;
    return ((totalStorage! - freeStorage!) / totalStorage!).clamp(0.0, 1.0);
  }
}

class StorageService {
  static const int _largeFileThreshold = 50 * 1024 * 1024; // 50MB

  /// Scans for temporary cache files.
  static Future<({int files, int bytes})> scanCache() async {
    try {
      final root = await getTemporaryDirectory();
      var files = 0;
      var bytes = 0;
      if (await root.exists()) {
        await for (final entity in root.list(recursive: true, followLinks: false)) {
          if (entity is File) {
            files++;
            try {
              bytes += await entity.length();
            } catch (_) {}
          }
        }
      }
      return (files: files, bytes: bytes);
    } catch (_) {
      return (files: 0, bytes: 0);
    }
  }

  static Future<StorageScan> scanAppStorage() async {
    if (kIsWeb) return const StorageScan('Android app required', 0, 0);
    
    try {
      final roots = <Directory>[
        await getApplicationDocumentsDirectory(),
        await getTemporaryDirectory(),
      ];
      
      var filesCount = 0;
      var totalBytes = 0;
      List<StorageItem> largeFiles = [];
      int? totalStorage;
      int? freeStorage;

      try {
        final snapshot = await const MethodChannel('pulseos/device').invokeMethod<Map<Object?, Object?>>('snapshot');
        totalStorage = (snapshot?['totalStorage'] as num?)?.toInt();
        freeStorage = (snapshot?['freeStorage'] as num?)?.toInt();
      } catch (_) {}

      for (final root in roots) {
        if (!await root.exists()) continue;
        await for (final entity in root.list(recursive: true, followLinks: false)) {
          if (entity is File) {
            filesCount++;
            try {
              final size = await entity.length();
              totalBytes += size;
              if (size > _largeFileThreshold) {
                largeFiles.add(StorageItem(
                  entity.path.split('/').last,
                  entity.path,
                  size,
                  false,
                  _categorizeFile(entity.path),
                ));
              }
            } catch (_) {}
          }
        }
      }

      largeFiles.sort((a, b) => b.size.compareTo(a.size));

      return StorageScan('Complete', filesCount, totalBytes,
          totalStorage: totalStorage, freeStorage: freeStorage, largeFiles: largeFiles);
    } catch (_) {
      return const StorageScan('Unavailable', 0, 0);
    }
  }

  static String _categorizeFile(String path) {
    final p = path.toLowerCase();
    if (p.endsWith('.jpg') || p.endsWith('.png') || p.endsWith('.mp4')) return 'MEDIA';
    if (p.endsWith('.pdf') || p.endsWith('.txt') || p.endsWith('.docx')) return 'DOCS';
    if (p.contains('cache')) return 'CACHE';
    return 'OTHER';
  }

  static Future<int> clearCache() async {
    try {
      final root = await getTemporaryDirectory();
      var deleted = 0;
      if (await root.exists()) {
        await for (final entity in root.list(followLinks: false)) {
          try {
            await entity.delete(recursive: true);
            deleted++;
          } catch (_) {}
        }
      }
      return deleted;
    } catch (_) {
      return 0;
    }
  }

  static Future<bool> deleteFile(String path) async {
    try {
      final file = File(path);
      if (await file.exists()) {
        await file.delete();
        return true;
      }
    } catch (_) {}
    return false;
  }
}
