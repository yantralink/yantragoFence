/// Alert model — device alert notification.
class Alert {
  final String id;
  final String organizationId;
  final String deviceId;
  final String imei;
  final String alertType;
  final String severity;
  final String message;
  final bool acknowledged;
  final DateTime? acknowledgedAt;
  final DateTime createdAt;

  const Alert({
    required this.id,
    required this.organizationId,
    required this.deviceId,
    required this.imei,
    required this.alertType,
    required this.severity,
    required this.message,
    required this.acknowledged,
    this.acknowledgedAt,
    required this.createdAt,
  });

  factory Alert.fromJson(Map<String, dynamic> json) {
    return Alert(
      id: json['id'] as String,
      organizationId: json['organizationId'] as String,
      deviceId: json['deviceId'] as String,
      imei: json['imei'] as String,
      alertType: json['alertType'] as String,
      severity: json['severity'] as String? ?? 'INFO',
      message: json['message'] as String? ?? '',
      acknowledged: json['acknowledged'] as bool? ?? false,
      acknowledgedAt: json['acknowledgedAt'] != null
          ? DateTime.tryParse(json['acknowledgedAt'] as String)
          : null,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }

  bool get isCritical => severity == 'CRITICAL';
  bool get isWarning => severity == 'WARNING';
}
