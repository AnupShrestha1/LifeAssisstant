import 'package:flutter/material.dart';
import 'home_screen.dart'; // Import your HomeScreen

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'My Journal App', // Change the title to your app's name
      theme: ThemeData(
        primarySwatch: Colors.blue, // You can customize the theme here
        //useMaterial3: true, // If you want to use Material 3
      ),
      home: HomeScreen(), // Set HomeScreen as the home
    );
  }
}