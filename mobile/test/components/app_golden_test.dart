// Golden tests for the shared presentation components.
//
// Run `flutter test --update-goldens` once to generate the baseline
// images under test/components/goldens/. Subsequent runs compare
// against the committed baselines. Golden baselines must be visually
// reviewed before committing changes.
//
// Per docs/YANTRAGO_Mobile_UI.md section 17.2: golden tests use
// controlled fonts, viewport dimensions, and provider data. The
// gallery is a test-only widget (not routed in production).

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_action_button.dart';
import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';

void main() {
  group('Golden: AppSurfaceCard', () {
    testWidgets('light theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 200));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: Padding(
            padding: const EdgeInsets.all(16),
            child: AppSurfaceCard(
              child: const Text('Card content'),
            ),
          ),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/surface_card_light.png'),
      );
    });

    testWidgets('dark theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 200));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.darkTheme,
        home: Scaffold(
          body: Padding(
            padding: const EdgeInsets.all(16),
            child: AppSurfaceCard(
              child: const Text('Card content'),
            ),
          ),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/surface_card_dark.png'),
      );
    });
  });

  group('Golden: AppStatusBadge', () {
    testWidgets('all tones in light theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 300));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: const <Widget>[
                AppStatusBadge(label: 'Neutral', tone: StatusTone.neutral, dot: true),
                SizedBox(height: 8),
                AppStatusBadge(label: 'Info', tone: StatusTone.info, dot: true),
                SizedBox(height: 8),
                AppStatusBadge(label: 'Success', tone: StatusTone.success, dot: true),
                SizedBox(height: 8),
                AppStatusBadge(label: 'Warning', tone: StatusTone.warning, dot: true),
                SizedBox(height: 8),
                AppStatusBadge(label: 'Danger', tone: StatusTone.danger, dot: true),
              ],
            ),
          ),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/status_badges_light.png'),
      );
    });

    testWidgets('all tones in dark theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 300));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.darkTheme,
        home: Scaffold(
          body: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: const <Widget>[
                AppStatusBadge(label: 'Neutral', tone: StatusTone.neutral, dot: true),
                SizedBox(height: 8),
                AppStatusBadge(label: 'Info', tone: StatusTone.info, dot: true),
                SizedBox(height: 8),
                AppStatusBadge(label: 'Success', tone: StatusTone.success, dot: true),
                SizedBox(height: 8),
                AppStatusBadge(label: 'Warning', tone: StatusTone.warning, dot: true),
                SizedBox(height: 8),
                AppStatusBadge(label: 'Danger', tone: StatusTone.danger, dot: true),
              ],
            ),
          ),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/status_badges_dark.png'),
      );
    });
  });

  group('Golden: AppMetricCard', () {
    testWidgets('available and unavailable in light theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 300));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: <Widget>[
                AppMetricCard(
                  label: 'Battery',
                  state: AppMetricState.available,
                  value: '85',
                  unit: '%',
                  statusTone: StatusTone.success,
                ),
                const SizedBox(height: 12),
                AppMetricCard(
                  label: 'Voltage',
                  state: AppMetricState.unavailable,
                  unit: 'V',
                ),
              ],
            ),
          ),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/metric_card_light.png'),
      );
    });
  });

  group('Golden: AppActionButton', () {
    testWidgets('all variants in light theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 400));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: const <Widget>[
                AppActionButton(
                  label: 'Primary',
                  style: AppActionButtonStyle.primary,
                  icon: Icons.check,
                ),
                SizedBox(height: 12),
                AppActionButton(
                  label: 'Secondary',
                  style: AppActionButtonStyle.secondary,
                ),
                SizedBox(height: 12),
                AppActionButton(
                  label: 'Turn ON',
                  style: AppActionButtonStyle.on,
                  icon: Icons.power_settings_new,
                ),
                SizedBox(height: 12),
                AppActionButton(
                  label: 'Turn OFF',
                  style: AppActionButtonStyle.off,
                  icon: Icons.power_off,
                ),
                SizedBox(height: 12),
                AppActionButton(
                  label: 'Delete',
                  style: AppActionButtonStyle.destructive,
                  icon: Icons.delete,
                ),
              ],
            ),
          ),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/action_buttons_light.png'),
      );
    });
  });

  group('Golden: AppStatePanel', () {
    testWidgets('loading state in light theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 200));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: AppStatePanel.loading(message: 'Loading data…'),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/state_panel_loading_light.png'),
      );
    });

    testWidgets('error state in light theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 300));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: AppStatePanel.error(
            message: 'Unable to load. Please try again.',
            onRetry: () {},
          ),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/state_panel_error_light.png'),
      );
    });

    testWidgets('empty state in light theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 200));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: AppStatePanel.empty(
            title: 'No items',
            message: 'Items will appear here when available.',
            icon: Icons.inbox_outlined,
          ),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/state_panel_empty_light.png'),
      );
    });
  });

  group('Golden: AppMetadataRow and AppSectionHeader', () {
    testWidgets('light theme', (tester) async {
      await tester.binding.setSurfaceSize(const Size(400, 250));
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: const <Widget>[
                AppSectionHeader(title: 'Machine Details', aside: 'Active'),
                SizedBox(height: 12),
                AppMetadataRow(label: 'Name', value: 'North Field Fence'),
                AppMetadataRow(label: 'IMEI', value: '123456789012345'),
                AppMetadataRow(label: 'Owner', value: null),
              ],
            ),
          ),
        ),
      ));
      await expectLater(
        find.byType(Scaffold),
        matchesGoldenFile('goldens/metadata_section_light.png'),
      );
    });
  });
}
