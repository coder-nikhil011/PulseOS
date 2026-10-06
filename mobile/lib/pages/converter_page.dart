import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import '../services/converter_service.dart';
import '../widgets/common_widgets.dart';

class ConverterPage extends StatefulWidget {
  const ConverterPage({super.key});

  @override
  State<ConverterPage> createState() => _ConverterPageState();
}

class _ConverterPageState extends State<ConverterPage> {
  String selected = 'Document';
  String format = 'TXT';
  String? selectedPath;
  bool running = false;
  String? status;

  Future<void> _chooseFile() async {
    final result = await FilePicker.platform
        .pickFiles(withData: false, allowMultiple: false);
    if (!mounted || result == null || result.files.single.path == null) return;
    final path = result.files.single.path!;
    final detected = ConverterMatrix.detectType(path);
    setState(() {
      selectedPath = path;
      if (detected != null) {
        selected = detected;
        final supported = ConverterMatrix.supportedOutputs(detected);
        format = supported.isEmpty ? '' : supported.first;
      }
      status = null;
    });
  }

  Future<void> _convert() async {
    final path = selectedPath;
    if (path == null) return;
    setState(() {
      running = true;
      status = null;
    });
    try {
      final output = await ConversionService.convert(
          path: path, inputType: selected, outputFormat: format);
      if (mounted) setState(() => status = 'Saved ${_basename(output.path)}');
    } catch (error) {
      if (mounted) {
        setState(
            () => status = error.toString().replaceFirst('Exception: ', ''));
      }
    } finally {
      if (mounted) setState(() => running = false);
    }
  }

  String _basename(String path) {
    final normalized = path.replaceAll('\\', '/');
    return normalized.substring(normalized.lastIndexOf('/') + 1);
  }

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(12),
        children: [
          const PulseSectionTitle('Offline Converter',
              'Real local conversions with an explicit compatibility matrix'),
          PulseCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const PulseHeader('CONVERT', 'Choose a workflow'),
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
                            final supported =
                                ConverterMatrix.supportedOutputs(v);
                            format = supported.isEmpty ? '' : supported.first;
                            status = null;
                          }),
                  decoration: const InputDecoration(labelText: 'Input type'),
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
                  onChanged: running || format.isEmpty
                      ? null
                      : (v) => setState(() => format = v!),
                  decoration: const InputDecoration(labelText: 'Output format'),
                ),
                const SizedBox(height: 8),
                Text(
                    ConverterMatrix.canConvert(selected, format)
                        ? 'Supported: local ${format == 'ZIP' ? 'archive creation' : 'text export'}'
                        : 'Unavailable: no bundled codec for this conversion',
                    style: const TextStyle(
                        fontSize: 11, color: Color(0xFF9BA7AF))),
                const SizedBox(height: 12),
                if (selectedPath != null)
                  Text('Selected: ${_basename(selectedPath!)}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 11)),
                const SizedBox(height: 5),
                LayoutBuilder(builder: (context, constraints) {
                  final vertical = constraints.maxWidth < 360;
                  final buttons = [
                    OutlinedButton(
                        onPressed: running ? null : _chooseFile,
                        child: const Text('CHOOSE FILE')),
                    FilledButton(
                        onPressed:
                            running || selectedPath == null ? null : _convert,
                        child: Text(running ? 'CONVERTING…' : 'CONVERT')),
                  ];
                  return vertical
                      ? Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            buttons[0],
                            buttons[1],
                          ],
                        )
                      : Row(
                          children: [
                            Expanded(child: buttons[0]),
                            const SizedBox(width: 8),
                            Expanded(child: buttons[1]),
                          ],
                        );
                }),
                if (running) ...[
                  const SizedBox(height: 10),
                  const LinearProgressIndicator(),
                  const SizedBox(height: 6),
                  const Text('Processing off the UI isolate…',
                      style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)))
                ],
                if (status != null) ...[
                  const SizedBox(height: 8),
                  Text(status!,
                      style: const TextStyle(
                          fontSize: 11, color: Color(0xFFBFD0C4))),
                ],
              ])),
          PulseCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const PulseHeader('COMPATIBILITY', 'Bundled conversions'),
                const SizedBox(height: 6),
                for (final entry in ConverterMatrix.outputs.entries)
                  Padding(
                      padding: const EdgeInsets.symmetric(vertical: 3),
                      child: Text(
                          '${entry.key}: ${ConverterMatrix.supportedOutputs(entry.key).isEmpty ? 'No supported conversions' : ConverterMatrix.supportedOutputs(entry.key).join(', ')}',
                          style: const TextStyle(fontSize: 11))),
                const SizedBox(height: 5),
                const Text(
                    'Files remain on-device. Only conversions listed above are implemented and selectable.',
                    style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
              ])),
        ],
      );
}
