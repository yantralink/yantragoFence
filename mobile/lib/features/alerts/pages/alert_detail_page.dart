import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/alerts/providers/alerts_provider.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/alert.dart';

/// Alert detail page — shows a single alert with machine drill-down.
///
/// Per SIG 18: fetched by ID from the API, shows alert type, severity,
/// message, machine, status, and timestamps. Has a "View Machine" button
/// that navigates to `/app/machines/:machineId`.
///
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
class AlertDetailPage extends ConsumerWidget {
  final String alertId;

  const AlertDetailPage({super.key, required this.alertId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(alertDetailProvider(alertId));
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.alertDetailsTitle)),
      body: detail.when(
        loading: () => AppStatePanel.loading(message: l10n.loadingAlert),
        error: (_, __) => AppStatePanel.error(
          message: l10n.alertLoadFailed,
          onRetry: () =>
              ref.refresh(alertDetailProvider(alertId).future),
        ),
        data: (alert) {
          if (alert == null) {
            return AppStatePanel.empty(
              title: l10n.alertNotFound,
              icon: Icons.warning_amber_outlined,
              onRetry: () => context.pop(),
            );
          }
          return _DetailContent(
            alert: alert,
            onMachineTap: alert.machineId != null
                ? () => context.push('/app/machines/${alert.machineId}')
                : null,
          );
        },
      ),
    );
  }
}

class _DetailContent extends StatelessWidget {
  final Alert alert;
  final VoidCallback? onMachineTap;

  const _DetailContent({required this.alert, this.onMachineTap});

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final l10n = context.l10n;
    final StatusTone tone = _tone(alert);

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
                    Expanded(
                      child: Text(
                        alert.alertType, // always-en: raw alert type code
                        style: text.titleLarge,
                      ),
                    ),
                    AppStatusBadge(
                      label: alert.severity, // always-en: raw severity code
                      tone: tone,
                      dot: true,
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  alert.message,
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
                  value: alert.acknowledged
                      ? l10n.statusAcknowledged
                      : l10n.statusUnacknowledged,
                ),
                _DetailRow(
                  label: l10n.detailAlertType,
                  value: alert.alertType, // always-en: raw alert type code
                ),
                _DetailRow(
                  label: l10n.detailSeverity,
                  value: alert.severity, // always-en: raw severity code
                ),
                if (alert.triggeredAt != null)
                  _DetailRow(
                    label: l10n.detailTriggered,
                    value: _formatDateTime(alert.triggeredAt!),
                  ),
                _DetailRow(
                  label: l10n.detailCreated,
                  value: _formatDateTime(alert.createdAt),
                ),
                if (alert.acknowledgedAt != null)
                  _DetailRow(
                    label: l10n.detailAcknowledgedAt,
                    value: _formatDateTime(alert.acknowledgedAt!),
                  ),
              ],
            ),
          ),
          // Machine drill-down
          if (onMachineTap != null) ...<Widget>[
            const SizedBox(height: 12),
            FilledButton.icon(
              icon: const Icon(Icons.devices_outlined),
              label: Text(l10n.viewMachine),
              onPressed: onMachineTap,
            ),
          ],
        ],
      ),
    );
  }

  StatusTone _tone(Alert a) {
    if (a.isCritical) return StatusTone.danger;
    if (a.isWarning) return StatusTone.warning;
    return StatusTone.info;
  }

  String _formatDateTime(DateTime dt) {
    return '${dt.day}/${dt.month}/${dt.year} '
        '${dt.hour}:${dt.minute.toString().padLeft(2, '0')}';
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
