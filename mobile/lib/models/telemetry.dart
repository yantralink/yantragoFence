/// Telemetry model — device telemetry reading.
///
/// Maps the backend `TelemetryLatestDto` response from
/// GET /api/v1/machines/{id}/telemetry/latest.
///
/// Per BR05 protocol:
/// - battery: approximate 0–100 from the 7-level voltage enum
/// - charging: Terminal Info Bit2 (true = external power connected)
/// - gsmSignal: 0–4 from the GSM signal level byte
/// - voltage: external power voltage in volts from 0x94 info packet (type 0x00)
class Telemetry {
  final String? id;
  final String? deviceId;
  final String? imei;
  final double? voltage;
  final int? battery;
  final int? gsmSignal;
  final bool? charging;
  final DateTime? timestamp;

  const Telemetry({
    this.id,
    this.deviceId,
    this.imei,
    this.voltage,
    this.battery,
    this.gsmSignal,
    this.charging,
    this.timestamp,
  });

  /// Creates a Telemetry from the backend TelemetryLatestDto JSON.
  /// Fields are null when no heartbeat/alarm packet has been received yet.
  factory Telemetry.fromJson(Map<String, dynamic> json) {
    return Telemetry(
      id: json['id'] as String?,
      deviceId: json['deviceId'] as String?,
      imei: json['imei'] as String?,
      voltage: (json['voltage'] as num?)?.toDouble(),
      // Backend sends batteryPct as a Double; convert to int for the UI widget
      battery: (json['batteryPct'] as num?)?.toInt() ??
          (json['battery'] as num?)?.toInt(),
      gsmSignal: (json['gsmSignal'] as num?)?.toInt(),
      charging: json['charging'] as bool?,
      timestamp: json['lastTelemetryAt'] != null
          ? DateTime.tryParse(json['lastTelemetryAt'] as String)
          : (json['timestamp'] != null
              ? DateTime.tryParse(json['timestamp'] as String)
              : null),
    );
  }

  /// Empty telemetry (all fields null) for "no data" state.
  static const Telemetry empty = Telemetry();
}
