import 'package:flutter/material.dart';
import 'journal_list_screen.dart'; // Import the journal list screen

    class HomeScreen extends StatelessWidget {
      @override
      Widget build(BuildContext context) {
        return Scaffold(
          appBar: AppBar(title: Text('My App')),
          body: Padding(
            padding: const EdgeInsets.all(16.0),
            child: GridView.count(
              crossAxisCount: 2,
              crossAxisSpacing: 16.0,
              mainAxisSpacing: 16.0,
              children: [
                _buildFeatureCard(context, 'Journal', Icons.book, JournalListScreen()),
                // Add other feature cards here (To-do, Calendar, etc.)
              ],
            ),
          ),
        );
      }

      Widget _buildFeatureCard(BuildContext context, String title, IconData icon, Widget page) {
        return Card(
          child: InkWell(
            onTap: () {
              Navigator.push(context, MaterialPageRoute(builder: (context) => page));
            },
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(icon, size: 48.0),
                SizedBox(height: 8.0),
                Text(title),
              ],
            ),
          ),
        );
      }
    }