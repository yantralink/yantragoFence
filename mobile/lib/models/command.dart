import '../core/utils/date_utils.dart';

/// Command model — machine ON/OFF command lifecycle.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
/// Per AGENTS.md rule 6: all commands must be auditable.
class Command {
  final String id;
  final String machineId;
  final String? deviceId;
  final String? imei;
  final String commandType;
  final String status;
  final int attemptCount;
  final int maxAttempts;
  final String? lastError;
  final DateTime? completedAt;
  final DateTime createdAt;

  const Command({
    required this.id,
    required this.machineId,
    this.deviceId,
    this.imei,
    required this.commandType,
    required this.status,
    this.attemptCount = 0,
    this.maxAttempts = 3,
    this.lastError,
    this.completedAt,
    required this.createdAt,
  });

  factory Command.fromJson(Map<String, dynamic> json) {
    return Command(
      id: json['id'] as String,
      machineId: json['machineId'] as String,
      deviceId: json['deviceId'] as String?,
      imei: json['imei'] as String?,
      commandType: json['commandType'] as String,
      status: json['status'] as String? ?? 'PENDING',
      attemptCount: (json['attemptCount'] as num?)?.toInt() ?? 0,
      maxAttempts: (json['maxAttempts'] as num?)?.toInt() ?? 3,
      lastError: json['lastError'] as String?,
      completedAt: AppDateUtils.parse(json['completedAt'] as String?),
      createdAt: AppDateUtils.parseStrict(json['createdAt'] as String),
    );
  }

  bool get isPending => status == 'PENDING' || status == 'QUEUED' || status == 'SENT';
  bool get isAcked => status == 'ACK';
  bool get isDone => status == 'DONE';
  bool get isFailed => status == 'FAILED';
  bool get isTimeout => status == 'TIMEOUT';
  bool get isTerminal => isDone || isFailed || isTimeout;

  /// Returns a copy of this command with the given fields replaced.
  /// A null argument means "keep the existing value" for that field.
  Command copyWith({
    String? status,
    int? attemptCount,
    String? lastError,
    DateTime? completedAt,
  }) {
    return Command(
      id: id,
      machineId: machineId,
      deviceId: deviceId,
      imei: imei,
      commandType: commandType,
      status: status ?? this.status,
      attemptCount: attemptCount ?? this.attemptCount,
      maxAttempts: maxAttempts,
      lastError: lastError ?? this.lastError,
      completedAt: completedAt ?? this.completedAt,
      createdAt: createdAt,
    );
  }
}
