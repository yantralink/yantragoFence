/// Alert model — device alert notification.
class Alert {
  final String id;
  final String organizationId;
  final String? alertRuleId;
  final String? machineId;
  final String? deviceId;
  final String? imei;
  final String alertType;
  final String severity;
  final String message;
  final bool acknowledged;
  final DateTime? acknowledgedAt;
  final DateTime? triggeredAt;
  final DateTime createdAt;

  const Alert({
    required this.id,
    required this.organizationId,
    this.alertRuleId,
    this.machineId,
    this.deviceId,
    this.imei,
    required this.alertType,
    required this.severity,
    required this.message,
    required this.acknowledged,
    this.acknowledgedAt,
    this.triggeredAt,
    required this.createdAt,
  });

  factory Alert.fromJson(Map<String, dynamic> json) {
    return Alert(
      id: json['id'] as String,
      organizationId: json['organizationId'] as String,
      alertRuleId: json['alertRuleId'] as String?,
      machineId: json['machineId'] as String?,
      deviceId: json['deviceId'] as String?,
      imei: json['imei'] as String?,
      alertType: json['alertType'] as String,
      severity: json['severity'] as String? ?? 'INFO',
      message: json['message'] as String? ?? '',
      acknowledged: json['isAcknowledged'] as bool? ?? false,
      acknowledgedAt: json['acknowledgedAt'] != null
          ? DateTime.tryParse(json['acknowledgedAt'] as String)
          : null,
      triggeredAt: json['triggeredAt'] != null
          ? DateTime.tryParse(json['triggeredAt'] as String)
          : null,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }

  bool get isCritical => severity == 'CRITICAL';
  bool get isWarning => severity == 'WARNING';
}
