import 'dart:io';
import 'package:archive/archive.dart';
import 'package:flutter/foundation.dart';
import 'package:image/image.dart' as img;
import 'package:pdf/widgets.dart' as pw;
import 'package:path_provider/path_provider.dart';

class ConverterMatrix {
  static const types = ['Document', 'Image', 'Archive', 'Media', 'Ebook'];
  static const outputs = <String, List<String>>{
    'Document': ['TXT', 'PDF', 'DOCX'],
    'Image': ['PNG', 'JPG', 'PDF'],
    'Archive': ['ZIP'],
    'Media': ['MP3', 'MP4'],
    'Ebook': ['PDF', 'EPUB'],
  };

  static bool canConvert(String input, String output) =>
      (input == 'Document' && output == 'TXT') ||
      (input == 'Image' && ['PNG', 'JPG', 'PDF'].contains(output)) ||
      (input == 'Archive' && output == 'ZIP');

  static List<String> supportedOutputs(String input) =>
      outputs[input]!.where((output) => canConvert(input, output)).toList();

  static String? detectType(String path) {
    final extension = _basename(path).toLowerCase().split('.').last;
    if (['txt', 'md', 'csv', 'json', 'xml', 'html'].contains(extension)) {
      return 'Document';
    }
    if (['zip'].contains(extension)) return 'Archive';
    if (['png', 'jpg', 'jpeg', 'webp'].contains(extension)) return 'Image';
    if (['mp3', 'wav', 'mp4', 'm4a'].contains(extension)) return 'Media';
    if (['epub', 'mobi'].contains(extension)) return 'Ebook';
    return null;
  }

  static String _basename(String path) {
    final normalized = path.replaceAll('\\', '/');
    return normalized.substring(normalized.lastIndexOf('/') + 1);
  }
}

class ConversionResult {
  final String path;
  const ConversionResult(this.path);
}

class ConversionService {
  static Future<ConversionResult> convert({
    required String path,
    required String inputType,
    required String outputFormat,
  }) async {
    if (!ConverterMatrix.canConvert(inputType, outputFormat)) {
      throw Exception(
          'Conversion is unavailable: no bundled codec supports $inputType → $outputFormat.');
    }
    final input = File(path);
    if (!await input.exists()) {
      throw Exception('The selected file no longer exists.');
    }
    final bytes = await input.readAsBytes();
    if (bytes.length > 100 * 1024 * 1024) {
      throw Exception('Files larger than 100 MB are not processed in memory.');
    }
    final name = _basename(path);
    final outputBytes = await _convertBytes(
        name: name,
        bytes: bytes,
        inputType: inputType,
        outputFormat: outputFormat);
    final extension = outputFormat.toLowerCase();
    final outputDirectory = await getApplicationDocumentsDirectory();
    final outputPath =
        '${outputDirectory.path}${Platform.pathSeparator}${_withoutExtension(name)}_converted.$extension';
    final output = File(outputPath);
    await output.writeAsBytes(outputBytes, flush: true);
    return ConversionResult(output.path);
  }

  static String _basename(String path) {
    final normalized = path.replaceAll('\\', '/');
    return normalized.substring(normalized.lastIndexOf('/') + 1);
  }

  static String _withoutExtension(String name) {
    final dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }
}

Future<List<int>> _convertBytes({
  required String name,
  required List<int> bytes,
  required String inputType,
  required String outputFormat,
}) async {
  if (outputFormat == 'ZIP') {
    return await compute(_zipBytesWrapper, {'name': name, 'bytes': bytes});
  }
  if (inputType == 'Image') {
    final decoded = img.decodeImage(Uint8List.fromList(bytes));
    if (decoded == null) {
      throw Exception('The selected image format could not be decoded.');
    }
    if (outputFormat == 'PNG') return img.encodePng(decoded);
    if (outputFormat == 'JPG') return img.encodeJpg(decoded, quality: 95);
    if (outputFormat == 'PDF') {
      final pngBytes = img.encodePng(decoded);
      final document = pw.Document();
      document.addPage(pw.Page(
          build: (_) => pw.Center(
              child:
                  pw.Image(pw.MemoryImage(pngBytes), fit: pw.BoxFit.contain))));
      return document.save();
    }
  }
  return bytes;
}

List<int> _zipBytesWrapper(Map<String, dynamic> args) {
  final String name = args['name'];
  final List<int> bytes = args['bytes'];
  final archive = Archive()..addFile(ArchiveFile(name, bytes.length, bytes));
  return ZipEncoder().encode(archive);
}
