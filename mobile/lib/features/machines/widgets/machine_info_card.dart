import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/models/machine.dart';

/// Machine info card — identity and status summary for a machine.
///
/// Feature widget composing shared presentation components. Maps the
/// [Machine] domain status to a [StatusTone] and renders available fields
/// via [AppMetadataRow]. Does not fabricate missing fields.
///
/// Uses a compact 2-column metadata layout so the card stays short and
/// leaves room for controls in the initial viewport.
class MachineInfoCard extends StatelessWidget {
  final Machine machine;

  const MachineInfoCard({super.key, required this.machine});

  @override
  Widget build(BuildContext context) {
    return AppSurfaceCard(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: Text(
                  machine.name,
                  style: Theme.of(context).textTheme.titleLarge,
                  overflow: TextOverflow.ellipsis,
                  maxLines: 2,
                ),
              ),
              const SizedBox(width: 8),
              AppStatusBadge(
                label: _statusLabel(machine),
                tone: _statusTone(machine),
                dot: true,
              ),
            ],
          ),
          const SizedBox(height: 8),
          // 2-column metadata grid for compact layout
          _CompactMetadata(machine: machine),
        ],
      ),
    );
  }

  String _statusLabel(Machine m) {
    if (m.isOnlineStatus) return 'Online';
    if (m.isFault) return 'Fault';
    if (m.isInStock) return 'In stock';
    return 'Offline';
  }

  StatusTone _statusTone(Machine m) {
    if (m.isFault) return StatusTone.danger;
    if (m.isOnlineStatus && m.isFencingOn) return StatusTone.success;
    if (m.isOnlineStatus) return StatusTone.info;
    if (m.isInStock) return StatusTone.info;
    return StatusTone.neutral;
  }
}

/// Compact 2-column metadata layout for machine info.
/// Pairs metadata fields side-by-side to save vertical space.
/// Only fields with data are shown; missing fields are omitted entirely.
class _CompactMetadata extends StatelessWidget {
  final Machine machine;

  const _CompactMetadata({required this.machine});

  @override
  Widget build(BuildContext context) {
    // Build a list of available fields only.
    final fields = <_FieldData>[
      if (machine.machineId.isNotEmpty)
        _FieldData(label: 'Machine ID', value: machine.machineId),
      if (machine.imei != null && machine.imei!.isNotEmpty)
        _FieldData(label: 'IMEI', value: machine.imei!),
      if (machine.protocolType != null && machine.protocolType!.isNotEmpty)
        _FieldData(label: 'Protocol', value: machine.protocolType!),
      _FieldData(label: 'State', value: _stateLabel(machine)),
      if (machine.model != null && machine.model!.isNotEmpty)
        _FieldData(label: 'Model', value: machine.model!),
      if (machine.simNumber != null && machine.simNumber!.isNotEmpty)
        _FieldData(label: 'SIM', value: machine.simNumber!),
    ];

    // Pair fields side-by-side, two per row.
    final rows = <Widget>[];
    for (int i = 0; i < fields.length; i += 2) {
      final left = fields[i];
      final right = (i + 1 < fields.length) ? fields[i + 1] : null;
      rows.add(_MetadataPair(left: left, right: right));
    }

    return Column(children: rows);
  }

  String _stateLabel(Machine m) {
    if (m.isFencingOn) return 'Fence ON';
    if (m.isFault) return 'Fault';
    return 'Fence OFF';
  }
}

class _FieldData {
  final String label;
  final String value;
  const _FieldData({required this.label, required this.value});
}

/// A pair of metadata fields displayed side-by-side in a single row.
class _MetadataPair extends StatelessWidget {
  final _FieldData left;
  final _FieldData? right;

  const _MetadataPair({required this.left, this.right});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Expanded(child: _Field(label: left.label, value: left.value)),
          const SizedBox(width: 12),
          Expanded(
            child: right != null
                ? _Field(label: right!.label, value: right!.value)
                : const SizedBox(),
          ),
        ],
      ),
    );
  }
}

class _Field extends StatelessWidget {
  final String label;
  final String? value;

  const _Field({required this.label, this.value});

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final bool hasValue = value != null && value!.isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(
          label,
          style: text.labelSmall?.copyWith(color: colors.onSurfaceVariant),
        ),
        const SizedBox(height: 2),
        Text(
          hasValue ? value! : '—',
          overflow: TextOverflow.ellipsis,
          style: text.bodyMedium?.copyWith(
            color: hasValue ? colors.onSurface : colors.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}
