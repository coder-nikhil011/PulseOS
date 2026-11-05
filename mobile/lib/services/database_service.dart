import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'dart:async';

class DatabaseService {
  static final DatabaseService _instance = DatabaseService._internal();
  factory DatabaseService() => _instance;
  Database? _database;

  DatabaseService._internal();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    String path = join(await getDatabasesPath(), 'pulseos_health.db');
    return await openDatabase(
      path,
      version: 3,
      onCreate: _onCreate,
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await db.execute('CREATE INDEX IF NOT EXISTS idx_telemetry_timestamp ON telemetry_samples(timestamp)');
          await db.execute('CREATE INDEX IF NOT EXISTS idx_health_events_active ON health_events(resolved, type, title)');
        }
        if (oldVersion < 3) {
          await db.execute('''
            CREATE TABLE IF NOT EXISTS cleanup_history(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              timestamp INTEGER,
              bytes_freed INTEGER,
              files_deleted INTEGER,
              category TEXT,
              note TEXT
            )
          ''');
          await db.execute('''
            CREATE TABLE IF NOT EXISTS conversion_history(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              timestamp INTEGER,
              source_name TEXT,
              target_name TEXT,
              output_path TEXT,
              size_bytes INTEGER,
              status TEXT
            )
          ''');
          await db.execute('CREATE INDEX IF NOT EXISTS idx_cleanup_timestamp ON cleanup_history(timestamp)');
          await db.execute('CREATE INDEX IF NOT EXISTS idx_conversion_timestamp ON conversion_history(timestamp)');
        }
      },
    );
  }

  Future<void> _onCreate(Database db, int version) async {
    // Table for raw telemetry samples
    await db.execute('''
      CREATE TABLE telemetry_samples(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        timestamp INTEGER,
        cpu REAL,
        ram_available INTEGER,
        ram_total INTEGER,
        battery_level REAL,
        battery_temp REAL,
        storage_free INTEGER,
        storage_total INTEGER,
        latency INTEGER
      )
    ''');

    await db.execute('CREATE INDEX IF NOT EXISTS idx_telemetry_timestamp ON telemetry_samples(timestamp)');

    // Table for recorded health events/problems
    await db.execute('''
      CREATE TABLE health_events(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        timestamp INTEGER,
        type TEXT,
        severity TEXT,
        title TEXT,
        message TEXT,
        resolved INTEGER
      )
    ''');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_health_events_active ON health_events(resolved, type, title)');

    // Table for storage cleanup history
    await db.execute('''
      CREATE TABLE cleanup_history(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        timestamp INTEGER,
        bytes_freed INTEGER,
        files_deleted INTEGER,
        category TEXT,
        note TEXT
      )
    ''');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_cleanup_timestamp ON cleanup_history(timestamp)');

    // Table for file conversion history
    await db.execute('''
      CREATE TABLE conversion_history(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        timestamp INTEGER,
        source_name TEXT,
        target_name TEXT,
        output_path TEXT,
        size_bytes INTEGER,
        status TEXT
      )
    ''');
    await db.execute('CREATE INDEX IF NOT EXISTS idx_conversion_timestamp ON conversion_history(timestamp)');
  }

  // --- Telemetry Operations ---

  Future<void> insertTelemetry(Map<String, dynamic> row) async {
    final db = await database;
    await db.insert('telemetry_samples', row);
  }

  Future<List<Map<String, dynamic>>> getRecentTelemetry(int limit) async {
    final db = await database;
    return await db.query(
      'telemetry_samples',
      orderBy: 'timestamp DESC',
      limit: limit,
    );
  }

  Future<List<Map<String, dynamic>>> getRecentEvents(int limit) async {
    final db = await database;
    return await db.query(
      'health_events',
      orderBy: 'timestamp DESC',
      limit: limit,
    );
  }

  /// Fetches telemetry data for a specific time range.
  /// [hours] defines how far back to look.
  Future<List<Map<String, dynamic>>> getTelemetryRange(int hours) async {
    final db = await database;
    final timestampThreshold = DateTime.now().millisecondsSinceEpoch - (hours * 60 * 60 * 1000);
    
    // We query and order by timestamp ASC for graph plotting
    return await db.query(
      'telemetry_samples',
      where: 'timestamp > ?',
      whereArgs: [timestampThreshold],
      orderBy: 'timestamp ASC',
    );
  }

  // --- Health Event Operations ---

  Future<void> insertOrRefreshEvent(Map<String, dynamic> row) async {
    final db = await database;
    final type = row['type']?.toString() ?? 'system';
    final title = row['title']?.toString() ?? 'Health alert';
    final existing = await db.query(
      'health_events',
      columns: ['id'],
      where: 'resolved = 0 AND type = ? AND title = ?',
      whereArgs: [type, title],
      limit: 1,
    );
    if (existing.isEmpty) {
      await db.insert('health_events', row);
    } else {
      await db.update(
        'health_events',
        {'timestamp': row['timestamp'], 'message': row['message'], 'severity': row['severity']},
        where: 'id = ?',
        whereArgs: [existing.first['id']],
      );
    }
  }

  Future<void> resolveActiveEventsExcept(Set<String> activeKeys) async {
    final db = await database;
    final active = await db.query(
      'health_events',
      columns: ['id', 'type', 'title'],
      where: 'resolved = 0',
    );
    for (final row in active) {
      final key = '${row['type']}::${row['title']}';
      if (!activeKeys.contains(key)) {
        await db.update(
          'health_events',
          {'resolved': 1},
          where: 'id = ?',
          whereArgs: [row['id']],
        );
      }
    }
  }

  Future<void> insertEvent(Map<String, dynamic> row) async {
    await insertOrRefreshEvent(row);
  }

  Future<List<Map<String, dynamic>>> getActiveEvents() async {
    final db = await database;
    return await db.query(
      'health_events',
      where: 'resolved = 0',
      orderBy: 'timestamp DESC',
    );
  }

  Future<void> resolveEvent(int id) async {
    final db = await database;
    await db.update(
      'health_events',
      {'resolved': 1},
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> clearOldData(int days) async {
    final db = await database;
    int threshold = DateTime.now().millisecondsSinceEpoch - (days * 24 * 60 * 60 * 1000);
    await db.delete('telemetry_samples', where: 'timestamp < ?', whereArgs: [threshold]);
  }

  // --- Storage Cleanup History ---

  Future<void> insertCleanupRecord({
    required int bytesFreed,
    required int filesDeleted,
    required String category,
    String? note,
  }) async {
    final db = await database;
    await db.insert('cleanup_history', {
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'bytes_freed': bytesFreed,
      'files_deleted': filesDeleted,
      'category': category,
      'note': note ?? 'Safe storage cleanup',
    });
  }

  Future<List<Map<String, dynamic>>> getRecentCleanups({int limit = 10}) async {
    final db = await database;
    return await db.query(
      'cleanup_history',
      orderBy: 'timestamp DESC',
      limit: limit,
    );
  }

  // --- Conversion History ---

  Future<void> insertConversionRecord({
    required String sourceName,
    required String targetName,
    required String outputPath,
    required int sizeBytes,
    String status = 'Complete',
  }) async {
    final db = await database;
    await db.insert('conversion_history', {
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'source_name': sourceName,
      'target_name': targetName,
      'output_path': outputPath,
      'size_bytes': sizeBytes,
      'status': status,
    });
  }

  Future<List<Map<String, dynamic>>> getRecentConversions({int limit = 20}) async {
    final db = await database;
    return await db.query(
      'conversion_history',
      orderBy: 'timestamp DESC',
      limit: limit,
    );
  }

  Future<List<Map<String, dynamic>>> searchConversions(String query) async {
    final db = await database;
    if (query.trim().isEmpty) return getRecentConversions();
    return await db.query(
      'conversion_history',
      where: 'source_name LIKE ? OR target_name LIKE ?',
      whereArgs: ['%$query%', '%$query%'],
      orderBy: 'timestamp DESC',
      limit: 30,
    );
  }

  // --- Clear / Maintenance Operations ---

  Future<void> clearTelemetrySamples() async {
    final db = await database;
    await db.delete('telemetry_samples');
  }

  Future<void> clearAllHistory() async {
    final db = await database;
    await db.delete('telemetry_samples');
    await db.delete('health_events');
    await db.delete('cleanup_history');
    await db.delete('conversion_history');
  }
}
