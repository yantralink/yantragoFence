import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/settings/providers/settings_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Settings page — app and machine settings.
class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.settingsTitle)),
      body: AppPageBody(
        scrollable: true,
        safeArea: false,
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            AppSectionHeader(title: l10n.connectionSection),
            AppSurfaceCard(
              child: Column(
                children: <Widget>[
                  AppMetadataRow(
                      label: l10n.apiUrlLabel,
                      value: settings.apiUrl), // always-en: URL
                ],
              ),
            ),
            const SizedBox(height: 16),
            AppSectionHeader(title: l10n.notificationsSection),
            AppSurfaceCard(
              child: SwitchListTile(
                secondary: const Icon(Icons.notifications_outlined),
                title: Text(l10n.pushNotifications),
                subtitle: Text(l10n.pushNotificationsSubtitle),
                value: settings.notificationsEnabled,
                onChanged: (v) =>
                    ref.read(settingsProvider.notifier).setNotificationsEnabled(v),
              ),
            ),
            const SizedBox(height: 16),
            AppSectionHeader(title: l10n.aboutSection),
            AppSurfaceCard(
              child: Column(
                children: <Widget>[
                  AppMetadataRow(
                      label: l10n.aboutAppLabel,
                      value: 'YantraGO'), // always-en: brand name
                  AppMetadataRow(
                      label: l10n.versionLabel,
                      value: '1.0.0'), // always-en: version
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
