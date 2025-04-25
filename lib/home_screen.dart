    import 'package:flutter/material.dart';
    import 'journal_list_screen.dart';
    import 'todo_list_screen.dart';

    class HomeScreen extends StatelessWidget {
      @override
      Widget build(BuildContext context) {
        return Scaffold(
          appBar: AppBar(title: Text('My Journal App')),
          body: _buildBody(context),
        );
      }

      Widget _buildBody(BuildContext context) {
        return Padding(
          padding: const EdgeInsets.all(16.0),
          child: GridView.count(
            crossAxisCount: 2, // Number of columns in the grid
            mainAxisSpacing: 16.0, // Spacing between rows
            crossAxisSpacing: 16.0, // Spacing between columns
            children: <Widget>[
              _buildJournalCard(context),
              _buildTodoListCard(context),
              // Add more cards for other features as needed
            ],
          ),
        );
      }

      Widget _buildJournalCard(BuildContext context) {
        return GestureDetector(
          onTap: () {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => JournalListScreen()),
            );
          },
          child: Card(
            elevation: 4,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center, // Vertically center content
                children: <Widget>[
                  Text(
                    "Journal",
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                  SizedBox(height: 8),
                  Text("View and manage your journal entries."),
                ],
              ),
            ),
          ),
        );
      }

      Widget _buildTodoListCard(BuildContext context) {
        return GestureDetector(
          onTap: () {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => TodoListScreen()),
            );
          },
          child: Card(
            elevation: 4,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center, // Vertically center content
                children: <Widget>[
                  Text(
                    "To-Do List",
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                  SizedBox(height: 8),
                  Text("Manage your to-do items."),
                ],
              ),
            ),
          ),
        );
      }
    }

