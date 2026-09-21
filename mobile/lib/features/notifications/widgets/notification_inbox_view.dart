import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/notifications/providers/notification_provider.dart';
import 'package:yantrago/features/notifications/widgets/notification_card.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/notification_inbox.dart';

/// Reusable notification inbox list view — embedded inside the Alerts page
/// Inbox tab.
///
/// Renders the paginated inbox list with loading, error, empty, and data
/// states. Handles notification taps (navigate to detail + optimistic
/// mark-read). The filter sheet and mark-all-read actions are managed by
/// the parent widget (AlertsPage) via the AppBar.
///
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
class NotificationInboxView extends ConsumerWidget {
  const NotificationInboxView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inbox = ref.watch(notificationInboxProvider);
    final filter = ref.watch(notificationFilterProvider);
    final l10n = context.l10n;

    return inbox.when(
      loading: () =>
          AppStatePanel.loading(message: l10n.loadingNotifications),
      error: (error, _) {
        final isOffline = _isOfflineError(error);
        return AppStatePanel.error(
          message: isOffline
              ? l10n.errorOffline
              : l10n.notificationsLoadFailed,
          onRetry: () => ref.refresh(notificationInboxProvider.future),
        );
      },
      data: (page) {
        if (page.isEmpty) {
          return AppStatePanel.empty(
            title: l10n.noNotifications,
            message: filter.unreadOnly
                ? l10n.noUnreadNotifications
                : l10n.notificationsEmptyMessage,
            icon: Icons.notifications_none_outlined,
            onRetry: () => ref.refresh(notificationInboxProvider.future),
          );
        }
        return AppPageBody(
          scrollable: true,
          safeArea: false,
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              AppSectionHeader(
                title: filter.unreadOnly ? l10n.headerUnread : l10n.filterAll,
                aside: l10n.totalCount(page.totalElements),
              ),
              ...page.items.map((n) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: NotificationCard(
                      notification: n,
                      onTap: () => _onNotificationTap(context, ref, n),
                    ),
                  )),
              if (page.hasNext || page.hasPrevious)
                NotificationPaginationControls(
                  page: page,
                  onPrevious: () => ref
                      .read(notificationFilterProvider.notifier)
                      .previousPage(),
                  onNext: () => ref
                      .read(notificationFilterProvider.notifier)
                      .nextPage(),
                ),
            ],
          ),
        );
      },
    );
  }

  void _onNotificationTap(
      BuildContext context, WidgetRef ref, NotificationInbox n) {
    // Navigate to detail page — detail navigation has a working back path
    context.push('/app/notifications/${n.id}');

    // Optimistic mark-read on tap if unread
    if (!n.isRead) {
      ref.read(markReadProvider(n.id).notifier).markRead();
    }
  }

  /// Detects if an error is a network/offline error.
  bool _isOfflineError(Object error) {
    if (error is DioException) {
      return error.type == DioExceptionType.connectionError ||
          error.type == DioExceptionType.connectionTimeout ||
          error.type == DioExceptionType.sendTimeout ||
          error.type == DioExceptionType.receiveTimeout ||
          error.type == DioExceptionType.unknown;
    }
    return false;
  }
}

/// Filter bottom sheet for notification inbox filtering.
///
/// Per notification plan Phase 4: filters (unread, alert type).
class NotificationFilterSheet extends ConsumerStatefulWidget {
  final NotificationFilter currentFilter;

  const NotificationFilterSheet({super.key, required this.currentFilter});

  @override
  ConsumerState<NotificationFilterSheet> createState() =>
      _NotificationFilterSheetState();
}

class _NotificationFilterSheetState
    extends ConsumerState<NotificationFilterSheet> {
  late bool _unreadOnly;
  late String? _alertType;

  @override
  void initState() {
    super.initState();
    _unreadOnly = widget.currentFilter.unreadOnly;
    _alertType = widget.currentFilter.alertType;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Text(l10n.filterNotifications,
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 16),
          SwitchListTile(
            title: Text(l10n.unreadOnly),
            value: _unreadOnly,
            onChanged: (v) => setState(() => _unreadOnly = v),
          ),
          const Divider(),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Text(l10n.alertTypeFilterLabel),
          ),
          Wrap(
            spacing: 8,
            children: <Widget>[
              _FilterChip(
                label: l10n.filterAll,
                selected: _alertType == null,
                onSelected: (_) => setState(() => _alertType = null),
              ),
              _FilterChip(
                label: l10n.alertLowBattery,
                selected: _alertType == 'LOW_BATTERY',
                onSelected: (_) => setState(() => _alertType = 'LOW_BATTERY'),
              ),
              _FilterChip(
                label: l10n.alertVoltageDrop,
                selected: _alertType == 'VOLTAGE_DROP',
                onSelected: (_) =>
                    setState(() => _alertType = 'VOLTAGE_DROP'),
              ),
              _FilterChip(
                label: l10n.chipGsmSignal,
                selected: _alertType == 'GSM_SIGNAL_LOW',
                onSelected: (_) =>
                    setState(() => _alertType = 'GSM_SIGNAL_LOW'),
              ),
              _FilterChip(
                label: l10n.alertDeviceOffline,
                selected: _alertType == 'DEVICE_OFFLINE',
                onSelected: (_) =>
                    setState(() => _alertType = 'DEVICE_OFFLINE'),
              ),
              _FilterChip(
                label: l10n.alertSimExpiry,
                selected: _alertType == 'SIM_EXPIRY',
                onSelected: (_) => setState(() => _alertType = 'SIM_EXPIRY'),
              ),
              _FilterChip(
                label: l10n.chipMachineOn,
                selected: _alertType == 'MACHINE_ON',
                onSelected: (_) => setState(() => _alertType = 'MACHINE_ON'),
              ),
              _FilterChip(
                label: l10n.chipMachineOff,
                selected: _alertType == 'MACHINE_OFF',
                onSelected: (_) => setState(() => _alertType = 'MACHINE_OFF'),
              ),
              _FilterChip(
                label: l10n.chipCommandAck,
                selected: _alertType == 'COMMAND_ACK',
                onSelected: (_) =>
                    setState(() => _alertType = 'COMMAND_ACK'),
              ),
              _FilterChip(
                label: l10n.alertCommandFailed,
                selected: _alertType == 'COMMAND_FAILED',
                onSelected: (_) =>
                    setState(() => _alertType = 'COMMAND_FAILED'),
              ),
              _FilterChip(
                label: l10n.chipMovement,
                selected: _alertType == 'MACHINE_MOVING',
                onSelected: (_) =>
                    setState(() => _alertType = 'MACHINE_MOVING'),
              ),
              _FilterChip(
                label: l10n.chipGeofence,
                selected: _alertType == 'GEOFENCE_BREACH',
                onSelected: (_) =>
                    setState(() => _alertType = 'GEOFENCE_BREACH'),
              ),
            ],
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () {
              ref
                  .read(notificationFilterProvider.notifier)
                  .setUnreadOnly(_unreadOnly);
              ref
                  .read(notificationFilterProvider.notifier)
                  .setAlertType(_alertType);
              if (context.mounted) Navigator.pop(context);
            },
            child: Text(l10n.apply),
          ),
        ],
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  final String label;
  final bool selected;
  final ValueChanged<bool> onSelected;

  const _FilterChip({
    required this.label,
    required this.selected,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return FilterChip(
      label: Text(label),
      selected: selected,
      onSelected: onSelected,
    );
  }
}

/// Pagination controls for the notification inbox list.
class NotificationPaginationControls extends StatelessWidget {
  final NotificationInboxPage page;
  final VoidCallback onPrevious;
  final VoidCallback onNext;

  const NotificationPaginationControls({
    super.key,
    required this.page,
    required this.onPrevious,
    required this.onNext,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          if (page.hasPrevious)
            TextButton.icon(
              icon: const Icon(Icons.chevron_left),
              label: Text(l10n.previous),
              onPressed: onPrevious,
            ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(l10n.pageOf(page.page + 1, page.totalPages)),
          ),
          if (page.hasNext)
            TextButton.icon(
              icon: const Icon(Icons.chevron_right),
              label: Text(l10n.next),
              onPressed: onNext,
            ),
        ],
      ),
    );
  }
}
