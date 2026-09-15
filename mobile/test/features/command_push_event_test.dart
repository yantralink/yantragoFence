// Tests for FCM command push events (Phase 6).
//
// Covers: CommandPushEvent.fromData parsing of the backend FCM data payload,
// alertType/incidentState → Command.status mapping, and integration with
// CommandNotifier.applyStatusUpdate from Phase 5.

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/models/command.dart';
import 'package:yantrago/models/command_push_event.dart';
import 'package:yantrago/models/command_status_update.dart';

Map<String, dynamic> _data({
  String? alertType = 'MACHINE_ON',
  String? incidentState = 'DONE',
  String? machineId = 'm1',
  String? alertId = 'c1',
}) {
  return <String, dynamic>{
    if (alertType != null) 'alertType': alertType,
    if (incidentState != null) 'incidentState': incidentState,
    'severity': 'INFO',
    if (machineId != null) 'machineId': machineId,
    'inboxId': 'i1',
    if (alertId != null) 'alertId': alertId,
  };
}

class _SeededNotifier extends CommandNotifier {
  _SeededNotifier(Command c) : super(Dio()) {
    seedLastCommand(c);
  }
}

void main() {
  group('CommandPushEvent.fromData', () {
    for (final type in CommandPushEvent.commandAlertTypes) {
      test('parses $type', () {
        final event = CommandPushEvent.fromData(_data(alertType: type));
        expect(event, isNotNull);
        expect(event!.alertType, type);
        expect(event.machineId, 'm1');
        expect(event.commandId, 'c1');
      });
    }

    test('returns null for a non-command alert type', () {
      expect(CommandPushEvent.fromData(_data(alertType: 'LOW_BATTERY')), isNull);
    });

    test('returns null when alertType is missing', () {
      expect(CommandPushEvent.fromData(_data(alertType: null)), isNull);
    });

    test('returns null when machineId is missing', () {
      expect(CommandPushEvent.fromData(_data(machineId: null)), isNull);
    });

    test('commandId is null when alertId is absent', () {
      final event = CommandPushEvent.fromData(_data(alertId: null));
      expect(event, isNotNull);
      expect(event!.commandId, isNull);
    });

    test('commandId is null when alertId is empty', () {
      final event = CommandPushEvent.fromData(_data(alertId: ''));
      expect(event!.commandId, isNull);
    });
  });

  group('CommandPushEvent.commandStatus', () {
    CommandPushEvent event(String type, String state) => CommandPushEvent(
          machineId: 'm1',
          alertType: type,
          incidentState: state,
        );

    test('COMMAND_ACK → ACK', () {
      expect(event('COMMAND_ACK', 'ACK').commandStatus, 'ACK');
    });
    test('MACHINE_ON → DONE', () {
      expect(event('MACHINE_ON', 'DONE').commandStatus, 'DONE');
    });
    test('MACHINE_OFF → DONE', () {
      expect(event('MACHINE_OFF', 'DONE').commandStatus, 'DONE');
    });
    test('COMMAND_FAILED + FAILED → FAILED', () {
      expect(event('COMMAND_FAILED', 'FAILED').commandStatus, 'FAILED');
    });
    test('COMMAND_FAILED + TIMEOUT → TIMEOUT', () {
      expect(event('COMMAND_FAILED', 'TIMEOUT').commandStatus, 'TIMEOUT');
    });
  });

  group('integration with CommandNotifier.applyStatusUpdate', () {
    Command sent() => Command(
          id: 'c1',
          machineId: 'm1',
          commandType: 'ON',
          status: 'SENT',
          attemptCount: 1,
          createdAt: DateTime(2026, 1, 1),
        );

    CommandStatusUpdate toUpdate(CommandPushEvent e, int attemptCount) =>
        CommandStatusUpdate(
          commandId: e.commandId!,
          machineId: e.machineId,
          status: e.commandStatus,
          attemptCount: attemptCount,
        );

    test('MACHINE_ON push marks the last command DONE', () {
      final notifier = _SeededNotifier(sent());
      final event = CommandPushEvent.fromData(_data(alertType: 'MACHINE_ON'))!;
      notifier.applyStatusUpdate(toUpdate(event, 1));
      expect(notifier.state.lastCommand?.isDone, isTrue);
      expect(notifier.state.pending, isFalse);
    });

    test('COMMAND_FAILED/TIMEOUT push marks the last command TIMEOUT', () {
      final notifier = _SeededNotifier(sent());
      final event = CommandPushEvent.fromData(
        _data(alertType: 'COMMAND_FAILED', incidentState: 'TIMEOUT'),
      )!;
      notifier.applyStatusUpdate(toUpdate(event, 1));
      expect(notifier.state.lastCommand?.isTimeout, isTrue);
      expect(notifier.state.pending, isFalse);
    });

    test('push for a different command leaves state unchanged', () {
      final notifier = _SeededNotifier(sent());
      final event = CommandPushEvent.fromData(_data(alertId: 'other'))!;
      notifier.applyStatusUpdate(toUpdate(event, 1));
      expect(notifier.state.lastCommand?.status, 'SENT');
      expect(notifier.state.pending, isTrue);
    });
  });
}
