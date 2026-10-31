import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/features/machines/widgets/environmental_strip.dart';
import 'package:yantrago/features/machines/widgets/machine_action_pills.dart';
import 'package:yantrago/features/machines/widgets/map_location_card.dart';
import 'package:yantrago/features/machines/widgets/theft_protection_card.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/machine.dart';

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
  final String conditionLabel;
  final String weatherLabel;
  final String windLabel;

  const MapInfoPanel({
    super.key,
    required this.machineId,
    required this.machine,
    required this.machineOn,
    required this.address,
    required this.updatedLabel,
    required this.latitude,
    required this.longitude,
    required this.conditionLabel,
    required this.weatherLabel,
    required this.windLabel,
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
            conditionLabel: conditionLabel,
            conditionIcon: Icons.check_circle,
            weatherLabel: weatherLabel,
            windLabel: windLabel,
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
