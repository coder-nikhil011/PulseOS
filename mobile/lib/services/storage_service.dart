import 'dart:io';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:flutter/foundation.dart';

class StorageItem {
  final String name;
  final String path;
  final int size;
  final bool isDirectory;
  final String category; // CACHE, DOCS, MEDIA, ARCHIVE, OTHER
  final DateTime? modified;

  StorageItem(this.name, this.path, this.size, this.isDirectory, this.category, [this.modified]);

  double get sizeInMb => size / (1024 * 1024);
}

class StorageScan {
  final String state;
  final int files;
  final int bytes;
  final int? totalStorage;
  final int? freeStorage;
  final List<StorageItem> largeFiles;
  final List<StorageItem> duplicates;
  final List<StorageItem> oldDownloads;
  final Map<String, int> categoryBytes;

  const StorageScan(
    this.state,
    this.files,
    this.bytes, {
    this.totalStorage,
    this.freeStorage,
    this.largeFiles = const [],
    this.duplicates = const [],
    this.oldDownloads = const [],
    this.categoryBytes = const {},
  });

  double get fraction {
    if (totalStorage == null || freeStorage == null || totalStorage == 0) return 0;
    return ((totalStorage! - freeStorage!) / totalStorage!).clamp(0.0, 1.0);
  }
}

class StorageService {
  static const int _largeFileThreshold = 20 * 1024 * 1024; // 20MB

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

      // Try adding downloads directory if available
      try {
        final dls = Directory('/storage/emulated/0/Download');
        if (await dls.exists()) roots.add(dls);
      } catch (_) {}

      var filesCount = 0;
      var totalBytes = 0;
      List<StorageItem> largeFiles = [];
      List<StorageItem> allScanned = [];
      Map<String, int> categoryBytes = {
        'MEDIA': 0,
        'DOCS': 0,
        'ARCHIVE': 0,
        'CACHE': 0,
        'OTHER': 0,
      };

      int? totalStorage;
      int? freeStorage;

      try {
        final snapshot = await const MethodChannel('pulseos/device').invokeMethod<Map<Object?, Object?>>('snapshot');
        totalStorage = (snapshot?['totalStorage'] as num?)?.toInt();
        freeStorage = (snapshot?['freeStorage'] as num?)?.toInt();
      } catch (_) {}

      final now = DateTime.now();

      for (final root in roots) {
        if (!await root.exists()) continue;
        try {
          await for (final entity in root.list(recursive: true, followLinks: false)) {
            if (entity is File) {
              filesCount++;
              try {
                final size = await entity.length();
                final stat = await entity.stat();
                totalBytes += size;
                final cat = _categorizeFile(entity.path);
                categoryBytes[cat] = (categoryBytes[cat] ?? 0) + size;

                final item = StorageItem(
                  entity.path.split(Platform.pathSeparator).last,
                  entity.path,
                  size,
                  false,
                  cat,
                  stat.modified,
                );

                allScanned.add(item);

                if (size > _largeFileThreshold) {
                  largeFiles.add(item);
                }
              } catch (_) {}
            }
          }
        } catch (_) {}
      }

      // Detect duplicates (items with same filename and identical byte size)
      final sizeMap = <String, List<StorageItem>>{};
      for (final item in allScanned) {
        final key = '${item.name}_${item.size}';
        sizeMap.putIfAbsent(key, () => []).add(item);
      }
      final duplicates = sizeMap.values.where((list) => list.length > 1).expand((i) => i.skip(1)).toList();

      // Detect old downloads (modified > 30 days ago)
      final oldDownloads = allScanned.where((item) {
        if (item.modified == null) return false;
        return now.difference(item.modified!).inDays > 30;
      }).toList();

      try {
        final intel = await const MethodChannel('pulseos/device').invokeMethod<Map<Object?, Object?>>('storageIntel');
        final scannedFiles = (intel?['files'] as num?)?.toInt() ?? 0;
        final scannedBytes = (intel?['bytes'] as num?)?.toInt() ?? 0;
        if (scannedFiles > filesCount) filesCount = scannedFiles;
        if (scannedBytes > totalBytes) totalBytes = scannedBytes;
      } catch (_) {}

      largeFiles.sort((a, b) => b.size.compareTo(a.size));

      return StorageScan(
        'Complete',
        filesCount,
        totalBytes,
        totalStorage: totalStorage,
        freeStorage: freeStorage,
        largeFiles: largeFiles,
        duplicates: duplicates,
        oldDownloads: oldDownloads,
        categoryBytes: categoryBytes,
      );
    } catch (_) {
      return const StorageScan('Unavailable', 0, 0);
    }
  }

  static String _categorizeFile(String path) {
    final p = path.toLowerCase();
    if (p.endsWith('.jpg') || p.endsWith('.png') || p.endsWith('.webp') || p.endsWith('.mp4') || p.endsWith('.mov') || p.endsWith('.mp3')) return 'MEDIA';
    if (p.endsWith('.pdf') || p.endsWith('.txt') || p.endsWith('.docx') || p.endsWith('.xlsx') || p.endsWith('.md')) return 'DOCS';
    if (p.endsWith('.zip') || p.endsWith('.rar') || p.endsWith('.tar') || p.endsWith('.gz') || p.endsWith('.7z')) return 'ARCHIVE';
    if (p.contains('cache') || p.endsWith('.tmp') || p.endsWith('.log')) return 'CACHE';
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
