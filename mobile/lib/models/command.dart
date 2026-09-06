/// Command model — machine ON/OFF command lifecycle.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
/// Per AGENTS.md rule 6: all commands must be auditable.
class Command {
  final String id;
  final String machineId;
  final String imei;
  final String commandType;
  final String status;
  final String? createdBy;
  final String? resultMessage;
  final DateTime? sentAt;
  final DateTime? ackedAt;
  final DateTime? completedAt;
  final DateTime createdAt;

  const Command({
    required this.id,
    required this.machineId,
    required this.imei,
    required this.commandType,
    required this.status,
    this.createdBy,
    this.resultMessage,
    this.sentAt,
    this.ackedAt,
    this.completedAt,
    required this.createdAt,
  });

  factory Command.fromJson(Map<String, dynamic> json) {
    return Command(
      id: json['id'] as String,
      machineId: json['machineId'] as String,
      imei: json['imei'] as String,
      commandType: json['commandType'] as String,
      status: json['status'] as String? ?? 'PENDING',
      createdBy: json['createdBy'] as String?,
      resultMessage: json['resultMessage'] as String?,
      sentAt: json['sentAt'] != null ? DateTime.tryParse(json['sentAt'] as String) : null,
      ackedAt: json['ackedAt'] != null ? DateTime.tryParse(json['ackedAt'] as String) : null,
      completedAt: json['completedAt'] != null ? DateTime.tryParse(json['completedAt'] as String) : null,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }

  bool get isPending => status == 'PENDING' || status == 'QUEUED' || status == 'SENT';
  bool get isAcked => status == 'ACK';
  bool get isDone => status == 'DONE';
  bool get isFailed => status == 'FAILED';
}
