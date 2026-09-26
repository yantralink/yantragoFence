import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/features/notifications/providers/notification_provider.dart';
import 'package:yantrago/features/notifications/widgets/notification_inbox_view.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Notifications page — the notification inbox with filters,
/// pagination, and mark-read. (Previously this page also had an
/// "Active Alerts" tab; it was removed — only the inbox remains.)
///
/// The notification badge appears on this tab's icon in the bottom
/// navigation bar.
///
/// Per AGENTS.md: organization_id comes from JWT, never from request body.
/// The providers handle tenant isolation; this page only renders results.
class AlertsPage extends ConsumerWidget {
  const AlertsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final markAllRead = ref.watch(markAllReadProvider);
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.alertsTitle),
        actions: <Widget>[
          if (markAllRead is AsyncLoading)
            const Padding(
              padding: EdgeInsets.all(12),
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            )
          else
            IconButton(
              icon: const Icon(Icons.done_all),
              tooltip: l10n.markAllRead,
              onPressed: () =>
                  ref.read(markAllReadProvider.notifier).markAllRead(),
            ),
          IconButton(
            icon: const Icon(Icons.tune),
            tooltip: l10n.filter,
            onPressed: () => _showFilterSheet(context, ref),
          ),
        ],
      ),
      body: const NotificationInboxView(),
    );
  }

  void _showFilterSheet(BuildContext context, WidgetRef ref) {
    final filter = ref.read(notificationFilterProvider);
    showModalBottomSheet<void>(
      context: context,
      builder: (context) => NotificationFilterSheet(currentFilter: filter),
    );
  }
}
