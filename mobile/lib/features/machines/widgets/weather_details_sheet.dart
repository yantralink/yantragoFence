import 'package:flutter/material.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/core/utils/date_utils.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/weather.dart';

/// Opens the weather details bottom sheet — exposes the full
/// WeatherDto field set without a new screen (map strip tap target).
void showWeatherDetailsSheet(BuildContext context, Weather weather) {
  showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    builder: (ctx) => WeatherDetailsSheet(weather: weather),
  );
}

/// Full weather details — every real field the backend returns;
/// fields that are null are omitted rather than fabricated.
class WeatherDetailsSheet extends StatelessWidget {
  final Weather weather;

  const WeatherDetailsSheet({super.key, required this.weather});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final colors = Theme.of(context).colorScheme;
    final w = weather;

    final metrics = <Widget>[
      if (w.apparentTemperatureCelsius != null)
        _Metric(
          icon: Icons.thermostat,
          label: l10n.weatherFeelsLike,
          value:
              '${w.apparentTemperatureCelsius!.toStringAsFixed(1)}°C',
        ),
      if (w.humidityPercent != null)
        _Metric(
          icon: Icons.water_drop_outlined,
          label: l10n.weatherHumidity,
          value: '${w.humidityPercent}%',
        ),
      if (w.windSpeedKph != null)
        _Metric(
          icon: Icons.air,
          label: l10n.weatherWind,
          value:
              '${w.windSpeedKph!.toStringAsFixed(1)} km/h${w.windCompass != null ? ' ${w.windCompass}' : ''}',
        ),
      if (w.windGustsKph != null)
        _Metric(
          icon: Icons.air,
          label: l10n.weatherGusts,
          value: '${w.windGustsKph!.toStringAsFixed(1)} km/h',
        ),
      if (w.precipitationMm != null)
        _Metric(
          icon: Icons.umbrella_outlined,
          label: l10n.weatherPrecipitation,
          value:
              '${w.precipitationMm!.toStringAsFixed(1)} mm',
        ),
    ];

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
            AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Icon(
                  weatherConditionIcon(w.conditionCode,
                      isDay: w.isDay),
                  size: 40,
                  color: colors.primary,
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        w.temperatureCelsius == null
                            ? weatherConditionLabel(
                                l10n, w.conditionCode)
                            : '${w.temperatureCelsius!.toStringAsFixed(1)}°C',
                        style:
                            Theme.of(context).textTheme.headlineSmall,
                      ),
                      Text(
                        '${weatherConditionLabel(l10n, w.conditionCode)} · '
                        '${l10n.updatedAt(relativeTime(l10n, w.observedTime))}',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (metrics.isNotEmpty) ...<Widget>[
              const SizedBox(height: AppSpacing.lg),
              Wrap(
                spacing: AppSpacing.lg,
                runSpacing: AppSpacing.md,
                children: metrics,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;

  const _Metric(
      {required this.icon, required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Icon(icon,
            size: 18, color: Theme.of(context).colorScheme.primary),
        const SizedBox(width: AppSpacing.xs),
        Text('$label: ',
            style: Theme.of(context).textTheme.bodySmall),
        Text(value,
            style: Theme.of(context)
                .textTheme
                .bodyMedium
                ?.copyWith(fontWeight: FontWeight.w600)),
      ],
    );
  }
}
