/// Real-time command status update delivered over the command WebSocket.
///
/// Mirrors the backend `CommandBroadcastService` payload published to
/// `/topic/command/{machineId}`:
/// `{"commandId", "machineId", "status", "attemptCount", "error", "timestamp"}`.
class CommandStatusUpdate {
  final String commandId;
  final String machineId;
  final String status;
  final int attemptCount;
  final String? error;

  const CommandStatusUpdate({
    required this.commandId,
    required this.machineId,
    required this.status,
    this.attemptCount = 0,
    this.error,
  });

  factory CommandStatusUpdate.fromJson(Map<String, dynamic> json) {
    final rawError = json['error'] as String?;
    return CommandStatusUpdate(
      commandId: json['commandId'] as String,
      machineId: json['machineId'] as String,
      status: json['status'] as String,
      attemptCount: (json['attemptCount'] as num?)?.toInt() ?? 0,
      // Backend sends "" when there is no error — normalize to null.
      error: (rawError == null || rawError.isEmpty) ? null : rawError,
    );
  }
}
