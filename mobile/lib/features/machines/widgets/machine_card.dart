import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/models/machine.dart';

/// Machine card — list item for a machine.
///
/// Composes shared presentation components. Maps the [Machine] domain
/// status to a [StatusTone] and renders identity + last-known state.
/// Tapping navigates to the machine detail screen.
class MachineCard extends StatelessWidget {
  final Machine machine;
  final VoidCallback? onTap;

  const MachineCard({super.key, required this.machine, this.onTap});

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final StatusTone tone = _statusTone(machine);
    final String subtitle = machine.machineId.isNotEmpty
        ? machine.machineId
        : (machine.imei ?? '');

    return AppSurfaceCard(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              _IconBox(tone: tone, icon: Icons.electrical_services),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      machine.name,
                      style: text.titleMedium,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (subtitle.isNotEmpty) ...<Widget>[
                      const SizedBox(height: 2),
                      Text(
                        subtitle,
                        style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 8),
              AppStatusBadge(
                label: _statusLabel(machine),
                tone: tone,
                dot: true,
              ),
            ],
          ),
          const SizedBox(height: 12),
          const Divider(height: 1),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      _stateLabel(machine),
                      style: text.bodyMedium,
                    ),
                    if (machine.lastSeenAt != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          'Last seen ${_relative(machine.lastSeenAt!)}',
                          style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
                        ),
                      ),
                  ],
                ),
              ),
              Icon(Icons.chevron_right, color: colors.onSurfaceVariant),
            ],
          ),
        ],
      ),
    );
  }

  String _statusLabel(Machine m) {
    if (m.isFault) return 'Fault';
    if (m.isOnlineStatus && m.isFencingOn) return 'Online';
    if (m.isOnlineStatus) return 'Online';
    if (m.isInStock) return 'In stock';
    return 'Offline';
  }

  String _stateLabel(Machine m) {
    if (m.isFault) return 'Fault reported';
    if (m.isFencingOn) return 'Fence ON · confirmed';
    if (m.isOnlineStatus) return 'Fence OFF · confirmed';
    if (m.isInStock) return 'In stock';
    return 'State unknown';
  }

  StatusTone _statusTone(Machine m) {
    if (m.isFault) return StatusTone.danger;
    if (m.isOnlineStatus && m.isFencingOn) return StatusTone.success;
    if (m.isOnlineStatus) return StatusTone.info;
    if (m.isInStock) return StatusTone.info;
    return StatusTone.neutral;
  }

  String _relative(DateTime t) {
    final Duration d = DateTime.now().difference(t);
    if (d.inSeconds < 60) return 'just now';
    if (d.inMinutes < 60) return '${d.inMinutes} min ago';
    if (d.inHours < 24) return '${d.inHours} h ago';
    return '${d.inDays} d ago';
  }
}

class _IconBox extends StatelessWidget {
  final StatusTone tone;
  final IconData icon;
  const _IconBox({required this.tone, required this.icon});

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    final Color foreground = semantic?.tone(tone) ?? Theme.of(context).colorScheme.primary;
    final Color background = semantic?.toneSoft(tone) ?? foreground.withValues(alpha: 0.12);
    return Container(
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Icon(icon, size: 22, color: foreground),
    );
  }
}
