/// Location model — GPS location reading.
class Location {
  final String id;
  final String deviceId;
  final String imei;
  final double latitude;
  final double longitude;
  final double? speed;
  final double? course;
  final int? satellites;
  final DateTime timestamp;

  const Location({
    required this.id,
    required this.deviceId,
    required this.imei,
    required this.latitude,
    required this.longitude,
    this.speed,
    this.course,
    this.satellites,
    required this.timestamp,
  });

  factory Location.fromJson(Map<String, dynamic> json) {
    return Location(
      id: json['id'] as String,
      deviceId: json['deviceId'] as String,
      imei: json['imei'] as String,
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      speed: (json['speed'] as num?)?.toDouble(),
      course: (json['course'] as num?)?.toDouble(),
      satellites: json['satellites'] as int?,
      timestamp: DateTime.parse(json['timestamp'] as String),
    );
  }
}
