import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:yantrago/core/theme/app_radii.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Location card below the map — pin icon, "LOCATION" label, resolved
/// address (or coordinates fallback), last-updated time, and a share
/// action that opens the coordinates in Google Maps.
class MapLocationCard extends StatelessWidget {
  final String locationLabel;
  final String address;
  final String updatedLabel;
  final double latitude;
  final double longitude;

  const MapLocationCard({
    super.key,
    required this.locationLabel,
    required this.address,
    required this.updatedLabel,
    required this.latitude,
    required this.longitude,
  });

  Future<void> _openInGoogleMaps() async {
    final uri = Uri.parse(
      'https://www.google.com/maps?q=$latitude,$longitude',
    );
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: AppRadii.cardRadius,
        border: Border.all(color: colors.outline),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          Icon(Icons.location_on_outlined,
              color: colors.onSurface, size: 24),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(locationLabel, style: text.labelSmall),
                const SizedBox(height: 2),
                Text(
                  address,
                  style: text.bodyMedium
                      ?.copyWith(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 2),
                Text(updatedLabel, style: text.bodySmall),
              ],
            ),
          ),
          IconButton(
            onPressed: _openInGoogleMaps,
            icon: Icon(Icons.share_outlined,
                color: colors.onSurface, size: 20),
            tooltip: context.l10n.openInGoogleMaps,
            visualDensity: VisualDensity.compact,
          ),
        ],
      ),
    );
  }
}
