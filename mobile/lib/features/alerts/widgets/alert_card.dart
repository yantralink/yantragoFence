import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/models/alert.dart';

/// Alert card — list item for a device alert.
///
/// Composes shared presentation components. Maps the [Alert] severity to a
/// [StatusTone] and renders the alert message, type, and timestamp.
/// Color is never the sole indicator — the severity label always
/// accompanies it.
class AlertCard extends StatelessWidget {
  final Alert alert;
  final VoidCallback? onTap;

  const AlertCard({super.key, required this.alert, this.onTap});

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final StatusTone tone = _tone(alert);
    final IconData icon = _icon(alert);

    return AppSurfaceCard(
      onTap: onTap,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          _IconBox(tone: tone, icon: icon),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        alert.alertType,
                        style: text.titleSmall,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 8),
                    AppStatusBadge(
                      label: alert.severity,
                      tone: tone,
                      dot: true,
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  alert.message,
                  style: text.bodyMedium,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Row(
                  children: <Widget>[
                    Text(
                      _relative(alert.createdAt),
                      style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
                    ),
                    if (alert.acknowledged) ...<Widget>[
                      const SizedBox(width: 8),
                      Text(
                        'Acknowledged',
                        style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  StatusTone _tone(Alert a) {
    if (a.isCritical) return StatusTone.danger;
    if (a.isWarning) return StatusTone.warning;
    return StatusTone.info;
  }

  IconData _icon(Alert a) {
    if (a.isCritical) return Icons.error_outline;
    if (a.isWarning) return Icons.warning_amber_outlined;
    return Icons.info_outline;
  }

  String _relative(DateTime t) {
    final Duration d = DateTime.now().difference(t);
    if (d.inMinutes < 1) return 'just now';
    if (d.inHours < 1) return '${d.inMinutes} min ago';
    if (d.inDays < 1) return '${d.inHours} h ago';
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
