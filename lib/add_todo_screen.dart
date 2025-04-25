import 'package:flutter/material.dart';
import 'todo_item.dart';

    class AddTodoScreen extends StatefulWidget {
      @override
      _AddTodoScreenState createState() => _AddTodoScreenState();
    }

    class _AddTodoScreenState extends State<AddTodoScreen> {
      final _taskController = TextEditingController();

      @override
      Widget build(BuildContext context) {
        return Scaffold(
          appBar: AppBar(title: Text('Add New To-Do')),
          body: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                TextField(
                  controller: _taskController,
                  decoration: InputDecoration(labelText: 'To-Do Task'),
                ),
                SizedBox(height: 24.0),
                ElevatedButton(
                  onPressed: () {
                    if (_taskController.text.isNotEmpty) {
                      final newTodo = TodoItem(
                        id: DateTime.now().millisecondsSinceEpoch.toString(),
                        task: _taskController.text,
                      );
                      Navigator.pop(context, newTodo); // Pass the new todo back to the list screen
                    } else {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Please enter a to-do task.')),
                      );
                    }
                  },
                  child: Text('Save'),
                ),
              ],
            ),
          ),
        );
      }
    }
