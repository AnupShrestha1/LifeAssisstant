    class TodoItem {
      final String id;
      final String task;
      final bool isCompleted;
      final DateTime? createdAt;

      TodoItem({
        required this.id,
        required this.task,
        this.isCompleted = false,
        this.createdAt,
      });

      Map<String, dynamic> toMap() {
        return {
          'id': id,
          'task': task,
          'isCompleted': isCompleted ? 1 : 0,
          'createdAt': createdAt?.toIso8601String(),
        };
      }

      factory TodoItem.fromMap(Map<String, dynamic> map) {
        return TodoItem(
          id: map['id'],
          task: map['task'],
          isCompleted: map['isCompleted'] == 1,
          createdAt: map['createdAt'] != null ? DateTime.parse(map['createdAt']) : null,
        );
      }
    }

