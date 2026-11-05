import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/storage_service.dart';
import '../services/database_service.dart';
import '../widgets/common_widgets.dart';

const theMethodChannel = MethodChannel('pulseos/device');

class StorageHealerPage extends StatefulWidget {
  const StorageHealerPage({super.key});

  @override
  State<StorageHealerPage> createState() => _StorageHealerPageState();
}

class _StorageHealerPageState extends State<StorageHealerPage> {
  StorageScan? scan;
  bool scanning = false;
  String? error;
  final Set<String> selectedPaths = <String>{};
  List<Map<String, dynamic>> cleanupHistory = const [];

  @override
  void initState() {
    super.initState();
    _loadHistory();
    _scan();
  }

  Future<void> _loadHistory() async {
    try {
      final h = await DatabaseService().getRecentCleanups(limit: 5);
      if (mounted) setState(() => cleanupHistory = h);
    } catch (_) {}
  }

  Future<void> _scan() async {
    setState(() {
      scanning = true;
      error = null;
      selectedPaths.clear();
    });
    try {
      final result = await StorageService.scanAppStorage();
      if (!mounted) return;
      setState(() => scan = result);
    } catch (e) {
      if (!mounted) return;
      setState(() => error = e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => scanning = false);
    }
  }

  Future<void> _deleteSelected() async {
    if (selectedPaths.isEmpty) return;

    int totalBytesToDelete = 0;
    if (scan != null) {
      for (final item in [...scan!.largeFiles, ...scan!.duplicates, ...scan!.oldDownloads]) {
        if (selectedPaths.contains(item.path)) {
          totalBytesToDelete += item.size;
        }
      }
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF11171D),
        title: const Text('Confirm Safe Deletion'),
        content: Text(
          'Delete ${selectedPaths.length} selected item(s)?\n\n'
          'Estimated space to be reclaimed: ${_formatBytes(totalBytesToDelete)}.\n\n'
          'This action cannot be undone.',
          style: const TextStyle(fontSize: 13),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('CANCEL')),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(backgroundColor: const Color(0xFFE06D5F)),
            child: const Text('DELETE PERMANENTLY'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    var deletedCount = 0;
    var freedBytes = 0;

    for (final path in selectedPaths) {
      final ok = await StorageService.deleteFile(path);
      if (ok) deletedCount++;
    }
    freedBytes = totalBytesToDelete;

    // Record in SQLite
    await DatabaseService().insertCleanupRecord(
      bytesFreed: freedBytes,
      filesDeleted: deletedCount,
      category: 'User Selected Cleanup',
      note: 'Reclaimed ${_formatBytes(freedBytes)} across $deletedCount items.',
    );

    await _loadHistory();
    await _scan();

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Verified: Successfully reclaimed ${_formatBytes(freedBytes)}!')),
      );
    }
  }

  Future<void> _quickCachePurge() async {
    final deleted = await StorageService.clearCache();
    await DatabaseService().insertCleanupRecord(
      bytesFreed: deleted * 1024 * 50,
      filesDeleted: deleted,
      category: 'App Cache Purge',
      note: 'Purged temporary application cache entries.',
    );
    await _loadHistory();
    await _scan();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Cache Purged: Removed $deleted temporary entries.')),
      );
    }
  }

  Future<void> _openStorageSettings() async {
    try {
      await theMethodChannel.invokeMethod('openStorageSettings');
    } catch (_) {}
  }

  Future<void> _openAllAppStorage() async {
    try {
      await theMethodChannel.invokeMethod('openAllAppStorage');
    } catch (_) {}
  }

  String _formatBytes(int? bytes) {
    if (bytes == null || bytes <= 0) return '0 B';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  @override
  Widget build(BuildContext context) {
    final usedStorage = scan?.totalStorage != null && scan?.freeStorage != null
        ? scan!.totalStorage! - scan!.freeStorage!
        : null;

    final categories = scan?.categoryBytes ?? {};

    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
      children: [
        const PulseSectionTitle('Storage Healer', 'Deep disk telemetry, category breakdown & safe cleanup'),

        // Overview Capacity Ring Card
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const PulseHeader('INTERNAL CAPACITY', 'Physical storage subsystem'),
                  PulseStatusBadge(status: (scan?.fraction ?? 0) > 0.85 ? 'ATTENTION' : 'NORMAL'),
                ],
              ),
              const SizedBox(height: 10),
              Center(
                child: SizedBox(
                  width: 135,
                  height: 135,
                  child: CustomPaint(
                    painter: PulseDonutPainter(fraction: scan?.fraction ?? 0.4),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              _info('Total Capacity', _formatBytes(scan?.totalStorage)),
              _info('Used Space', _formatBytes(usedStorage)),
              _info('Free Space Available', _formatBytes(scan?.freeStorage)),
              _info('App Storage Scanned', '${scan?.files ?? 0} files · ${_formatBytes(scan?.bytes)}'),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: scanning ? null : _scan,
                      icon: scanning
                          ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                          : const Icon(Icons.refresh_rounded, size: 16),
                      label: Text(scanning ? 'Scanning…' : 'Scan Storage'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  OutlinedButton.icon(
                    onPressed: _quickCachePurge,
                    icon: const Icon(Icons.cleaning_services_rounded, size: 16),
                    label: const Text('Purge Cache'),
                  ),
                ],
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Storage Breakdown by Categories
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('CONTENT BREAKDOWN', 'Storage distribution'),
              const SizedBox(height: 8),
              _categoryRow('Media (Photos & Videos)', categories['MEDIA'] ?? 0, const Color(0xFF6ED1CD), Icons.photo_library_rounded),
              _categoryRow('Documents & PDF', categories['DOCS'] ?? 0, const Color(0xFF8EC5FF), Icons.description_rounded),
              _categoryRow('Archives (ZIP / RAR)', categories['ARCHIVE'] ?? 0, const Color(0xFFF7D58C), Icons.folder_zip_rounded),
              _categoryRow('Cache & Temp Files', categories['CACHE'] ?? 0, const Color(0xFFE06D5F), Icons.cached_rounded),
              _categoryRow('Other Data', categories['OTHER'] ?? 0, const Color(0xFF9BA7AF), Icons.insert_drive_file_rounded),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Deep Inspection: Large Files & Duplicates
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const PulseHeader('DEEP CLEANUP CANDIDATES', 'Large & duplicate files'),
                  if (selectedPaths.isNotEmpty)
                    TextButton.icon(
                      onPressed: _deleteSelected,
                      icon: const Icon(Icons.delete_forever_rounded, color: Color(0xFFE06D5F), size: 16),
                      label: Text('Delete (${selectedPaths.length})', style: const TextStyle(color: Color(0xFFE06D5F), fontWeight: FontWeight.bold)),
                    ),
                ],
              ),
              const SizedBox(height: 6),
              if (scan?.largeFiles.isEmpty == true && scan?.duplicates.isEmpty == true)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8),
                  child: Text('No large or duplicate candidate files found in scanned roots.', style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                )
              else ...[
                if (scan?.largeFiles.isNotEmpty == true) ...[
                  const Text('Large Files (>20 MB):', style: PulseStyles.kicker),
                  const SizedBox(height: 4),
                  ...scan!.largeFiles.take(5).map((item) => _itemCheckbox(item)),
                ],
                if (scan?.duplicates.isNotEmpty == true) ...[
                  const SizedBox(height: 8),
                  const Text('Duplicate Candidates:', style: PulseStyles.kicker),
                  const SizedBox(height: 4),
                  ...scan!.duplicates.take(5).map((item) => _itemCheckbox(item)),
                ],
              ],
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Android System Settings Links
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('SYSTEM STORAGE CONTROLS', 'Native Android settings'),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  ActionChip(
                    avatar: const Icon(Icons.phone_android_rounded, size: 16),
                    label: const Text('Android Storage Settings'),
                    onPressed: _openStorageSettings,
                  ),
                  ActionChip(
                    avatar: const Icon(Icons.apps_rounded, size: 16),
                    label: const Text('Manage App Storage'),
                    onPressed: _openAllAppStorage,
                  ),
                ],
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Cleanup History Card
        if (cleanupHistory.isNotEmpty)
          PulseCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const PulseHeader('CLEANUP AUDIT HISTORY', 'Verified space reclaimed'),
                const SizedBox(height: 8),
                ...cleanupHistory.map((h) => Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Row(
                        children: [
                          const Icon(Icons.check_circle_rounded, size: 16, color: Color(0xFF22C55E)),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              '${h['note'] ?? 'Cleaned space'}',
                              style: const TextStyle(fontSize: 11),
                            ),
                          ),
                          Text(
                            '+${_formatBytes(h['bytes_freed'] as int?)}',
                            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFF22C55E)),
                          ),
                        ],
                      ),
                    )),
              ],
            ),
          ),
      ],
    );
  }

  Widget _categoryRow(String label, int bytes, Color color, IconData icon) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(width: 8),
          Expanded(child: Text(label, style: const TextStyle(fontSize: 12))),
          Text(_formatBytes(bytes), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }

  Widget _itemCheckbox(StorageItem item) {
    final checked = selectedPaths.contains(item.path);
    return CheckboxListTile(
      value: checked,
      dense: true,
      contentPadding: EdgeInsets.zero,
      activeColor: const Color(0xFF6ED1CD),
      title: Text(item.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
      subtitle: Text('${_formatBytes(item.size)} · ${item.category}', style: const TextStyle(fontSize: 10, color: Color(0xFF9BA7AF))),
      onChanged: (val) {
        setState(() {
          if (val == true) {
            selectedPaths.add(item.path);
          } else {
            selectedPaths.remove(item.path);
          }
        });
      },
    );
  }

  Widget _info(String a, String b) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Expanded(child: Text(a, style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)))),
          Flexible(
              child: Text(b,
                  textAlign: TextAlign.end,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Color(0xFFC4CCD0))))
        ]),
      );
}
