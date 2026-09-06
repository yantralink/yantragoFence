/// Machine model — represents a fencing machine.
class Machine {
  final String id;
  final String machineId;
  final String? organizationId;
  final String? customerId;
  final String name;
  final String? model;
  final String? serialNumber;
  final String status;
  final bool isOnline;
  final String? imei;
  final String? simNumber;
  final String? protocolType;
  final String? firmwareVersion;
  final String? customerName;
  final DateTime? lastSeenAt;
  final DateTime createdAt;
  final DateTime? updatedAt;

  const Machine({
    required this.id,
    required this.machineId,
    this.organizationId,
    this.customerId,
    required this.name,
    this.model,
    this.serialNumber,
    required this.status,
    required this.isOnline,
    this.imei,
    this.simNumber,
    this.protocolType,
    this.firmwareVersion,
    this.customerName,
    this.lastSeenAt,
    required this.createdAt,
    this.updatedAt,
  });

  factory Machine.fromJson(Map<String, dynamic> json) {
    return Machine(
      id: json['id'] as String,
      machineId: json['machineId'] as String? ?? '',
      organizationId: json['organizationId'] as String?,
      customerId: json['customerId'] as String?,
      name: json['name'] as String? ?? '',
      model: json['model'] as String?,
      serialNumber: json['serialNumber'] as String?,
      status: json['status'] as String? ?? 'OFFLINE',
      isOnline: json['isOnline'] as bool? ?? false,
      imei: json['imei'] as String?,
      simNumber: json['simNumber'] as String?,
      protocolType: json['protocolType'] as String?,
      firmwareVersion: json['firmwareVersion'] as String?,
      customerName: json['customerName'] as String?,
      lastSeenAt: json['lastSeenAt'] != null
          ? DateTime.tryParse(json['lastSeenAt'] as String)
          : null,
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'] as String)
          : DateTime.now(),
      updatedAt: json['updatedAt'] != null
          ? DateTime.tryParse(json['updatedAt'] as String)
          : null,
    );
  }

  bool get isOnlineStatus => isOnline || status == 'ACTIVE';
  bool get isFencingOn => status == 'FENCING_ON' || status == 'ACTIVE';
  bool get isFault => status == 'FAULT';
  bool get isInStock => status == 'IN_STOCK';
}
