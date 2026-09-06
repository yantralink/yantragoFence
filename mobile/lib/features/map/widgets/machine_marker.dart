import 'package:flutter/material.dart';
import 'package:yantrago/models/location.dart';

/// Machine marker — custom Google Maps marker widget.
///
/// Placeholder — full implementation will use google_maps_flutter Marker API.
class MachineMarker extends StatelessWidget {
  final Location location;
  final String status;
  final VoidCallback? onTap;

  const MachineMarker({
    super.key,
    required this.location,
    this.status = 'OFFLINE',
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: _statusColor(status),
          shape: BoxShape.circle,
        ),
        child: const Icon(Icons.electrical_services, color: Colors.white, size: 20),
      ),
    );
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'ONLINE':
      case 'FENCING_ON':
        return Colors.green;
      case 'FAULT':
        return Colors.red;
      default:
        return Colors.grey;
    }
  }
}
