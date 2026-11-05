import 'dart:io';
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import '../services/converter_service.dart';
import '../services/database_service.dart';
import '../widgets/common_widgets.dart';

class ConverterPage extends StatefulWidget {
  const ConverterPage({super.key});

  @override
  State<ConverterPage> createState() => _ConverterPageState();
}

class _ConverterPageState extends State<ConverterPage> {
  String selected = 'Document';
  String format = 'TXT';
  List<String> selectedPaths = [];
  bool running = false;
  String? status;
  String? latestOutputFile;
  final TextEditingController _historySearchCtrl = TextEditingController();
  List<Map<String, dynamic>> conversionHistory = const [];

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  @override
  void dispose() {
    _historySearchCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadHistory([String query = '']) async {
    try {
      final h = await DatabaseService().searchConversions(query);
      if (mounted) setState(() => conversionHistory = h);
    } catch (_) {}
  }

  Future<void> _chooseFiles() async {
    final result = await FilePicker.platform.pickFiles(withData: false, allowMultiple: true);
    if (!mounted || result == null || result.paths.isEmpty) return;
    final paths = result.paths.whereType<String>().toList();
    if (paths.isEmpty) return;

    final first = paths.first;
    final detected = ConverterMatrix.detectType(first);

    setState(() {
      selectedPaths = paths;
      if (detected != null) {
        selected = detected;
        final supported = ConverterMatrix.supportedOutputs(detected);
        format = supported.isEmpty ? '' : supported.first;
      }
      status = null;
      latestOutputFile = null;
    });
  }

  Future<void> _convert() async {
    if (selectedPaths.isEmpty) return;
    setState(() {
      running = true;
      status = null;
    });

    var successCount = 0;
    String lastPath = '';

    for (final path in selectedPaths) {
      try {
        final output = await ConversionService.convert(
          path: path,
          inputType: selected,
          outputFormat: format,
        );
        successCount++;
        lastPath = output.path;

        // Persist to SQLite
        final sourceName = _basename(path);
        final targetName = _basename(output.path);
        final file = File(output.path);
        final size = await file.exists() ? await file.length() : 0;

        await DatabaseService().insertConversionRecord(
          sourceName: sourceName,
          targetName: targetName,
          outputPath: output.path,
          sizeBytes: size,
          status: 'Complete',
        );
      } catch (e) {
        status = e.toString().replaceFirst('Exception: ', '');
      }
    }

    await _loadHistory();

    if (mounted) {
      setState(() {
        running = false;
        latestOutputFile = lastPath.isNotEmpty ? lastPath : null;
        if (successCount > 0) {
          status = 'Converted $successCount file(s) successfully!';
        }
      });
    }
  }

  String _basename(String path) {
    final normalized = path.replaceAll('\\', '/');
    return normalized.substring(normalized.lastIndexOf('/') + 1);
  }

  String _formatBytes(int? bytes) {
    if (bytes == null || bytes <= 0) return '0 B';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
      children: [
        const PulseSectionTitle('Offline Converter',
            'Real on-device batch conversion for images, documents and archives'),

        // Converter Studio Card
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('CONVERT STUDIO', 'Configure format workflow'),
              const SizedBox(height: 10),
              DropdownButtonFormField<String>(
                initialValue: selected,
                items: ConverterMatrix.types
                    .map((x) => DropdownMenuItem(value: x, child: Text(x)))
                    .toList(),
                onChanged: running
                    ? null
                    : (v) => setState(() {
                          selected = v!;
                          final supported = ConverterMatrix.supportedOutputs(v);
                          format = supported.isEmpty ? '' : supported.first;
                          status = null;
                        }),
                decoration: const InputDecoration(labelText: 'Source Category'),
              ),
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(
                initialValue: format.isEmpty ? null : format,
                items: ConverterMatrix.supportedOutputs(selected)
                    .map((x) => DropdownMenuItem(
                          value: x,
                          enabled: ConverterMatrix.canConvert(selected, x),
                          child: Text(ConverterMatrix.canConvert(selected, x)
                              ? x
                              : '$x (unavailable)'),
                        ))
                    .toList(),
                onChanged: running || format.isEmpty ? null : (v) => setState(() => format = v!),
                decoration: const InputDecoration(labelText: 'Target Format'),
              ),
              const SizedBox(height: 8),
              Text(
                ConverterMatrix.canConvert(selected, format)
                    ? 'Engine: Local offline ${format == 'ZIP' ? 'archive compression' : 'format translation'}'
                    : 'Unavailable: Codec not bundled for this specific pair',
                style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)),
              ),
              const SizedBox(height: 12),
              if (selectedPaths.isNotEmpty)
                Text(
                  selectedPaths.length == 1
                      ? 'Selected: ${_basename(selectedPaths.first)}'
                      : 'Selected: ${selectedPaths.length} files (Batch mode)',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF6ED1CD)),
                ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: running ? null : _chooseFiles,
                      icon: const Icon(Icons.file_open_rounded, size: 16),
                      label: Text(selectedPaths.isEmpty ? 'CHOOSE FILES' : 'CHANGE FILES'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: running || selectedPaths.isEmpty ? null : _convert,
                      icon: running
                          ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                          : const Icon(Icons.swap_horiz_rounded, size: 18),
                      label: Text(running ? 'CONVERTING…' : 'START CONVERT'),
                    ),
                  ),
                ],
              ),
              if (running) ...[
                const SizedBox(height: 12),
                const LinearProgressIndicator(),
                const SizedBox(height: 4),
                const Text('Processing in background thread…', style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
              ],
              if (status != null) ...[
                const SizedBox(height: 10),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFF161E26),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: const Color(0xFF243644)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(status!, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Color(0xFF7FE0B2))),
                      if (latestOutputFile != null) ...[
                        const SizedBox(height: 4),
                        Text('Saved at: $latestOutputFile', style: const TextStyle(fontSize: 10, color: Color(0xFF9BA7AF))),
                      ],
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Conversion History & Search
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const PulseHeader('CONVERSION HISTORY', 'Searchable local conversion records'),
                  if (conversionHistory.isNotEmpty)
                    Badge(
                      backgroundColor: const Color(0xFF6ED1CD),
                      textColor: Colors.black,
                      label: Text('${conversionHistory.length}'),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _historySearchCtrl,
                onChanged: _loadHistory,
                decoration: InputDecoration(
                  hintText: 'Search past conversions…',
                  prefixIcon: const Icon(Icons.search_rounded, size: 16),
                  filled: true,
                  fillColor: const Color(0xFF0C1217),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none),
                ),
              ),
              const SizedBox(height: 8),
              if (conversionHistory.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8),
                  child: Text('No recorded conversions match this filter.', style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                )
              else
                ...conversionHistory.take(8).map((h) => Container(
                      margin: const EdgeInsets.only(bottom: 6),
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: const Color(0xFF0C1217),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: const Color(0xFF1F2B36)),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.check_circle_outline_rounded, size: 16, color: Color(0xFF6ED1CD)),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('${h['source_name']} → ${h['target_name']}', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                                Text(_formatBytes(h['size_bytes'] as int?), style: const TextStyle(fontSize: 10, color: Color(0xFF9BA7AF))),
                              ],
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(color: const Color(0x336ED1CD), borderRadius: BorderRadius.circular(4)),
                            child: const Text('Saved', style: TextStyle(fontSize: 9, color: Color(0xFF6ED1CD), fontWeight: FontWeight.bold)),
                          ),
                        ],
                      ),
                    )),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Matrix & Codec Specifications
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('BUNDLED CODECS', 'Offline supported matrix'),
              const SizedBox(height: 6),
              for (final entry in ConverterMatrix.outputs.entries)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Row(
                    children: [
                      SizedBox(width: 80, child: Text(entry.key, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700))),
                      Expanded(
                        child: Text(
                          ConverterMatrix.supportedOutputs(entry.key).isEmpty
                              ? 'Unavailable on mobile'
                              : ConverterMatrix.supportedOutputs(entry.key).join(', '),
                          style: const TextStyle(fontSize: 11, color: Color(0xFF6ED1CD)),
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
