import 'package:flutter/material.dart';
import 'journal_entry.dart';
import 'package:intl/intl.dart';

    class ViewJournalEntryScreen extends StatelessWidget {
      final JournalEntry journal;

      ViewJournalEntryScreen({required this.journal});

      @override
      Widget build(BuildContext context) {
        return Scaffold(
          appBar: AppBar(title: Text('View Entry')),
          body: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  journal.heading,
                  style: TextStyle(fontSize: 24.0, fontWeight: FontWeight.bold),
                ),
                SizedBox(height: 8.0),
                Text(
                  DateFormat('yyyy-MM-dd – HH:mm').format(journal.date),
                  style: TextStyle(color: Colors.grey),
                ),
                SizedBox(height: 16.0),
                Expanded(
                  child: SingleChildScrollView(
                    child: Text(journal.content),
                  ),
                ),
              ],
            ),
          ),
        );
      }
    }