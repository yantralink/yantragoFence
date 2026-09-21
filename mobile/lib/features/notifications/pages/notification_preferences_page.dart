import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/notifications/providers/notification_provider.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/l10n/l10n.dart';
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
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.notificationTitle)),
      body: prefs.when(
        loading: () =>
            AppStatePanel.loading(message: l10n.loadingPreferences),
        error: (_, __) => AppStatePanel.error(
          message: l10n.preferencesLoadFailed,
          onRetry: () =>
              ref.refresh(notificationPreferencesProvider.future),
        ),
        data: (list) {
          if (list.isEmpty) {
            return AppStatePanel.empty(
              title: l10n.noPreferences,
              message: l10n.preferencesEmptyMessage,
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
                AppSectionHeader(title: l10n.channelsSection),
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
    final l10n = context.l10n;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: AppSurfaceCard(
        child: SwitchListTile(
          title: Text(channelLabel(l10n, preference.channel)),
          subtitle: Text(
            preference.appliesToAll
                ? l10n.allAlertTypes
                // always-en: raw alert type code
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

/// Localized channel label for a raw channel code (moved out of the model
/// so the model keeps only backend data). Unknown codes fall back to the
/// raw code (always-en carve-out).
String channelLabel(AppLocalizations l10n, String channel) {
  switch (channel) {
    case 'PUSH':
      return l10n.channelPush;
    case 'EMAIL':
      return l10n.channelEmail;
    case 'SMS':
      return l10n.channelSms;
    case 'WHATSAPP':
      return l10n.channelWhatsapp;
    default:
      return channel;
  }
}
