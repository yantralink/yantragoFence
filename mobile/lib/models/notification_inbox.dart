import '../core/utils/date_utils.dart';

/// Notification inbox item — one per alert transition per recipient.
///
/// Matches the backend notification_inbox table (V24 + V25 migration).
/// Per notification plan Phase 4: inbox/active-alert views, badge, filters,
/// authorized acknowledgement.
class NotificationInbox {
  final String id;
  final String organizationId;
  final String userId;
  final String? alertId;
  final String eventId;
  final String alertType;
  final String severity;
  final String incidentState;
  final String title;
  final String body;
  final String? machineId;
  final double? observedValue;
  final String? observedUnit;
  final String locale;
  final int templateVersion;
  final bool isRead;
  final DateTime? readAt;
  final String? recipientCustomerId;
  final String? recipientCustomerName;
  final bool isAcknowledged;
  final DateTime? acknowledgedAt;
  final DateTime createdAt;
  final DateTime updatedAt;

  const NotificationInbox({
    required this.id,
    required this.organizationId,
    required this.userId,
    this.alertId,
    required this.eventId,
    required this.alertType,
    required this.severity,
    required this.incidentState,
    required this.title,
    required this.body,
    this.machineId,
    this.observedValue,
    this.observedUnit,
    required this.locale,
    required this.templateVersion,
    required this.isRead,
    this.readAt,
    this.recipientCustomerId,
    this.recipientCustomerName,
    required this.isAcknowledged,
    this.acknowledgedAt,
    required this.createdAt,
    required this.updatedAt,
  });

  factory NotificationInbox.fromJson(Map<String, dynamic> json) {
    return NotificationInbox(
      id: json['id'] as String,
      organizationId: json['organizationId'] as String,
      userId: json['userId'] as String,
      alertId: json['alertId'] as String?,
      eventId: json['eventId'] as String,
      alertType: json['alertType'] as String,
      severity: json['severity'] as String? ?? 'INFO',
      incidentState: json['incidentState'] as String? ?? 'OPEN',
      title: json['title'] as String? ?? '',
      body: json['body'] as String? ?? '',
      machineId: json['machineId'] as String?,
      observedValue: (json['observedValue'] as num?)?.toDouble(),
      observedUnit: json['observedUnit'] as String?,
      locale: json['locale'] as String? ?? 'en',
      templateVersion: json['templateVersion'] as int? ?? 1,
      isRead: json['isRead'] as bool? ?? false,
      readAt: AppDateUtils.parse(json['readAt'] as String?),
      recipientCustomerId: json['recipientCustomerId'] as String?,
      recipientCustomerName: json['recipientCustomerName'] as String?,
      isAcknowledged: json['isAcknowledged'] as bool? ?? false,
      acknowledgedAt: AppDateUtils.parse(json['acknowledgedAt'] as String?),
      createdAt: AppDateUtils.parseStrict(json['createdAt'] as String),
      updatedAt: AppDateUtils.parseStrict(json['updatedAt'] as String),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'organizationId': organizationId,
        'userId': userId,
        'alertId': alertId,
        'eventId': eventId,
        'alertType': alertType,
        'severity': severity,
        'incidentState': incidentState,
        'title': title,
        'body': body,
        'machineId': machineId,
        'observedValue': observedValue,
        'observedUnit': observedUnit,
        'locale': locale,
        'templateVersion': templateVersion,
        'isRead': isRead,
        'readAt': readAt?.toIso8601String(),
        'recipientCustomerId': recipientCustomerId,
        'recipientCustomerName': recipientCustomerName,
        'isAcknowledged': isAcknowledged,
        'acknowledgedAt': acknowledgedAt?.toIso8601String(),
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
      };

  /// Copy with isRead and readAt updated (for mark-read optimistic updates).
  NotificationInbox copyWithReadState({required bool read, DateTime? readAt}) {
    return NotificationInbox(
      id: id,
      organizationId: organizationId,
      userId: userId,
      alertId: alertId,
      eventId: eventId,
      alertType: alertType,
      severity: severity,
      incidentState: incidentState,
      title: title,
      body: body,
      machineId: machineId,
      observedValue: observedValue,
      observedUnit: observedUnit,
      locale: locale,
      templateVersion: templateVersion,
      isRead: read,
      readAt: readAt,
      recipientCustomerId: recipientCustomerId,
      recipientCustomerName: recipientCustomerName,
      isAcknowledged: isAcknowledged,
      acknowledgedAt: acknowledgedAt,
      createdAt: createdAt,
      updatedAt: DateTime.now(),
    );
  }

  /// Copy with acknowledged state updated.
  NotificationInbox copyWithAcknowledged({required bool acknowledged, DateTime? acknowledgedAt}) {
    return NotificationInbox(
      id: id,
      organizationId: organizationId,
      userId: userId,
      alertId: alertId,
      eventId: eventId,
      alertType: alertType,
      severity: severity,
      incidentState: incidentState,
      title: title,
      body: body,
      machineId: machineId,
      observedValue: observedValue,
      observedUnit: observedUnit,
      locale: locale,
      templateVersion: templateVersion,
      isRead: acknowledged ? true : isRead,
      readAt: acknowledged ? (readAt ?? acknowledgedAt) : readAt,
      recipientCustomerId: recipientCustomerId,
      recipientCustomerName: recipientCustomerName,
      isAcknowledged: acknowledged,
      acknowledgedAt: acknowledgedAt,
      createdAt: createdAt,
      updatedAt: DateTime.now(),
    );
  }

  bool get isOpen => incidentState == 'OPEN';
  bool get isResolved => incidentState == 'RESOLVED';
  bool get isEscalated => incidentState == 'ESCALATED';
  bool get isCritical => severity == 'CRITICAL';
  bool get isWarning => severity == 'WARNING';
  bool get isInfo => severity == 'INFO';

  /// Command lifecycle notification types from relay ON/OFF commands.
  bool get isCommand =>
      alertType == 'COMMAND_ACK' ||
      alertType == 'MACHINE_ON' ||
      alertType == 'MACHINE_OFF' ||
      alertType == 'COMMAND_FAILED';

  /// Speed-based movement alert — possible theft.
  bool get isMovementAlert => alertType == 'MACHINE_MOVING';

  /// Geo-fence breach alert — possible theft.
  bool get isGeofenceAlert => alertType == 'GEOFENCE_BREACH';

  /// Theft-related alert (movement or geofence breach).
  bool get isTheftAlert => isMovementAlert || isGeofenceAlert;

  /// Command completed successfully (DONE state).
  bool get isDone => incidentState == 'DONE';

  /// Command acknowledged by device (ACK state).
  bool get isAck => incidentState == 'ACK';

  /// Command failed (FAILED state).
  bool get isFailed => incidentState == 'FAILED';
}
