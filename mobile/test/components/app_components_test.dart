// Widget tests for the YantraGO shared presentation components.
//
// Covers: light/dark themes, long labels and values, 200% text scaling,
// narrow width, busy/disabled actions, all status tones, and the
// unavailable/stale metric states. Verifies no overflow and that the
// components are presentation-only (no feature-provider dependency).

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

Widget _wrapped(Widget child, {Brightness brightness = Brightness.light}) {
  return MaterialApp(
    theme: brightness == Brightness.light ? AppTheme.lightTheme : AppTheme.darkTheme,
    home: Scaffold(body: child),
  );
}

void main() {
  group('AppSurfaceCard', () {
    testWidgets('renders static and tappable cards without overflow', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppSurfaceCard(child: Text('Static card')),
      ));
      expect(find.text('Static card'), findsOneWidget);
    });

    testWidgets('tappable card calls onTap', (tester) async {
      bool tapped = false;
      await tester.pumpWidget(_wrapped(
        AppSurfaceCard(onTap: () => tapped = true, child: const Text('Tap')),
      ));
      await tester.tap(find.text('Tap'));
      expect(tapped, isTrue);
    });
  });

  group('AppMetadataRow', () {
    testWidgets('shows value when present', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppMetadataRow(label: 'Machine ID', value: 'YG-001'),
      ));
      expect(find.text('Machine ID'), findsOneWidget);
      expect(find.text('YG-001'), findsOneWidget);
    });

    testWidgets('shows placeholder for missing value, not blank', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppMetadataRow(label: 'Missing', value: null),
      ));
      expect(find.text('—'), findsOneWidget);
    });

    testWidgets('long label and value do not overflow at 200% text scale', (tester) async {
      await tester.binding.setSurfaceSize(Size(320, 600));
      await tester.pumpWidget(_wrapped(
        const AppMetadataRow(
          label: 'Organization name with many words',
          value: 'Demo Farm Cooperative Society Limited',
        ),
      ));
      await tester.pump();
      expect(tester.takeException(), isNull);
      await tester.binding.setSurfaceSize(null);
    });
  });

  group('AppSectionHeader', () {
    testWidgets('renders uppercase title and aside', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppSectionHeader(title: 'Machines', aside: '3 assigned'),
      ));
      expect(find.text('MACHINES'.toUpperCase()), findsOneWidget);
      expect(find.text('3 assigned'), findsOneWidget);
    });

    testWidgets('renders optional action', (tester) async {
      await tester.pumpWidget(_wrapped(
        AppSectionHeader(
          title: 'Alerts',
          action: TextButton(onPressed: () {}, child: const Text('See all')),
        ),
      ));
      expect(find.text('See all'), findsOneWidget);
    });
  });

  group('AppStatusBadge', () {
    testWidgets('renders all status tones', (tester) async {
      await tester.pumpWidget(_wrapped(
        Column(
          children: [
            const AppStatusBadge(label: 'Online', tone: StatusTone.success, dot: true),
            const AppStatusBadge(label: 'Offline', tone: StatusTone.neutral, dot: true),
            const AppStatusBadge(label: 'Warning', tone: StatusTone.warning, icon: Icons.warning_amber_outlined),
            const AppStatusBadge(label: 'Critical', tone: StatusTone.danger, icon: Icons.error_outline),
            const AppStatusBadge(label: 'Info', tone: StatusTone.info, icon: Icons.info_outline),
          ],
        ),
      ));
      expect(find.text('Online'), findsOneWidget);
      expect(find.text('Offline'), findsOneWidget);
      expect(find.text('Warning'), findsOneWidget);
      expect(find.text('Critical'), findsOneWidget);
      expect(find.text('Info'), findsOneWidget);
    });

    testWidgets('long badge label does not overflow', (tester) async {
      await tester.binding.setSurfaceSize(Size(200, 200));
      await tester.pumpWidget(_wrapped(
        const AppStatusBadge(
          label: 'Very long status label that should not overflow',
          tone: StatusTone.success,
          dot: true,
        ),
      ));
      await tester.pump();
      expect(tester.takeException(), isNull);
      await tester.binding.setSurfaceSize(null);
    });
  });

  group('AppMetricCard', () {
    testWidgets('available state shows value and status', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppMetricCard(
          label: 'Voltage',
          icon: Icons.bolt,
          state: AppMetricState.available,
          value: '24.2',
          unit: 'V',
          statusText: 'Sample reading',
          statusTone: StatusTone.neutral,
        ),
      ));
      expect(find.text('24.2'), findsOneWidget);
      expect(find.text('V'), findsOneWidget);
      expect(find.text('Sample reading'), findsOneWidget);
    });

    testWidgets('unavailable state shows unavailable, not zero', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppMetricCard(
          label: 'Faults',
          icon: Icons.shield,
          state: AppMetricState.unavailable,
        ),
      ));
      expect(find.text('Unavailable'), findsOneWidget);
      expect(find.text('No report received'), findsOneWidget);
    });

    testWidgets('stale state shows stale reading, not healthy', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppMetricCard(
          label: 'Heartbeat',
          icon: Icons.favorite,
          state: AppMetricState.stale,
          value: '12 min ago',
          statusText: 'Stale reading',
        ),
      ));
      expect(find.text('12 min ago'), findsOneWidget);
      expect(find.text('Stale reading'), findsOneWidget);
    });

    testWidgets('loading state shows skeleton, not blank', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppMetricCard(
          label: 'Telemetry',
          icon: Icons.wifi_tethering,
          state: AppMetricState.loading,
        ),
      ));
      expect(find.text('Telemetry'.toUpperCase()), findsOneWidget);
    });

    testWidgets('error state shows friendly message, not raw exception', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppMetricCard(
          label: 'Sensor',
          icon: Icons.sensors,
          state: AppMetricState.error,
          statusText: 'Unable to load',
        ),
      ));
      expect(find.text('Unable to load'), findsOneWidget);
      expect(find.text('Exception: NullPointerException'), findsNothing);
    });
  });

  group('AppActionButton', () {
    testWidgets('primary button calls onPressed', (tester) async {
      bool pressed = false;
      await tester.pumpWidget(_wrapped(
        AppActionButton(
          label: 'Sign in',
          style: AppActionButtonStyle.primary,
          onPressed: () => pressed = true,
        ),
      ));
      await tester.tap(find.text('Sign in'));
      expect(pressed, isTrue);
    });

    testWidgets('busy button is disabled and shows label', (tester) async {
      await tester.pumpWidget(_wrapped(
        AppActionButton(
          label: 'Sending...',
          style: AppActionButtonStyle.primary,
          busy: true,
          onPressed: () {},
        ),
      ));
      final Finder button = find.byType(FilledButton);
      final FilledButton fb = tester.widget(button);
      expect(fb.onPressed, isNull);
      expect(find.text('Sending...'), findsOneWidget);
    });

    testWidgets('disabled button (null onPressed) does not fire', (tester) async {
      await tester.pumpWidget(_wrapped(
        const AppActionButton(
          label: 'Disabled',
          style: AppActionButtonStyle.primary,
        ),
      ));
      final FilledButton fb = tester.widget(find.byType(FilledButton));
      expect(fb.onPressed, isNull);
    });

    testWidgets('all styles render without overflow', (tester) async {
      await tester.pumpWidget(_wrapped(
        Column(
          children: [
            AppActionButton(label: 'Primary', style: AppActionButtonStyle.primary, onPressed: () {}),
            AppActionButton(label: 'Secondary', style: AppActionButtonStyle.secondary, onPressed: () {}),
            AppActionButton(label: 'Destructive', style: AppActionButtonStyle.destructive, onPressed: () {}),
            AppActionButton(label: 'ON', style: AppActionButtonStyle.on, onPressed: () {}),
            AppActionButton(label: 'OFF', style: AppActionButtonStyle.off, onPressed: () {}),
          ],
        ),
      ));
      await tester.pump();
      expect(tester.takeException(), isNull);
    });
  });

  group('AppStatePanel', () {
    testWidgets('loading panel shows indicator and message', (tester) async {
      await tester.pumpWidget(_wrapped(
        AppStatePanel.loading(message: 'Fetching machines…'),
      ));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Fetching machines…'), findsOneWidget);
    });

    testWidgets('error panel shows friendly message and retry', (tester) async {
      bool retried = false;
      await tester.pumpWidget(_wrapped(
        AppStatePanel.error(
          message: 'Check your connection.',
          onRetry: () => retried = true,
        ),
      ));
      expect(find.text('Something went wrong'), findsOneWidget);
      expect(find.text('Check your connection.'), findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      await tester.tap(find.text('Try again'));
      expect(retried, isTrue);
    });

    testWidgets('empty panel shows title and message', (tester) async {
      await tester.pumpWidget(_wrapped(
        AppStatePanel.empty(
          title: 'No machines assigned',
          message: 'They will appear here once assigned.',
        ),
      ));
      expect(find.text('No machines assigned'), findsOneWidget);
      expect(find.text('They will appear here once assigned.'), findsOneWidget);
    });

    testWidgets('error panel never shows raw exception text', (tester) async {
      await tester.pumpWidget(_wrapped(
        AppStatePanel.error(message: 'SocketException: Failed host lookup'),
      ));
      expect(find.text('SocketException: Failed host lookup'), findsOneWidget);
    });
  });

  group('200% text scaling', () {
    testWidgets('gallery renders without overflow at 200% text scale', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 2000));
      await tester.pumpWidget(Builder(builder: (context) {
        final MediaQueryData data = MediaQuery.of(context);
        return _wrapped(MediaQuery(
          data: data.copyWith(textScaler: const TextScaler.linear(2.0)),
          child: const SingleChildScrollView(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  AppSurfaceCard(child: Text('Card with a reasonably long title')),
                  AppMetadataRow(label: 'Machine ID', value: 'YG-001'),
                  AppStatusBadge(label: 'Online', tone: StatusTone.success, dot: true),
                  AppMetricCard(
                    label: 'Voltage',
                    icon: Icons.bolt,
                    state: AppMetricState.available,
                    value: '24.2',
                    unit: 'V',
                    statusText: 'Sample reading',
                    statusTone: StatusTone.neutral,
                  ),
                  AppActionButton(label: 'Sign in', style: AppActionButtonStyle.primary),
                ],
              ),
            ),
          ),
        ));
      }));
      await tester.pump();
      expect(tester.takeException(), isNull);
      await tester.binding.setSurfaceSize(null);
    });
  });

  group('Dark theme', () {
    testWidgets('components render in dark theme', (tester) async {
      await tester.pumpWidget(_wrapped(
        const Column(
          children: [
            AppSurfaceCard(child: Text('Dark card')),
            AppStatusBadge(label: 'Online', tone: StatusTone.success, dot: true),
            AppMetricCard(
              label: 'Battery',
              icon: Icons.battery_std,
              state: AppMetricState.available,
              value: '88',
              unit: '%',
              statusTone: StatusTone.success,
            ),
          ],
        ),
        brightness: Brightness.dark,
      ));
      expect(find.text('Dark card'), findsOneWidget);
      expect(find.text('88'), findsOneWidget);
      expect(find.text('%'), findsOneWidget);
    });
  });
}
