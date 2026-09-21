import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/alerts/providers/alerts_provider.dart';
import 'package:yantrago/features/alerts/widgets/alert_card.dart';
import 'package:yantrago/features/notifications/providers/notification_provider.dart';
import 'package:yantrago/features/notifications/widgets/notification_inbox_view.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Alerts page — unified view with Active Alerts and Inbox tabs.
///
/// Per notification plan Phase 4 (SIG 17): the separate Notifications
/// bottom-navigation tab has been merged into this Alerts page. The page
/// has two tabs:
/// - "Active Alerts" — existing alerts list
/// - "Inbox" — notification inbox with filters, pagination, mark-read
///
/// The notification badge appears on the Alerts tab icon in the bottom
/// navigation bar.
///
/// Per AGENTS.md: organization_id comes from JWT, never from request body.
/// The providers handle tenant isolation; this page only renders results.
class AlertsPage extends ConsumerStatefulWidget {
  /// Initial tab index: 0 = Active Alerts, 1 = Inbox.
  final int initialTab;

  const AlertsPage({super.key, this.initialTab = 0});

  @override
  ConsumerState<AlertsPage> createState() => _AlertsPageState();
}

class _AlertsPageState extends ConsumerState<AlertsPage>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(
      length: 2,
      vsync: this,
      initialIndex: widget.initialTab.clamp(0, 1),
    );
    _tabController.addListener(() {
      if (!_tabController.indexIsChanging) {
        setState(() {});
      }
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  bool get _isInboxTab => _tabController.index == 1;

  @override
  Widget build(BuildContext context) {
    final markAllRead = ref.watch(markAllReadProvider);
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.alertsTitle),
        actions: <Widget>[
          if (_isInboxTab) ...<Widget>[
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
              onPressed: () => _showFilterSheet(context),
            ),
          ],
        ],
        bottom: TabBar(
          controller: _tabController,
          tabs: <Widget>[
            Tab(text: l10n.activeAlertsTab),
            Tab(text: l10n.inboxTab),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: const <Widget>[
          _ActiveAlertsTab(),
          NotificationInboxView(),
        ],
      ),
    );
  }

  void _showFilterSheet(BuildContext context) {
    final filter = ref.read(notificationFilterProvider);
    showModalBottomSheet<void>(
      context: context,
      builder: (context) => NotificationFilterSheet(currentFilter: filter),
    );
  }
}

/// Active Alerts tab — shows all active alerts for the organization.
class _ActiveAlertsTab extends ConsumerWidget {
  const _ActiveAlertsTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final alerts = ref.watch(alertsProvider);
    final l10n = context.l10n;

    return alerts.when(
      loading: () => AppStatePanel.loading(message: l10n.loadingAlerts),
      error: (_, __) => AppStatePanel.error(
        message: l10n.alertsLoadFailed,
        onRetry: () => ref.refresh(alertsProvider.future),
      ),
      data: (list) {
        if (list.isEmpty) {
          return AppStatePanel.empty(
            title: l10n.noActiveAlerts,
            message: l10n.activeAlertsEmptyMessage,
            icon: Icons.warning_amber_outlined,
            onRetry: () => ref.refresh(alertsProvider.future),
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
                title: l10n.activeAlertsTab,
                aside: l10n.totalCount(list.length),
              ),
              ...list.map((a) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: AlertCard(
                      alert: a,
                      onTap: () => context.push('/app/alerts/${a.id}'),
                    ),
                  )),
            ],
          ),
        );
      },
    );
  }
}
