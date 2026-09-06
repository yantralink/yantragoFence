/// Device state model — current state of a device (cached in Redis).
class DeviceState {
  final String deviceId;
  final String imei;
  final String status;
  final double? voltage;
  final int? battery;
  final int? gsmSignal;
  final bool? charging;
  final bool? fencingOn;
  final double? outputVoltage;
  final double? current;
  final double? latitude;
  final double? longitude;
  final double? speed;
  final int? satellites;
  final DateTime? lastUpdateAt;

  const DeviceState({
    required this.deviceId,
    required this.imei,
    required this.status,
    this.voltage,
    this.battery,
    this.gsmSignal,
    this.charging,
    this.fencingOn,
    this.outputVoltage,
    this.current,
    this.latitude,
    this.longitude,
    this.speed,
    this.satellites,
    this.lastUpdateAt,
  });

  factory DeviceState.fromJson(Map<String, dynamic> json) {
    return DeviceState(
      deviceId: json['deviceId'] as String,
      imei: json['imei'] as String,
      status: json['status'] as String? ?? 'OFFLINE',
      voltage: (json['voltage'] as num?)?.toDouble(),
      battery: json['battery'] as int?,
      gsmSignal: json['gsmSignal'] as int?,
      charging: json['charging'] as bool?,
      fencingOn: json['fencingOn'] as bool?,
      outputVoltage: (json['outputVoltage'] as num?)?.toDouble(),
      current: (json['current'] as num?)?.toDouble(),
      latitude: (json['latitude'] as num?)?.toDouble(),
      longitude: (json['longitude'] as num?)?.toDouble(),
      speed: (json['speed'] as num?)?.toDouble(),
      satellites: json['satellites'] as int?,
      lastUpdateAt: json['lastUpdateAt'] != null
          ? DateTime.tryParse(json['lastUpdateAt'] as String)
          : null,
    );
  }
}
