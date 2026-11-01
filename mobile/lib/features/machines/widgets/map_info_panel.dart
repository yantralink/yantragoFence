import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/features/machines/widgets/environmental_strip.dart';
import 'package:yantrago/features/machines/widgets/machine_action_pills.dart';
import 'package:yantrago/features/machines/widgets/map_location_card.dart';
import 'package:yantrago/features/machines/widgets/theft_protection_card.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/machine.dart';
import 'package:yantrago/models/weather.dart';

/// Below-map info panel for the machine location map screen —
/// environmental strip, machine/fence status pills + Set Schedule,
/// location card, and the Theft Protection card.
class MapInfoPanel extends StatelessWidget {
  final String machineId;

  /// Machine whose fencing state drives the Fence pill — null hides it.
  final Machine? machine;

  /// Ignition/power state — null hides the Machine pill.
  final bool? machineOn;
  final String address;
  final String updatedLabel;
  final double latitude;
  final double longitude;
  /// Real weather for the strip — null renders the unavailable state.
  final Weather? weather;
  final bool weatherLoading;

  /// Strip tap handler (opens the weather details sheet). Null =
  /// non-interactive strip.
  final VoidCallback? onWeatherTap;

  const MapInfoPanel({
    super.key,
    required this.machineId,
    required this.machine,
    required this.machineOn,
    required this.address,
    required this.updatedLabel,
    required this.latitude,
    required this.longitude,
    required this.weather,
    required this.weatherLoading,
    this.onWeatherTap,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final l10n = context.l10n;

    return Container(
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border(
          top: BorderSide(color: colors.outlineVariant),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          EnvironmentalStrip(
            weather: weather,
            isLoading: weatherLoading,
            onTap: onWeatherTap,
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg,
                AppSpacing.sm, AppSpacing.lg, AppSpacing.sm),
            child: MachineActionPills(
              machine: machine,
              machineOn: machineOn,
              machineOnLabel: l10n.mapMachineOn,
              machineOffLabel: l10n.mapMachineOff,
              fenceOnLabel: l10n.mapFenceOn,
              fenceOffLabel: l10n.mapFenceOff,
              scheduleLabel: l10n.setSchedule,
              onScheduleTap: () => context
                  .push('/app/machines/$machineId/schedule'),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.lg),
            child: MapLocationCard(
              locationLabel: l10n.locationLabel,
              address: address,
              updatedLabel: updatedLabel,
              latitude: latitude,
              longitude: longitude,
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          Padding(
            padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.lg),
            child: TheftProtectionCard(machineId: machineId),
          ),
          const SizedBox(height: AppSpacing.sm),
        ],
      ),
    );
  }
}
