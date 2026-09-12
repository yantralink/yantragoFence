import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/notifications/providers/notification_provider.dart';
import 'package:yantrago/models/notification_preference.dart';

/// Notification preferences page — manage per-user channel preferences.
///
/// Per notification plan Phase 4: preferences.
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
class NotificationPreferencesPage extends ConsumerWidget {
  const NotificationPreferencesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final prefs = ref.watch(notificationPreferencesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Notification Preferences')),
      body: prefs.when(
        loading: () =>
            AppStatePanel.loading(message: 'Loading preferences…'),
        error: (_, __) => AppStatePanel.error(
          message: 'Unable to load preferences. Please try again.',
          onRetry: () =>
              ref.refresh(notificationPreferencesProvider.future),
        ),
        data: (list) {
          if (list.isEmpty) {
            return AppStatePanel.empty(
              title: 'No preferences',
              message: 'You have not configured any notification preferences.',
              icon: Icons.tune,
              onRetry: () =>
                  ref.refresh(notificationPreferencesProvider.future),
            );
          }
          return AppPageBody(
            scrollable: true,
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                const AppSectionHeader(title: 'Channels'),
                ...list.map((p) => _PreferenceTile(preference: p)),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _PreferenceTile extends ConsumerWidget {
  final NotificationPreference preference;

  const _PreferenceTile({required this.preference});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final setPref = ref.watch(setPreferenceProvider);
    final isLoading = setPref is AsyncLoading;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: AppSurfaceCard(
        child: SwitchListTile(
          title: Text(preference.channelLabel),
          subtitle: Text(
            preference.appliesToAll
                ? 'All alert types'
                : preference.alertType!,
          ),
          value: preference.isEnabled,
          onChanged: isLoading
              ? null
              : (value) {
                  ref.read(setPreferenceProvider.notifier).setPreference(
                        channel: preference.channel,
                        alertType: preference.alertType,
                        isEnabled: value,
                        // SIG 22: send pushEnabled for PUSH channel
                        pushEnabled: preference.channel == 'PUSH'
                            ? value
                            : null,
                      );
                },
        ),
      ),
    );
  }
}
