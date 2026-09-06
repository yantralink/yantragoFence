/// Machine model — represents a fencing machine.
class Machine {
  final String id;
  final String organizationId;
  final String imei;
  final String name;
  final String? model;
  final String? serialNumber;
  final String protocolType;
  final String status;
  final String? customerId;
  final String? simNumber;
  final String? firmwareVersion;
  final DateTime? lastSeenAt;
  final DateTime createdAt;
  final DateTime? updatedAt;

  const Machine({
    required this.id,
    required this.organizationId,
    required this.imei,
    required this.name,
    this.model,
    this.serialNumber,
    required this.protocolType,
    required this.status,
    this.customerId,
    this.simNumber,
    this.firmwareVersion,
    this.lastSeenAt,
    required this.createdAt,
    this.updatedAt,
  });

  factory Machine.fromJson(Map<String, dynamic> json) {
    return Machine(
      id: json['id'] as String,
      organizationId: json['organizationId'] as String,
      imei: json['imei'] as String,
      name: json['name'] as String? ?? '',
      model: json['model'] as String?,
      serialNumber: json['serialNumber'] as String?,
      protocolType: json['protocolType'] as String? ?? 'YANTRAGO_FENCING',
      status: json['status'] as String? ?? 'OFFLINE',
      customerId: json['customerId'] as String?,
      simNumber: json['simNumber'] as String?,
      firmwareVersion: json['firmwareVersion'] as String?,
      lastSeenAt: json['lastSeenAt'] != null
          ? DateTime.tryParse(json['lastSeenAt'] as String)
          : null,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: json['updatedAt'] != null
          ? DateTime.tryParse(json['updatedAt'] as String)
          : null,
    );
  }

  bool get isOnline => status == 'ONLINE';
  bool get isFencingOn => status == 'FENCING_ON';
  bool get isFault => status == 'FAULT';
}
