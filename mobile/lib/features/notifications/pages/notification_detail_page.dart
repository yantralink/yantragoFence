import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/notifications/providers/notification_provider.dart';
import 'package:yantrago/features/notifications/widgets/alert_type_labels.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/notification_inbox.dart';

/// Notification detail page — shows a single notification with mark-read
/// and machine drill-down.
///
/// Per notification plan Phase 4:
/// - Notification/alert detail routes
/// - Machine drill-down
/// - Mark-read
/// - Detail navigation has a working back path
///
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
class NotificationDetailPage extends ConsumerWidget {
  final String notificationId;

  const NotificationDetailPage({super.key, required this.notificationId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(notificationDetailProvider(notificationId));
    final markReadState = ref.watch(markReadProvider(notificationId));
    final acknowledgeState = ref.watch(acknowledgeProvider(notificationId));
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.notificationTitle)),
      body: detail.when(
        loading: () =>
            AppStatePanel.loading(message: l10n.loadingNotification),
        error: (error, _) {
          // SIG 21: When the machine is no longer assigned to the user
          // (API returns 403/404), show a friendly message instead of
          // a generic error.
          final isMachineUnavailable = _isMachineUnavailableError(error);
          if (isMachineUnavailable) {
            return AppStatePanel.empty(
              title: l10n.machineUnavailableTitle,
              message: l10n.machineUnavailableMessage,
              icon: Icons.devices_other,
              onRetry: () => context.pop(),
            );
          }
          return AppStatePanel.error(
            message: l10n.notificationLoadFailed,
            onRetry: () => ref.refresh(
              notificationDetailProvider(notificationId).future,
            ),
          );
        },
        data: (notification) {
          if (notification == null) {
            return AppStatePanel.empty(
              title: l10n.notificationNotFound,
              icon: Icons.notifications_off_outlined,
              onRetry: () => context.pop(),
            );
          }
          return _DetailContent(
            notification: notification,
            markReadLoading: markReadState is AsyncLoading,
            acknowledgeLoading: acknowledgeState is AsyncLoading,
            onMarkRead: () => ref
                .read(markReadProvider(notificationId).notifier)
                .markRead(),
            onAcknowledge: () => ref
                .read(acknowledgeProvider(notificationId).notifier)
                .acknowledge(),
            onMachineTap: notification.machineId != null
                ? () => context.push('/app/machines/${notification.machineId}')
                : null,
          );
        },
      ),
    );
  }

  /// SIG 21: Detects if the error is a 403/404 indicating the machine is
  /// no longer assigned to the user.
  bool _isMachineUnavailableError(Object error) {
    if (error is DioException) {
      final statusCode = error.response?.statusCode;
      return statusCode == 403 || statusCode == 404;
    }
    return false;
  }
}

class _DetailContent extends StatelessWidget {
  final NotificationInbox notification;
  final bool markReadLoading;
  final bool acknowledgeLoading;
  final VoidCallback onMarkRead;
  final VoidCallback onAcknowledge;
  final VoidCallback? onMachineTap;

  const _DetailContent({
    required this.notification,
    required this.markReadLoading,
    required this.acknowledgeLoading,
    required this.onMarkRead,
    required this.onAcknowledge,
    this.onMachineTap,
  });

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final l10n = context.l10n;
    final StatusTone tone = _tone(notification);

    return AppPageBody(
      scrollable: true,
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          // Severity badge and alert type
          AppSurfaceCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: (Theme.of(context)
                                .extension<AppSemanticColors>()
                                ?.toneSoft(tone) ??
                            colors.primary.withValues(alpha: 0.12)),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        _icon(notification),
                        size: 22,
                        color: Theme.of(context)
                                .extension<AppSemanticColors>()
                                ?.tone(tone) ??
                            colors.primary,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        alertTypeLabel(l10n, notification.alertType),
                        style: text.titleLarge,
                      ),
                    ),
                    AppStatusBadge(
                      label: notification
                          .severity, // always-en: raw severity code
                      tone: tone,
                      dot: true,
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  notification.title,
                  style: text.titleMedium,
                ),
                const SizedBox(height: 4),
                Text(
                  notification.body,
                  style: text.bodyLarge,
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          // Details card
          AppSurfaceCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                _DetailRow(
                  label: l10n.detailStatus,
                  value: _statusLabel(l10n, notification),
                ),
                if (notification.observedValue != null)
                  _DetailRow(
                    label: l10n.detailObservedValue,
                    value:
                        '${notification.observedValue}${notification.observedUnit != null ? ' ${notification.observedUnit}' : ''}',
                  ),
                _DetailRow(
                  label: l10n.detailReceived,
                  value: _formatDateTime(l10n, notification.createdAt),
                ),
                if (notification.readAt != null)
                  _DetailRow(
                    label: l10n.detailRead,
                    value: _formatDateTime(l10n, notification.readAt!),
                  ),
              ],
            ),
          ),
          // Machine drill-down
          if (onMachineTap != null) ...<Widget>[
            const SizedBox(height: 12),
            AppSurfaceCard(
              onTap: onMachineTap,
              child: Row(
                children: <Widget>[
                  Icon(Icons.devices_outlined, color: colors.primary),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      l10n.viewMachineDetails,
                      style: text.bodyLarge?.copyWith(
                        color: colors.primary,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                  Icon(Icons.chevron_right, color: colors.onSurfaceVariant),
                ],
              ),
            ),
          ],
          // Acknowledge button (if not yet acknowledged)
          if (!notification.isAcknowledged) ...<Widget>[
            const SizedBox(height: 12),
            OutlinedButton.icon(
              icon: acknowledgeLoading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.check_circle_outline),
              label: Text(l10n.acknowledge),
              onPressed: acknowledgeLoading ? null : onAcknowledge,
            ),
          ],
          // Mark as read button
          if (!notification.isRead) ...<Widget>[
            const SizedBox(height: 12),
            FilledButton.icon(
              icon: markReadLoading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.check_circle_outline),
              label: Text(l10n.markAsRead),
              onPressed: markReadLoading ? null : onMarkRead,
            ),
          ],
        ],
      ),
    );
  }

  StatusTone _tone(NotificationInbox n) {
    // Command notifications use tone based on outcome, not just severity
    if (n.isCommand) {
      if (n.alertType == 'COMMAND_FAILED') return StatusTone.danger;
      if (n.alertType == 'MACHINE_ON' || n.alertType == 'MACHINE_OFF') {
        return StatusTone.success;
      }
      if (n.alertType == 'COMMAND_ACK') return StatusTone.info;
    }
    // Movement or geofence alert is always danger — possible theft
    if (n.isTheftAlert) return StatusTone.danger;
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
      case 'EXTERNAL_POWER_LOW':
        return Icons.power_outlined;
      case 'EXTERNAL_POWER_CUT':
        return Icons.power_off_outlined;
      case 'LOW_POWER_SHUTDOWN':
        return Icons.bedtime_outlined;
      case 'INTERNAL_BATTERY_LOW':
        return Icons.battery_2_bar_outlined;
      case 'COMMAND_ACK':
        return Icons.check_circle_outline;
      case 'MACHINE_ON':
        return Icons.power_settings_new;
      case 'MACHINE_OFF':
        return Icons.power_off;
      case 'COMMAND_FAILED':
        return Icons.error_outline;
      case 'MACHINE_MOVING':
        return Icons.directions_run;
      case 'GEOFENCE_BREACH':
        return Icons.location_off_outlined;
      default:
        return Icons.notifications_outlined;
    }
  }

  String _formatDateTime(AppLocalizations l10n, DateTime dt) {
    final d = DateTime.now().difference(dt);
    if (d.inMinutes < 1) return l10n.justNow;
    if (d.inHours < 1) return l10n.minutesAgo(d.inMinutes);
    if (d.inDays < 1) return l10n.hoursAgo(d.inHours);
    // always-en: numeric date, Latin digits
    return '${dt.day}/${dt.month}/${dt.year} ${dt.hour}:${dt.minute.toString().padLeft(2, '0')}';
  }

  /// Friendlier status label for command and alert states.
  String _statusLabel(AppLocalizations l10n, NotificationInbox n) {
    switch (n.incidentState) {
      case 'OPEN':
        return l10n.statusOpen;
      case 'RESOLVED':
        return l10n.statusResolved;
      case 'ESCALATED':
        return l10n.statusEscalated;
      case 'ACK':
        return l10n.statusAcknowledgedByDevice;
      case 'DONE':
        return l10n.statusCompleted;
      case 'FAILED':
        return l10n.statusFailed;
      default:
        return n.incidentState; // always-en: raw incident state code
    }
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;

  const _DetailRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: text.bodyMedium?.copyWith(
                color: colors.onSurfaceVariant,
              ),
            ),
          ),
          Expanded(
            child: Text(value, style: text.bodyMedium),
          ),
        ],
      ),
    );
  }
}
