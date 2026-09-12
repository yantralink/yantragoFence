import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/features/notifications/providers/notification_provider.dart';

/// Notification badge — shows unread count on the navigation bar.
///
/// Per notification plan Phase 4: badge.
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
class NotificationBadge extends ConsumerWidget {
  final Widget child;

  const NotificationBadge({super.key, required this.child});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unreadCount = ref.watch(unreadCountProvider);

    return unreadCount.when(
      loading: () => child,
      error: (_, __) => child,
      data: (count) {
        if (count == 0) return child;
        return Badge(
          label: Text(count > 99 ? '99+' : count.toString()),
          child: child,
        );
      },
    );
  }
}
