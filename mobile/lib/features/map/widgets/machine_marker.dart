import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/models/location.dart';

/// Machine marker — custom Google Maps marker widget.
///
/// Placeholder — full implementation will use google_maps_flutter Marker API.
/// Uses semantic theme tones instead of raw colors.
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
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    final StatusTone tone = _statusTone(status);
    final Color foreground = semantic?.tone(tone) ?? Theme.of(context).colorScheme.primary;
    final Color background = semantic?.toneSoft(tone) ?? foreground.withValues(alpha: 0.12);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: background,
          shape: BoxShape.circle,
          border: Border.all(color: foreground, width: 2),
        ),
        child: Icon(Icons.electrical_services, color: foreground, size: 20),
      ),
    );
  }

  StatusTone _statusTone(String status) {
    switch (status) {
      case 'ONLINE':
      case 'FENCING_ON':
        return StatusTone.success;
      case 'FAULT':
        return StatusTone.danger;
      default:
        return StatusTone.neutral;
    }
  }
}
