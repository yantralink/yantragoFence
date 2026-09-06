/// Telemetry model — device telemetry reading.
class Telemetry {
  final String id;
  final String deviceId;
  final String imei;
  final double? voltage;
  final int? battery;
  final int? gsmSignal;
  final bool? charging;
  final DateTime timestamp;

  const Telemetry({
    required this.id,
    required this.deviceId,
    required this.imei,
    this.voltage,
    this.battery,
    this.gsmSignal,
    this.charging,
    required this.timestamp,
  });

  factory Telemetry.fromJson(Map<String, dynamic> json) {
    return Telemetry(
      id: json['id'] as String,
      deviceId: json['deviceId'] as String,
      imei: json['imei'] as String,
      voltage: (json['voltage'] as num?)?.toDouble(),
      battery: json['battery'] as int?,
      gsmSignal: json['gsmSignal'] as int?,
      charging: json['charging'] as bool?,
      timestamp: DateTime.parse(json['timestamp'] as String),
    );
  }
}
