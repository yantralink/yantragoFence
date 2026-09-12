import 'package:flutter/widgets.dart';
import 'package:yantrago/features/alerts/pages/alerts_page.dart';

/// Deprecated — use [AlertsPage] with `initialTab: 1` instead.
///
/// Per SIG 17: the Notifications tab has been merged into the Alerts page.
/// This wrapper is kept for backward compatibility and delegates to
/// [AlertsPage] with the Inbox tab selected.
@Deprecated('Use AlertsPage(initialTab: 1) instead')
class NotificationsInboxPage extends StatelessWidget {
  const NotificationsInboxPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const AlertsPage(initialTab: 1);
  }
}
