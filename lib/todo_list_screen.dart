import 'package:flutter/material.dart';
import 'add_todo_screen.dart';
import 'todo_item.dart';
import 'database_helper.dart'; // Import the database helper

    class TodoListScreen extends StatefulWidget {
      @override
      _TodoListScreenState createState() => _TodoListScreenState();
    }

    class _TodoListScreenState extends State<TodoListScreen> {
      final dbHelper = DatabaseHelper();
      late Future<List<TodoItem>> _todosFuture;

      @override
      void initState() {
        super.initState();
        _todosFuture = _loadTodos();
      }

      Future<List<TodoItem>> _loadTodos() async {
        // Fetch to-dos from the database
        final db = await dbHelper.db;
        final List<Map<String, dynamic>> maps = await db!.query('Todo');
        return maps.map((map) => TodoItem.fromMap(map)).toList();
      }

      Future<void> _addTodo(TodoItem todo) async {
        // Insert the new to-do into the database
        final db = await dbHelper.db;
        await db!.insert('Todo', todo.toMap());
        // Refresh the list after adding
        setState(() {
          _todosFuture = _loadTodos();
        });
      }

      Future<void> _toggleTodoCompletion(TodoItem todo) async {
        final db = await dbHelper.db;
        await db!.update(
          'Todo',
          {'isCompleted': todo.isCompleted ? 0 : 1}, // Toggle completion status
          where: 'id = ?',
          whereArgs: [todo.id],
        );
        setState(() {
          _todosFuture = _loadTodos();
        });
      }

      @override
      Widget build(BuildContext context) {
        return Scaffold(
          appBar: AppBar(
            title: Text('To-Do List'),
          ),
          body: FutureBuilder<List<TodoItem>>(
            future: _todosFuture,
            builder: (context, snapshot) {
              if (snapshot.connectionState == ConnectionState.waiting) {
                return Center(child: CircularProgressIndicator());
              } else if (snapshot.hasError) {
                return Center(child: Text('Error loading to-dos: ${snapshot.error}'));
              } else if (snapshot.hasData) {
                final todos = snapshot.data!;
                if (todos.isEmpty) {
                  return Center(child: Text('No to-dos yet. Click the "+" to add one!'));
                }
                return ListView.builder(
                  itemCount: todos.length,
                  itemBuilder: (context, index) {
                    final todo = todos[index];
                    return Card(
                      margin: EdgeInsets.all(8.0),
                      child: ListTile(
                        title: Text(
                          todo.task,
                          style: TextStyle(
                            decoration: todo.isCompleted
                                ? TextDecoration.lineThrough
                                : TextDecoration.none,
                          ),
                        ),
                        leading: Checkbox(
                          value: todo.isCompleted,
                          onChanged: (bool? value) {
                            _toggleTodoCompletion(todo);
                          },
                        ),
                        // We'll add timer/reminder UI later
                      ),
                    );
                  },
                );
              } else {
                return Center(child: Text('No to-dos found.'));
              }
            },
          ),
          floatingActionButton: FloatingActionButton(
            child: Icon(Icons.add),
            onPressed: () async {
              // Navigate to AddTodoScreen and wait for the result
              final newTodo = await Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => AddTodoScreen()),
              );
              if (newTodo != null && newTodo is TodoItem) {
                // Handle the new todo item
                _addTodo(newTodo);
              }
            },
          ),
        );
      }
    }

