import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io' as io;
import 'journal_entry.dart';
import 'package:flutter/foundation.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

class DatabaseHelper {
  static Database? _db;

  Future<Database?> get db async {
    if (_db != null) {
      return _db;
    }
    _db = await initDb();
    return _db;
  }

  initDb() async {
    if (io.Platform.isWindows || kIsWeb) {
      // Initialize FFI for Windows and Web
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
    }
    io.Directory documentsDirectory = await getApplicationDocumentsDirectory();
    String path = join(documentsDirectory.path, "journal.db");
    var theDb = await openDatabase(path, version: 1, onCreate: _onCreate);
    return theDb;
  }

  void _onCreate(Database db, int version) async {
    await db.execute(
        "CREATE TABLE Journal(id TEXT PRIMARY KEY, date TEXT, heading TEXT, content TEXT)");
  }

  Future<int> saveJournal(JournalEntry journal) async {
    var dbClient = await db;
    return await dbClient!.insert('Journal', journal.toMap());
  }

  Future<List<JournalEntry>> getJournals() async {
    var dbClient = await db;
    List<Map<String, dynamic>> maps = await dbClient!.query('Journal', orderBy: 'date DESC');
    return maps.map((map) => JournalEntry.fromMap(map)).toList();
  }

  Future<int> deleteJournal(String id) async {
    var dbClient = await db;
    return await dbClient!.delete('Journal', where: 'id = ?', whereArgs: [id]);
  }

  Future<int> updateJournal(JournalEntry journal) async {
    var dbClient = await db;
    return await dbClient!.update('Journal', journal.toMap(), where: 'id = ?', whereArgs: [journal.id]);
  }
}