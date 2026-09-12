import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/settings/providers/settings_provider.dart';

/// Settings page — app and machine settings.
class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: AppPageBody(
        scrollable: true,
        safeArea: false,
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            const AppSectionHeader(title: 'Connection'),
            AppSurfaceCard(
              child: Column(
                children: <Widget>[
                  AppMetadataRow(label: 'API URL', value: settings.apiUrl),
                ],
              ),
            ),
            const SizedBox(height: 16),
            const AppSectionHeader(title: 'Notifications'),
            AppSurfaceCard(
              child: SwitchListTile(
                secondary: const Icon(Icons.notifications_outlined),
                title: const Text('Push notifications'),
                subtitle: const Text('Receive alerts and command updates'),
                value: settings.notificationsEnabled,
                onChanged: (v) =>
                    ref.read(settingsProvider.notifier).setNotificationsEnabled(v),
              ),
            ),
            const SizedBox(height: 16),
            const AppSectionHeader(title: 'About'),
            AppSurfaceCard(
              child: Column(
                children: <Widget>[
                  const AppMetadataRow(label: 'App', value: 'YantraGO'),
                  const AppMetadataRow(label: 'Version', value: '1.0.0'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
