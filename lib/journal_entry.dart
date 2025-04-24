class JournalEntry {
      String id;
      DateTime date;
      String heading;
      String content;

      JournalEntry({
        required this.id,
        required this.date,
        required this.heading,
        required this.content,
      });

      Map<String, dynamic> toMap() {
        return {
          'id': id,
          'date': date.toIso8601String(),
          'heading': heading,
          'content': content,
        };
      }

      factory JournalEntry.fromMap(Map<String, dynamic> map) {
        return JournalEntry(
          id: map['id'],
          date: DateTime.parse(map['date']),
          heading: map['heading'],
          content: map['content'],
        );
      }
    }