// Test-only component gallery for the YantraGO shared presentation widgets.
//
// This file lives under test/ and is NOT routed in the production app. It
// exists for repeatable visual checks and golden tests. Do not import it
// from lib/.

import 'package:flutter/material.dart';
import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_action_button.dart';
import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';

/// A scrollable gallery showing every shared component in every relevant
/// state. Used by widget and golden tests.
class AppComponentGallery extends StatelessWidget {
  const AppComponentGallery({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Component Gallery')),
      body: AppPageBody(
        scrollable: true,
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: const <Widget>[
            _SurfaceCardSection(),
            _MetadataRowSection(),
            _SectionHeaderSection(),
            _StatusBadgeSection(),
            _MetricCardSection(),
            _ActionButtonSection(),
            _StatePanelSection(),
          ],
        ),
      ),
    );
  }
}

class _SurfaceCardSection extends StatelessWidget {
  const _SurfaceCardSection();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        const AppSectionHeader(title: 'Surface card'),
        AppSurfaceCard(
          child: Text('Static card', style: Theme.of(context).textTheme.titleMedium),
        ),
        const SizedBox(height: 8),
        AppSurfaceCard(
          onTap: () {},
          child: Text('Tappable card', style: Theme.of(context).textTheme.titleMedium),
        ),
      ],
    );
  }
}

class _MetadataRowSection extends StatelessWidget {
  const _MetadataRowSection();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        const AppSectionHeader(title: 'Metadata row'),
        AppSurfaceCard(
          child: Column(
            children: <Widget>[
              const AppMetadataRow(label: 'Machine ID', value: 'YG-001'),
              const AppMetadataRow(label: 'IMEI', value: '000000000000001'),
              const AppMetadataRow(
                label: 'Long organization name with many words',
                value: 'Demo Farm Cooperative Society Limited',
              ),
              const AppMetadataRow(label: 'Missing value', value: null),
              const AppMetadataRow(label: 'Long label only no value', value: null),
            ],
          ),
        ),
      ],
    );
  }
}

class _SectionHeaderSection extends StatelessWidget {
  const _SectionHeaderSection();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        AppSectionHeader(
          title: 'Section header',
          aside: '3 items',
          action: TextButton(onPressed: () {}, child: const Text('See all')),
        ),
      ],
    );
  }
}

class _StatusBadgeSection extends StatelessWidget {
  const _StatusBadgeSection();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        const AppSectionHeader(title: 'Status badge'),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: <Widget>[
            const AppStatusBadge(label: 'Online', tone: StatusTone.success, dot: true),
            const AppStatusBadge(label: 'Offline', tone: StatusTone.neutral, dot: true),
            const AppStatusBadge(label: 'Warning', tone: StatusTone.warning, icon: Icons.warning_amber_outlined),
            const AppStatusBadge(label: 'Critical', tone: StatusTone.danger, icon: Icons.error_outline),
            const AppStatusBadge(label: 'Info', tone: StatusTone.info, icon: Icons.info_outline),
            const AppStatusBadge(
              label: 'Very long status label that should not overflow the screen',
              tone: StatusTone.success,
              dot: true,
            ),
          ],
        ),
      ],
    );
  }
}

class _MetricCardSection extends StatelessWidget {
  const _MetricCardSection();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        const AppSectionHeader(title: 'Metric card'),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: <Widget>[
            SizedBox(
              width: 160,
              child: AppMetricCard(
                label: 'Voltage',
                icon: Icons.bolt,
                state: AppMetricState.available,
                value: '24.2',
                unit: 'V',
                statusText: 'Sample reading',
                statusTone: StatusTone.neutral,
                freshness: '9:40 AM',
              ),
            ),
            SizedBox(
              width: 160,
              child: AppMetricCard(
                label: 'Battery',
                icon: Icons.battery_std,
                state: AppMetricState.available,
                value: '88',
                unit: '%',
                statusText: 'Latest sample',
                statusTone: StatusTone.success,
              ),
            ),
            SizedBox(
              width: 160,
              child: const AppMetricCard(
                label: 'Faults',
                icon: Icons.shield,
                state: AppMetricState.unavailable,
              ),
            ),
            SizedBox(
              width: 160,
              child: const AppMetricCard(
                label: 'Heartbeat',
                icon: Icons.favorite,
                state: AppMetricState.stale,
                value: '12 min ago',
                statusText: 'Stale reading',
              ),
            ),
            SizedBox(
              width: 160,
              child: const AppMetricCard(
                label: 'Telemetry',
                icon: Icons.wifi_tethering,
                state: AppMetricState.loading,
              ),
            ),
            SizedBox(
              width: 160,
              child: const AppMetricCard(
                label: 'Sensor',
                icon: Icons.sensors,
                state: AppMetricState.error,
                statusText: 'Unable to load',
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _ActionButtonSection extends StatelessWidget {
  const _ActionButtonSection();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        const AppSectionHeader(title: 'Action button'),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: <Widget>[
            SizedBox(
              width: 180,
              child: AppActionButton(
                label: 'Sign in',
                style: AppActionButtonStyle.primary,
                icon: Icons.login,
                onPressed: () {},
              ),
            ),
            SizedBox(
              width: 180,
              child: AppActionButton(
                label: 'Cancel',
                style: AppActionButtonStyle.secondary,
                onPressed: () {},
              ),
            ),
            SizedBox(
              width: 180,
              child: AppActionButton(
                label: 'Delete',
                style: AppActionButtonStyle.destructive,
                icon: Icons.delete,
                onPressed: () {},
              ),
            ),
            SizedBox(
              width: 180,
              child: AppActionButton(
                label: 'Turn ON',
                style: AppActionButtonStyle.on,
                icon: Icons.power_settings_new,
                onPressed: () {},
              ),
            ),
            SizedBox(
              width: 180,
              child: AppActionButton(
                label: 'Turn OFF',
                style: AppActionButtonStyle.off,
                icon: Icons.power_off,
                onPressed: () {},
              ),
            ),
            SizedBox(
              width: 180,
              child: AppActionButton(
                label: 'Sending...',
                style: AppActionButtonStyle.primary,
                busy: true,
                onPressed: () {},
              ),
            ),
            SizedBox(
              width: 180,
              child: const AppActionButton(
                label: 'Disabled',
                style: AppActionButtonStyle.primary,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _StatePanelSection extends StatelessWidget {
  const _StatePanelSection();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        const AppSectionHeader(title: 'State panel'),
        SizedBox(
          height: 200,
          child: AppStatePanel.loading(message: 'Fetching machines…'),
        ),
        SizedBox(
          height: 200,
          child: AppStatePanel.error(
            message: 'Check your connection and try again.',
            onRetry: () {},
          ),
        ),
        SizedBox(
          height: 200,
          child: AppStatePanel.empty(
            title: 'No machines assigned',
            message: 'Your machines will appear here once assigned.',
          ),
        ),
      ],
    );
  }
}
