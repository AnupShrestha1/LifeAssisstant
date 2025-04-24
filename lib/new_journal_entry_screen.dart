import 'package:flutter/material.dart';
    import 'database_helper.dart';
    import 'journal_entry.dart';
    import 'package:intl/intl.dart';

    class NewJournalEntryScreen extends StatefulWidget {
      @override
      _NewJournalEntryScreenState createState() => _NewJournalEntryScreenState();
    }

    class _NewJournalEntryScreenState extends State<NewJournalEntryScreen> {
      final _headingController = TextEditingController();
      final _contentController = TextEditingController();
      DateTime? _selectedDate = DateTime.now();
      final _dbHelper = DatabaseHelper();
      String? _selectedMoodEmoji; // To store the selected mood emoji

      final List<String> _moodEmojis = ['😊', '😞', '😠', '😨', '🤢']; // Happy, Sad, Angry, Scared, Disgust

      Future<void> _selectDate(BuildContext context) async {
        final DateTime? picked = await showDatePicker(
          context: context,
          initialDate: _selectedDate ?? DateTime.now(),
          firstDate: DateTime(2000),
          lastDate: DateTime(2101),
        );
        if (picked != null && picked != _selectedDate) {
          setState(() {
            _selectedDate = picked;
          });
        }
      }

      Future<void> _saveJournalEntry() async {
        if (_headingController.text.isNotEmpty && _contentController.text.isNotEmpty && _selectedDate != null) {
          final headingWithMood = _selectedMoodEmoji != null
              ? '${_headingController.text} ${_selectedMoodEmoji!}'
              : _headingController.text;

          final newEntry = JournalEntry(
            id: DateTime.now().millisecondsSinceEpoch.toString(),
            date: _selectedDate!,
            heading: headingWithMood, // Include the mood emoji in the heading
            content: _contentController.text,
          );
          await _dbHelper.saveJournal(newEntry);
          Navigator.pop(context); // Go back to the journal list
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Please fill in all fields and select a date.')),
          );
        }
      }

      Widget _buildMoodEmojiOption(String emoji) {
        return GestureDetector(
          onTap: () {
            setState(() {
              _selectedMoodEmoji = emoji;
            });
          },
          child: Container(
            padding: const EdgeInsets.all(8.0),
            decoration: BoxDecoration(
              border: Border.all(
                color: _selectedMoodEmoji == emoji ? Colors.blue : Colors.transparent,
                width: 2.0,
              ),
              borderRadius: BorderRadius.circular(8.0),
            ),
            child: Text(
              emoji,
              style: const TextStyle(fontSize: 24.0),
            ),
          ),
        );
      }

      @override
      Widget build(BuildContext context) {
        return Scaffold(
          appBar: AppBar(title: Text('Add New Entry')),
          body: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                TextField(
                  controller: _headingController,
                  decoration: const InputDecoration(labelText: 'Heading'),
                ),
                const SizedBox(height: 16.0),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: _moodEmojis.map((emoji) => _buildMoodEmojiOption(emoji)).toList(),
                ),
                const SizedBox(height: 16.0),
                InkWell(
                  onTap: () => _selectDate(context),
                  child: InputDecorator(
                    decoration: const InputDecoration(labelText: 'Date'),
                    child: Text(
                      _selectedDate != null ? DateFormat('yyyy-MM-dd').format(_selectedDate!) : 'Select Date',
                    ),
                  ),
                ),
                const SizedBox(height: 16.0),
                Expanded(
                  child: TextField(
                    controller: _contentController,
                    decoration: const InputDecoration(labelText: 'Content', border: OutlineInputBorder()),
                    maxLines: null,
                    keyboardType: TextInputType.multiline,
                  ),
                ),
                const SizedBox(height: 24.0),
                ElevatedButton(
                  onPressed: _saveJournalEntry,
                  child: const Text('Save'),
                ),
              ],
            ),
          ),
        );
      }
    }