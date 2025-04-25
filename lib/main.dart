    import 'package:flutter/material.dart';
    import 'home_screen.dart';
    import 'database_helper.dart';

    void main() async {
      WidgetsFlutterBinding.ensureInitialized();
      final dbHelper = DatabaseHelper();
      await dbHelper.db; // Ensure database is initialized
      dbHelper.printDbPath(); // Call printDbPath()
      runApp(const MyApp());
    }

    class MyApp extends StatelessWidget {
      const MyApp({super.key});

      @override
      Widget build(BuildContext context) {
        return MaterialApp(
          title: 'My Journal App',
          theme: ThemeData(
            primarySwatch: Colors.blue,
          ),
          home: HomeScreen(),
        );
      }
    }
