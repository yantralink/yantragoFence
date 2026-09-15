/// Command lifecycle event carried in an FCM data payload.
///
/// Backend `NotificationEventConsumer.createInboxItem` builds the push data
/// with keys: alertType, incidentState, severity, machineId, inboxId, and
/// (for command alerts) alertId. For command alerts the alertId equals the
/// commandId (set by `CommandResultConsumer`).
///
/// Command alert types: COMMAND_ACK (incidentState ACK), MACHINE_ON /
/// MACHINE_OFF (incidentState DONE), COMMAND_FAILED (incidentState FAILED
/// or TIMEOUT).
class CommandPushEvent {
  static const Set<String> commandAlertTypes = {
    'COMMAND_ACK',
    'MACHINE_ON',
    'MACHINE_OFF',
    'COMMAND_FAILED',
  };

  final String machineId;
  final String? commandId;
  final String alertType;
  final String incidentState;

  const CommandPushEvent({
    required this.machineId,
    this.commandId,
    required this.alertType,
    required this.incidentState,
  });

  /// Returns null if the data payload is not a command alert or lacks machineId.
  static CommandPushEvent? fromData(Map<String, dynamic> data) {
    final alertType = data['alertType']?.toString();
    if (alertType == null || !commandAlertTypes.contains(alertType)) {
      return null;
    }
    final machineId = data['machineId']?.toString();
    if (machineId == null || machineId.isEmpty) {
      return null;
    }
    final alertId = data['alertId']?.toString();
    final commandId = (alertId == null || alertId.isEmpty) ? null : alertId;
    final incidentState = data['incidentState']?.toString() ?? '';
    return CommandPushEvent(
      machineId: machineId,
      commandId: commandId,
      alertType: alertType,
      incidentState: incidentState,
    );
  }

  /// Maps alert type + incident state to the Command.status vocabulary.
  String get commandStatus {
    switch (alertType) {
      case 'COMMAND_ACK':
        return 'ACK';
      case 'MACHINE_ON':
      case 'MACHINE_OFF':
        return 'DONE';
      case 'COMMAND_FAILED':
        return incidentState == 'TIMEOUT' ? 'TIMEOUT' : 'FAILED';
      default:
        return incidentState;
    }
  }
}
