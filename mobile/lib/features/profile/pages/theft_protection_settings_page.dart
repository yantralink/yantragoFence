import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/profile/providers/customer_settings_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Theft Protection Settings page — customer-level defaults for new
/// machines (geofence radius, speed threshold). Values apply only after
/// Save; Cancel/Back discards changes.
class TheftProtectionSettingsPage extends ConsumerStatefulWidget {
  const TheftProtectionSettingsPage({super.key});

  @override
  ConsumerState<TheftProtectionSettingsPage> createState() =>
      _TheftProtectionSettingsPageState();
}

class _TheftProtectionSettingsPageState
    extends ConsumerState<TheftProtectionSettingsPage> {
  late double _radiusSlider;
  late double _speedSlider;
  bool _initialized = false;
  bool _saving = false;

  @override
  Widget build(BuildContext context) {
    final settingsAsync = ref.watch(customerSettingsNotifierProvider);

    return Scaffold(
      appBar: AppBar(title: Text(context.l10n.theftProtectionSettings)),
      body: settingsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Icon(Icons.error_outline,
                  color: Theme.of(context).colorScheme.error, size: 32),
              const SizedBox(height: 8),
              Text(context.l10n.settingsLoadFailed),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref
                    .read(customerSettingsNotifierProvider.notifier)
                    .load(),
                child: Text(context.l10n.retry),
              ),
            ],
          ),
        ),
        data: (settings) {
          if (!_initialized) {
            _radiusSlider = settings.defaultGeofenceRadiusMeters.toDouble();
            _speedSlider = settings.defaultSpeedThresholdKmh.toDouble();
            _initialized = true;
          }

          final ColorScheme colors = Theme.of(context).colorScheme;
          final TextTheme text = Theme.of(context).textTheme;
          final l10n = context.l10n;
          final bool changed = _radiusSlider.round() !=
                  settings.defaultGeofenceRadiusMeters ||
              _speedSlider.round() != settings.defaultSpeedThresholdKmh;

          return AppPageBody(
            scrollable: true,
            safeArea: false,
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                AppSurfaceCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      // Geofence radius row
                      Row(
                        children: <Widget>[
                          Icon(Icons.location_searching,
                              color: colors.primary, size: 22),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(l10n.geofenceRadius,
                                style: text.bodyLarge),
                          ),
                          Text(
                            // always-en: radius digits + unit
                            '${_radiusSlider.round()} m',
                            style: text.bodyLarge?.copyWith(
                              fontWeight: FontWeight.w600,
                              color: colors.primary,
                            ),
                          ),
                        ],
                      ),
                      Slider(
                        value: _radiusSlider,
                        min: 50,
                        max: 1000,
                        divisions: 19, // 50m steps
                        label: '${_radiusSlider.round()} m', // always-en: digits + unit
                        onChanged: (v) => setState(() => _radiusSlider = v),
                      ),
                      const SizedBox(height: 8),

                      // Speed threshold row
                      Row(
                        children: <Widget>[
                          Icon(Icons.speed, color: colors.primary, size: 22),
                          const SizedBox(width: 8),
                          Expanded(
                            child:
                                Text(l10n.speedAlert, style: text.bodyLarge),
                          ),
                          Text(
                            // always-en: digits + unit
                            '${_speedSlider.round()} km/h',
                            style: text.bodyLarge?.copyWith(
                              fontWeight: FontWeight.w600,
                              color: colors.primary,
                            ),
                          ),
                        ],
                      ),
                      Slider(
                        value: _speedSlider,
                        min: 1,
                        max: 30,
                        divisions: 29, // 1 km/h steps
                        label: '${_speedSlider.round()} km/h', // always-en: digits + unit
                        onChanged: (v) => setState(() => _speedSlider = v),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        l10n.speedAlertThreshold(_speedSlider.round()),
                        style: text.bodySmall
                            ?.copyWith(color: colors.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Text(
                    l10n.theftProtectionDefaultsNote,
                    style: text.bodySmall
                        ?.copyWith(color: colors.onSurfaceVariant),
                  ),
                ),
                const SizedBox(height: 24),
                Row(
                  children: <Widget>[
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _saving
                            ? null
                            : () => Navigator.of(context).pop(),
                        child: Text(context.l10n.cancel),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton(
                        onPressed: _saving || !changed ? null : _save,
                        child: _saving
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2),
                              )
                            : Text(context.l10n.save),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    await ref
        .read(customerSettingsNotifierProvider.notifier)
        .update(
          _radiusSlider.round(),
          _speedSlider.round(),
        );
    if (mounted) {
      setState(() => _saving = false);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.settingsSaved),
            duration: const Duration(seconds: 2),
          ),
        );
      }
      Navigator.of(context).pop();
    }
  }
}
