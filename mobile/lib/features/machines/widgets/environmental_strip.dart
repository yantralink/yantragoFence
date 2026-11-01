import 'package:flutter/material.dart';
import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/weather.dart';

/// Environmental strip below the map — shows real weather for the
/// machine's location: condition icon + localized label, temperature,
/// and wind. Tap opens the weather details sheet.
///
/// States: loading (subtle dots — never blank), unavailable (weather
/// null — honest label, never fabricated), data (real WeatherDto
/// fields only).
class EnvironmentalStrip extends StatelessWidget {
  /// Real weather from machineWeatherProvider — null renders the
  /// unavailable state.
  final Weather? weather;

  /// Whether the weather request is in flight.
  final bool isLoading;

  /// Tap handler (opens the weather details sheet upstream). Null
  /// renders the strip non-interactive — e.g. no data to expand.
  final VoidCallback? onTap;

  const EnvironmentalStrip({
    super.key,
    required this.weather,
    required this.isLoading,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();

    Widget content;
    if (isLoading && weather == null) {
      content = const SizedBox(
        height: 18,
        width: 18,
        child: CircularProgressIndicator(strokeWidth: 2),
      );
    } else if (weather == null) {
      content = _item(
        icon: Icons.cloud_off_outlined,
        label: l10n.weatherUnavailable,
        color: semantic?.tone(StatusTone.neutral),
        context: context,
      );
    } else {
      final w = weather!;
      final temp = w.temperatureCelsius == null
          ? null
          : '${w.temperatureCelsius!.toStringAsFixed(0)}°C';
      final windParts = <String>[
        if (w.windSpeedKph != null)
          '${w.windSpeedKph!.toStringAsFixed(1)} km/h',
        if (w.windCompass != null) w.windCompass!,
      ];
      content = Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: <Widget>[
          Flexible(
            child: _item(
              icon: weatherConditionIcon(w.conditionCode,
                  isDay: w.isDay),
              label:
                  weatherConditionLabel(l10n, w.conditionCode),
              color: semantic?.tone(StatusTone.success),
              context: context,
            ),
          ),
          if (temp != null)
            Flexible(
              child: _item(
                icon: Icons.thermostat,
                label: temp,
                color: colors.onSurface,
                context: context,
              ),
            ),
          if (windParts.isNotEmpty)
            Flexible(
              child: _item(
                icon: Icons.air,
                label: windParts.join(' '),
                color: colors.onSurface,
                context: context,
              ),
            ),
        ],
      );
    }

    return Material(
      color: colors.surface,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.lg, vertical: AppSpacing.sm + 2),
          child: Center(child: content),
        ),
      ),
    );
  }

  Widget _item({
    required IconData icon,
    required String label,
    required Color? color,
    required BuildContext context,
  }) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Icon(icon, size: 16, color: color),
        const SizedBox(width: 4),
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context)
                .textTheme
                .labelMedium
                ?.copyWith(color: color),
          ),
        ),
      ],
    );
  }
}
