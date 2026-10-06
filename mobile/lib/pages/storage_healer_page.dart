import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/storage_service.dart';
import '../widgets/common_widgets.dart';

/// Global helper for MethodChannel to avoid duplicate declarations in pages
const theMethodChannel = MethodChannel('pulseos/device');

class StorageHealerPage extends StatefulWidget {
  const StorageHealerPage({super.key});

  @override
  State<StorageHealerPage> createState() => _StorageHealerPageState();
}

class _StorageHealerPageState extends State<StorageHealerPage> {
  StorageScan? scan;
  bool scanning = false;
  bool cacheScanning = false;
  int? cacheBytes;
  int? cacheFiles;
  List<Map<String, dynamic>> appCaches = const [];
  final Set<String> selectedPackages = <String>{};
  String? error;

  Future<void> _scan() async {
    setState(() {
      scanning = true;
      error = null;
    });
    try {
      final result = await StorageService.scanAppStorage();
      if (!mounted) return;
      setState(() => scan = result);
    } catch (exception) {
      if (!mounted) return;
      setState(
          () => error = exception.toString().replaceFirst('Exception: ', ''));
    }
    if (mounted) {
      setState(() => scanning = false);
    }
  }

  Future<void> _openStorageSettings() async {
    try {
      await theMethodChannel.invokeMethod('openStorageSettings');
    } on PlatformException catch (exception) {
      if (mounted) setState(() => error = exception.message);
    } on MissingPluginException {
      if (mounted) {
        setState(() => error = 'System storage settings are Android-only.');
      }
    }
  }

  Future<void> _cleanup() async {
    setState(() => cacheScanning = true);
    final cache = await StorageService.scanCache();
    Map<Object?, Object?>? device;
    try {
      device = await theMethodChannel.invokeMethod<Map<Object?, Object?>>('snapshot');
    } on PlatformException {
      // Android may reject the snapshot call when the platform bridge is unavailable.
    }
    if (!mounted) return;
    setState(() {
      cacheScanning = false;
      cacheBytes = cache.bytes;
      cacheFiles = cache.files;
    });
    await showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
              title: const Text('Cleanup'),
              content: Text(
                  'Available RAM: ${_formatBytes((device?['availableMemory'] as num?)?.toInt())} of ${_formatBytes((device?['totalMemory'] as num?)?.toInt())}.\\n\\nDetected PulseOS temporary files: ${_formatBytes(cache.bytes)} (${cache.files} files).\\n\\nOnly these app-owned temporary files can be safely removed.'),
              actions: [
                TextButton(
                    onPressed: _openStorageSettings,
                    child: const Text('SYSTEM STORAGE')),
                FilledButton(
                    onPressed: cache.bytes == 0
                        ? null
                        : () async {
                            Navigator.pop(context);
                            await _clearCache();
                          },
                    child: const Text('CLEAN SAFE CACHE')),
              ],
            ));
  }

  Future<void> _clearCache() async {
    final safe = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
              title: const Text('Confirm cleanup'),
              content: const Text(
                  'Only PulseOS temporary files will be removed. Photos, videos, documents, downloads, databases and other apps are not touched.'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('CANCEL')),
                FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('CONFIRM CLEANUP')),
              ],
            ));
    if (safe != true || !mounted) return;
    final deleted = await StorageService.clearCache();
    if (mounted) {
      setState(() {
        cacheBytes = 0;
        cacheFiles = 0;
      });
      _show(context, 'Cleanup complete',
          Text('Removed $deleted temporary entries.'));
    }
  }

  Future<void> _reviewCache() async {
    setState(() => cacheScanning = true);
    final cache = await StorageService.scanCache();
    List<Map<String, dynamic>> apps = const [];
    try {
      final result = await theMethodChannel.invokeMethod<List<Object?>>('appCaches');
      apps = (result ?? const [])
          .whereType<Map>()
          .map((item) =>
              item.map((key, value) => MapEntry(key.toString(), value)))
          .where((item) => (item['cacheBytes'] as num? ?? 0) > 0)
          .toList();
    } on PlatformException {
      // App cache access may not be available on all Android devices.
    }
    if (!mounted) return;
    setState(() {
      cacheScanning = false;
      cacheBytes = cache.bytes;
      cacheFiles = cache.files;
      appCaches = apps;
    });
  }

  Future<void> _openAppStorage(String packageName) async {
    await theMethodChannel.invokeMethod('openAppStorage', {
      'package': packageName,
    });
  }

  Future<void> _openAllAppStorage() async {
    await theMethodChannel.invokeMethod('openAllAppStorage');
  }

  String _formatBytes(int? bytes) {
    if (bytes == null) return 'Unavailable';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  void _show(BuildContext context, String title, Widget body) =>
      showModalBottomSheet(
          context: context,
          backgroundColor: const Color(0xFF10161C),
          showDragHandle: true,
          isScrollControlled: true,
          builder: (_) => Padding(
              padding: const EdgeInsets.fromLTRB(16, 2, 16, 24),
              child: SingleChildScrollView(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(title,
                        style: const TextStyle(
                            fontSize: 18, fontWeight: FontWeight.w800)),
                    const SizedBox(height: 12),
                    body,
                    const SizedBox(height: 8),
                  ]))));

  Widget _info(String a, String b) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Expanded(
              child: Text(a,
                  style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)))),
          Flexible(
              child: Text(b,
                  textAlign: TextAlign.end,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFFC4CCD0))))
        ]),
      );

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(12),
        children: [
          const PulseSectionTitle('Storage Healer',
              'Analyze app-owned files → review → delete → verify'),
          PulseCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const PulseHeader(
                    'STORAGE INTELLIGENCE', 'Internal device storage'),
                const SizedBox(height: 10),
                Center(
                    child: SizedBox(
                        width: 145,
                        height: 145,
                        child: CustomPaint(
                            painter:
                                PulseDonutPainter(fraction: scan?.fraction ?? 0)))),
                const SizedBox(height: 12),
                _info('Scan state',
                    scanning ? 'Scanning…' : scan?.state ?? 'Not scanned'),
                _info('App-owned files', scan == null ? '—' : '${scan!.files}'),
                _info('App-owned size',
                    scan == null ? '—' : _formatBytes(scan!.bytes)),
                _info(
                    'Internal storage',
                    scan?.totalStorage == null || scan?.freeStorage == null
                        ? 'Storage size unavailable'
                        : 'Used ${_formatBytes(scan!.totalStorage! - scan!.freeStorage!)} / ${_formatBytes(scan!.totalStorage)}'),
                _info(
                    'Free storage',
                    scan?.freeStorage == null
                        ? 'Storage size unavailable'
                        : _formatBytes(scan!.freeStorage)),
                const SizedBox(height: 9),
                Row(children: [
                  Expanded(
                      child: FilledButton(
                          onPressed: scanning ? null : _scan,
                          child:
                              Text(scanning ? 'SCANNING…' : 'SCAN STORAGE'))),
                  const SizedBox(width: 8),
                  Expanded(
                      child: OutlinedButton(
                          onPressed: scanning ? null : _cleanup,
                          child: const Text('CLEANUP'))),
                ]),
                const SizedBox(height: 7),
                const Text(
                    'Device totals come from Android. App-owned file details are limited by Android storage access rules.',
                    style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                if (error != null) ...[
                  const SizedBox(height: 7),
                  Text(error!,
                      style: const TextStyle(
                          fontSize: 11, color: Color(0xFFE1B39A))),
                ],
              ]),
          ),
          
          if (scan != null && scan!.largeFiles.isNotEmpty) ...[
            const SizedBox(height: 8),
            PulseCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const PulseHeader('STORAGE HOGS', 'Files larger than 50MB'),
                const SizedBox(height: 10),
                ...scan!.largeFiles.map((file) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Row(children: [
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(file.name, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                        Text('${file.category} • ${_formatBytes(file.size)}', 
                            style: const TextStyle(fontSize: 10, color: Color(0xFF9BA7AF))),
                      ]),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, color: Color(0xFFE24A4A), size: 20),
                      onPressed: () async {
                        final currentContext = context;
                        final success = await StorageService.deleteFile(file.path);
                        if (!mounted || !currentContext.mounted) return;
                        if (success) {
                          _scan();
                          _show(currentContext, 'File Removed', Text('Successfully deleted ${file.name}'));
                        } else {
                          _show(currentContext, 'Error', const Text('Could not delete file. It may be in use or protected.'));
                        }
                      },
                    ),
                  ]),
                )),
                const SizedBox(height: 5),
                const Text('Deletions are permanent. Use with caution.', 
                    style: TextStyle(fontSize: 10, color: Color(0xFFE1B39A), fontStyle: FontStyle.italic)),
              ]),
            ),
          ],
          
          PulseCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const PulseHeader('CACHE', 'Detectable temporary files'),
                const SizedBox(height: 8),
                const Text(
                    'Only PulseOS-owned temporary files can be measured or removed. Other apps caches are protected by Android.',
                    style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                const SizedBox(height: 8),
                _info(
                    'Detected cache',
                    cacheBytes == null
                        ? 'Not scanned'
                        : '${_formatBytes(cacheBytes)} ($cacheFiles files)'),
                const SizedBox(height: 8),
                if (appCaches.isNotEmpty) ...[
                  const Text('APP CACHE BY APPLICATION', style: PulseStyles.kicker),
                  for (final app in appCaches)
                    CheckboxListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      value: selectedPackages.contains(app['package']),
                      title: Text('${app['label']}'),
                      subtitle: Text(
                          _formatBytes((app['cacheBytes'] as num).toInt())),
                      onChanged: (checked) => setState(() {
                        final packageName = app['package'] as String;
                        if (checked == true) {
                          selectedPackages.add(packageName);
                        } else {
                          selectedPackages.remove(packageName);
                        }
                      }),
                    ),
                  OutlinedButton(
                      onPressed: selectedPackages.length == 1
                          ? () => _openAppStorage(selectedPackages.first)
                          : null,
                      child: const Text('OPEN SELECTED APP STORAGE')),
                ],
                OutlinedButton(
                    onPressed: cacheScanning ? null : _reviewCache,
                    child: Text(cacheScanning ? 'SCANNING…' : 'CLEAR CACHE')),
                TextButton.icon(
                  onPressed: _openAllAppStorage,
                  icon: const Icon(Icons.delete_sweep_outlined),
                  label: const Text('DELETE ALL APP CACHE'),
                ),
              ])),
        ],
      );
}
