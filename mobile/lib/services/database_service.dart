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
      version: 1,
      onCreate: _onCreate,
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

  Future<void> insertEvent(Map<String, dynamic> row) async {
    final db = await database;
    await db.insert('health_events', row);
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
}
