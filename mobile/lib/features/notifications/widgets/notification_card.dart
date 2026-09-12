import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/models/notification_inbox.dart';

/// Notification card — list item for a notification inbox item.
///
/// Per notification plan Phase 4: inbox/active-alert views with read/unread
/// styling. Color is never the sole indicator — the severity label always
/// accompanies it.
///
/// Composes shared presentation components (AppSurfaceCard, AppStatusBadge).
class NotificationCard extends StatelessWidget {
  final NotificationInbox notification;
  final VoidCallback? onTap;

  const NotificationCard({
    super.key,
    required this.notification,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final StatusTone tone = _tone(notification);
    final IconData icon = _icon(notification);

    return AppSurfaceCard(
      onTap: onTap,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          _IconBox(tone: tone, icon: icon, isRead: notification.isRead),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        notification.alertTypeLabel,
                        style: notification.isRead
                            ? text.titleSmall
                            : text.titleSmall?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 8),
                    AppStatusBadge(
                      label: notification.severity,
                      tone: tone,
                      dot: true,
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  notification.title,
                  style: notification.isRead
                      ? text.bodyMedium
                      : text.bodyMedium?.copyWith(
                          fontWeight: FontWeight.w500,
                        ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  notification.body,
                  style: text.bodySmall?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Row(
                  children: <Widget>[
                    if (!notification.isRead)
                      Container(
                        width: 8,
                        height: 8,
                        decoration: BoxDecoration(
                          color: tone == StatusTone.danger
                              ? colors.error
                              : colors.primary,
                          shape: BoxShape.circle,
                        ),
                      ),
                    if (!notification.isRead) const SizedBox(width: 6),
                    Text(
                      _relative(notification.createdAt),
                      style: text.bodySmall?.copyWith(
                        color: colors.onSurfaceVariant,
                      ),
                    ),
                    const Spacer(),
                    if (notification.incidentState == 'RESOLVED')
                      AppStatusBadge(
                        label: 'Resolved',
                        tone: StatusTone.success,
                        dot: true,
                      ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  StatusTone _tone(NotificationInbox n) {
    if (n.isCritical) return StatusTone.danger;
    if (n.isWarning) return StatusTone.warning;
    return StatusTone.info;
  }

  IconData _icon(NotificationInbox n) {
    switch (n.alertType) {
      case 'LOW_BATTERY':
        return Icons.battery_alert_outlined;
      case 'VOLTAGE_DROP':
        return Icons.bolt_outlined;
      case 'GSM_SIGNAL_LOW':
        return Icons.signal_cellular_connected_no_internet_0_bar;
      case 'DEVICE_OFFLINE':
        return Icons.wifi_off_outlined;
      case 'SIM_EXPIRY':
        return Icons.sim_card_alert_outlined;
      default:
        return Icons.notifications_outlined;
    }
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
  final bool isRead;

  const _IconBox({
    required this.tone,
    required this.icon,
    required this.isRead,
  });

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    final Color foreground =
        semantic?.tone(tone) ?? Theme.of(context).colorScheme.primary;
    final Color background = isRead
        ? foreground.withValues(alpha: 0.06)
        : semantic?.toneSoft(tone) ?? foreground.withValues(alpha: 0.12);
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
