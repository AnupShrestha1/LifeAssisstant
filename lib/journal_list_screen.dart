import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'database_helper.dart'; // Import your database helper
import 'journal_entry.dart';
import 'new_journal_entry_screen.dart'; // Import the new entry screen
import 'view_journal_entry_screen.dart'; // Import the view entry screen

    class JournalListScreen extends StatefulWidget {
      @override
      _JournalListScreenState createState() => _JournalListScreenState();
    }

    class _JournalListScreenState extends State<JournalListScreen> {
      final dbHelper = DatabaseHelper();
      late Future<List<JournalEntry>> _journalsFuture;

      @override
      void initState() {
        super.initState();
        _journalsFuture = _loadJournals();
      }

      Future<List<JournalEntry>> _loadJournals() async {
        return await dbHelper.getJournals();
      }

      Future<void> _deleteJournalEntry(String id) async {
        await dbHelper.deleteJournal(id);
        setState(() {
          _journalsFuture = _loadJournals(); // Reload the journal list
        });
      }

      @override
      Widget build(BuildContext context) {
        return Scaffold(
          appBar: AppBar(title: Text('My Journal')),
          body: FutureBuilder<List<JournalEntry>>(
            future: _journalsFuture,
            builder: (context, snapshot) {
              if (snapshot.connectionState == ConnectionState.waiting) {
                return Center(child: CircularProgressIndicator());
              } else if (snapshot.hasError) {
                return Center(child: Text('Error loading journals: ${snapshot.error}'));
              } else if (snapshot.hasData) {
                final journals = snapshot.data!;
                if (journals.isEmpty) {
                  return Center(child: Text('No journal entries yet. Click the "+" to add one!'));
                }
                return ListView.builder(
                  itemCount: journals.length,
                  itemBuilder: (context, index) {
                    final entry = journals[index];
                    return Card(
                      margin: EdgeInsets.all(8.0),
                      child: ListTile(
                        title: Text(
                          entry.heading,
                          style: TextStyle(fontWeight: FontWeight.bold),
                        ),
                        subtitle: Text(DateFormat('yyyy-MM-dd – HH:mm').format(entry.date)),
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => ViewJournalEntryScreen(journal: entry),
                            ),
                          ).then((_) {
                            setState(() {
                              _journalsFuture = _loadJournals(); // Refresh after viewing/editing
                            });
                          });
                        },
                        trailing: IconButton(
                          icon: Icon(Icons.delete, color: Colors.red),
                          onPressed: () {
                            showDialog(
                              context: context,
                              builder: (BuildContext context) {
                                return AlertDialog(
                                  title: Text('Confirm Delete'),
                                  content: Text('Are you sure you want to delete this entry?'),
                                  actions: <Widget>[
                                    TextButton(
                                      onPressed: () => Navigator.of(context).pop(),
                                      child: Text('Cancel'),
                                    ),
                                    TextButton(
                                      onPressed: () {
                                        _deleteJournalEntry(entry.id);
                                        Navigator.of(context).pop();
                                      },
                                      child: Text('Delete', style: TextStyle(color: Colors.red)),
                                    ),
                                  ],
                                );
                              },
                            );
                          },
                        ),
                      ),
                    );
                  },
                );
              } else {
                return Center(child: Text('No journal entries found.'));
              }
            },
          ),
          floatingActionButton: FloatingActionButton(
            child: Icon(Icons.add),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => NewJournalEntryScreen()),
              ).then((_) {
                setState(() {
                  _journalsFuture = _loadJournals(); // Refresh the list after adding
                });
              });
            },
          ),
        );
      }
    }