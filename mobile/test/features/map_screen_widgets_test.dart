import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/machines/widgets/environmental_strip.dart';
import 'package:yantrago/features/machines/widgets/machine_action_pills.dart';
import 'package:yantrago/features/machines/widgets/map_location_card.dart';
import 'package:yantrago/features/machines/widgets/map_overlay_widgets.dart';
import 'package:yantrago/features/machines/widgets/map_speed_gauge.dart';
import 'package:yantrago/models/machine.dart';

Machine _machine({String? fencingState = 'OFF'}) => Machine(
      id: 'm1',
      machineId: 'YG000009',
      name: 'Shubham Hedau',
      status: 'ACTIVE',
      isOnline: true,
      fencingState: fencingState,
      createdAt: DateTime(2026),
    );

Future<void> _pump(WidgetTester tester, Widget child,
    {List<Override> overrides = const []}) async {
  await tester.pumpWidget(ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      home: Scaffold(body: Center(child: child)),
    ),
  ));
}

void main() {
  group('MapStatusChip', () {
    testWidgets('renders label and dot', (tester) async {
      await _pump(tester, const MapStatusChip(
          label: 'Moving', dotColor: Colors.green));
      expect(find.text('Moving'), findsOneWidget);
    });
  });

  group('MapZoomPill', () {
    testWidgets('zoom callbacks fire', (tester) async {
      var zoomedIn = false, zoomedOut = false;
      await _pump(
          tester,
          MapZoomPill(
            onZoomIn: () => zoomedIn = true,
            onZoomOut: () => zoomedOut = true,
          ));
      await tester.tap(find.byIcon(Icons.add));
      await tester.tap(find.byIcon(Icons.remove));
      expect(zoomedIn, isTrue);
      expect(zoomedOut, isTrue);
    });
  });

  group('MapSpeedGauge', () {
    testWidgets('renders value and unit', (tester) async {
      await _pump(tester,
          const MapSpeedGauge(valueLabel: '36', unitLabel: 'km/h'));
      expect(find.text('36'), findsOneWidget);
      expect(find.text('km/h'), findsOneWidget);
    });
  });

  group('EnvironmentalStrip', () {
    testWidgets('renders all three items', (tester) async {
      await _pump(
          tester,
          const EnvironmentalStrip(
            conditionLabel: 'Good operating condition',
            conditionIcon: Icons.check_circle,
            weatherLabel: '24°C Clear',
            windLabel: 'Wind 11.9 km/h',
          ));
      expect(find.text('Good operating condition'), findsOneWidget);
      expect(find.text('24°C Clear'), findsOneWidget);
      expect(find.text('Wind 11.9 km/h'), findsOneWidget);
    });
  });

  group('MapLocationCard', () {
    testWidgets('renders address and updated label', (tester) async {
      await _pump(
          tester,
          const MapLocationCard(
            locationLabel: 'LOCATION',
            address: 'Mahatma Gandhi Road, Bengaluru',
            updatedLabel: 'Updated 1s ago',
            latitude: 12.97,
            longitude: 77.59,
          ));
      expect(find.text('LOCATION'), findsOneWidget);
      expect(
          find.text('Mahatma Gandhi Road, Bengaluru'), findsOneWidget);
      expect(find.text('Updated 1s ago'), findsOneWidget);
      expect(find.byIcon(Icons.share_outlined), findsOneWidget);
    });
  });

  group('MachineActionPills', () {
    testWidgets('hides pills when states are null', (tester) async {
      await _pump(
          tester,
          MachineActionPills(
            machine: _machine(fencingState: 'UNKNOWN'),
            machineOn: null,
            machineOnLabel: 'Machine ON',
            machineOffLabel: 'Machine OFF',
            fenceOnLabel: 'Fence ON',
            fenceOffLabel: 'Fence OFF',
            scheduleLabel: 'Set Schedule',
            onScheduleTap: () {},
          ));
      expect(find.text('Machine ON'), findsNothing);
      expect(find.text('Set Schedule'), findsOneWidget);
    });

    testWidgets('shows fence OFF pill when fencing off', (tester) async {
      await _pump(
          tester,
          MachineActionPills(
            machine: _machine(fencingState: 'OFF'),
            machineOn: true,
            machineOnLabel: 'Machine ON',
            machineOffLabel: 'Machine OFF',
            fenceOnLabel: 'Fence ON',
            fenceOffLabel: 'Fence OFF',
            scheduleLabel: 'Set Schedule',
            onScheduleTap: () {},
          ));
      expect(find.text('Machine ON'), findsOneWidget);
      expect(find.text('Fence OFF'), findsOneWidget);
      expect(find.text('Set Schedule'), findsOneWidget);
    });

    testWidgets('schedule pill tap fires callback', (tester) async {
      var tapped = false;
      await _pump(
          tester,
          MachineActionPills(
            machine: _machine(),
            machineOn: true,
            machineOnLabel: 'Machine ON',
            machineOffLabel: 'Machine OFF',
            fenceOnLabel: 'Fence ON',
            fenceOffLabel: 'Fence OFF',
            scheduleLabel: 'Set Schedule',
            onScheduleTap: () => tapped = true,
          ));
      await tester.tap(find.text('Set Schedule'));
      expect(tapped, isTrue);
    });
  });
}
