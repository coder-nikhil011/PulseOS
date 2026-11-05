class PulseAlert {
  final String id;
  final String type;
  final String severity;
  final String title;
  final String message;
  final String actionLabel;
  final String actionTarget;
  final DateTime createdAt;
  final bool resolved;

  const PulseAlert({
    required this.id,
    required this.type,
    required this.severity,
    required this.title,
    required this.message,
    required this.actionLabel,
    required this.actionTarget,
    required this.createdAt,
    this.resolved = false,
  });
}
