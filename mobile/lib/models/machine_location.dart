/// Machine location model — current GPS location for a machine.
///
/// Matches the backend LocationDto JSON response from
/// GET /api/v1/locations/{machineId}.
class MachineLocation {
  final String? deviceId;
  final String? machineId;
  final double latitude;
  final double longitude;
  final double? speed;
  final double? course;
  final DateTime? recordedAt;
  final DateTime? updatedAt;

  const MachineLocation({
    this.deviceId,
    this.machineId,
    required this.latitude,
    required this.longitude,
    this.speed,
    this.course,
    this.recordedAt,
    this.updatedAt,
  });

  factory MachineLocation.fromJson(Map<String, dynamic> json) {
    return MachineLocation(
      deviceId: json['deviceId'] as String?,
      machineId: json['machineId'] as String?,
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      speed: (json['speed'] as num?)?.toDouble(),
      course: (json['course'] as num?)?.toDouble(),
      recordedAt: json['recordedAt'] != null
          ? DateTime.tryParse(json['recordedAt'] as String)
          : null,
      updatedAt: json['updatedAt'] != null
          ? DateTime.tryParse(json['updatedAt'] as String)
          : null,
    );
  }
}
