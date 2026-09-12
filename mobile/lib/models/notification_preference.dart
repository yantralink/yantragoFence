/// Notification preference — per-user channel and alert type preference.
///
/// Matches the backend notification_preferences table (V10 + V24 migration).
/// Per notification plan Phase 4: preferences UI.
class NotificationPreference {
  final String id;
  final String userId;
  final String organizationId;
  final String channel;
  final String? alertType;
  final bool isEnabled;
  final DateTime createdAt;
  final DateTime updatedAt;

  const NotificationPreference({
    required this.id,
    required this.userId,
    required this.organizationId,
    required this.channel,
    this.alertType,
    required this.isEnabled,
    required this.createdAt,
    required this.updatedAt,
  });

  factory NotificationPreference.fromJson(Map<String, dynamic> json) {
    return NotificationPreference(
      id: json['id'] as String,
      userId: json['userId'] as String,
      organizationId: json['organizationId'] as String,
      channel: json['channel'] as String,
      alertType: json['alertType'] as String?,
      isEnabled: json['isEnabled'] as bool? ?? true,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }

  /// Display label for the channel.
  String get channelLabel {
    switch (channel) {
      case 'PUSH':
        return 'Push Notifications';
      case 'EMAIL':
        return 'Email';
      case 'SMS':
        return 'SMS';
      case 'WHATSAPP':
        return 'WhatsApp';
      default:
        return channel;
    }
  }

  /// Icon for the channel.
  String get channelIcon {
    switch (channel) {
      case 'PUSH':
        return 'notifications';
      case 'EMAIL':
        return 'email';
      case 'SMS':
        return 'sms';
      case 'WHATSAPP':
        return 'chat';
      default:
        return 'notifications';
    }
  }

  /// Whether this preference applies to all alert types.
  bool get appliesToAll => alertType == null || alertType!.isEmpty;
}
