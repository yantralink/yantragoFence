// Tests for real-time command status updates (Phase 5).
//
// Covers: CommandStatusUpdate parsing of the backend broadcast payload,
// Command.copyWith / terminal-state getters, and CommandNotifier.applyStatusUpdate
// matching rules.

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/models/command.dart';
import 'package:yantrago/models/command_status_update.dart';

Command _command({
  String id = 'c1',
  String machineId = 'm1',
  String status = 'SENT',
  String? lastError,
}) {
  return Command(
    id: id,
    machineId: machineId,
    deviceId: 'd1',
    imei: '000000000000001',
    commandType: 'ON',
    status: status,
    attemptCount: 1,
    maxAttempts: 3,
    lastError: lastError,
    createdAt: DateTime(2026, 1, 1),
  );
}

CommandStatusUpdate _update({
  String commandId = 'c1',
  String machineId = 'm1',
  String status = 'ACK',
  int attemptCount = 1,
  String? error,
}) {
  return CommandStatusUpdate(
    commandId: commandId,
    machineId: machineId,
    status: status,
    attemptCount: attemptCount,
    error: error,
  );
}

class _SeededNotifier extends CommandNotifier {
  _SeededNotifier(Command? c) : super(Dio()) {
    if (c != null) seedLastCommand(c);
  }
}

void main() {
  group('CommandStatusUpdate.fromJson', () {
    test('parses the backend broadcast payload', () {
      final update = CommandStatusUpdate.fromJson({
        'commandId': 'c1',
        'machineId': 'm1',
        'status': 'ACK',
        'attemptCount': 2,
        'error': '',
        'timestamp': '2026-01-01T00:00:00',
      });
      expect(update.commandId, 'c1');
      expect(update.machineId, 'm1');
      expect(update.status, 'ACK');
      expect(update.attemptCount, 2);
      expect(update.error, isNull, reason: 'empty error string normalizes to null');
    });

    test('keeps non-empty error text', () {
      final update = CommandStatusUpdate.fromJson({
        'commandId': 'c1',
        'machineId': 'm1',
        'status': 'FAILED',
        'attemptCount': 3,
        'error': 'Device not online',
      });
      expect(update.error, 'Device not online');
    });

    test('defaults attemptCount to 0 when missing', () {
      final update = CommandStatusUpdate.fromJson({
        'commandId': 'c1',
        'machineId': 'm1',
        'status': 'SENT',
      });
      expect(update.attemptCount, 0);
    });
  });

  group('Command model', () {
    test('copyWith preserves untouched fields', () {
      final original = _command(status: 'SENT');
      final copy = original.copyWith(status: 'ACK', attemptCount: 2);
      expect(copy.id, original.id);
      expect(copy.machineId, original.machineId);
      expect(copy.commandType, original.commandType);
      expect(copy.createdAt, original.createdAt);
      expect(copy.status, 'ACK');
      expect(copy.attemptCount, 2);
    });

    test('isTimeout / isTerminal', () {
      expect(_command(status: 'TIMEOUT').isTimeout, isTrue);
      expect(_command(status: 'TIMEOUT').isTerminal, isTrue);
      expect(_command(status: 'DONE').isTerminal, isTrue);
      expect(_command(status: 'FAILED').isTerminal, isTrue);
      expect(_command(status: 'SENT').isTerminal, isFalse);
      expect(_command(status: 'ACK').isTerminal, isFalse);
    });
  });

  group('CommandNotifier.applyStatusUpdate', () {
    test('SENT → ACK updates status and stays pending', () {
      final notifier = _SeededNotifier(_command(status: 'SENT'));
      notifier.applyStatusUpdate(_update(status: 'ACK', attemptCount: 1));
      expect(notifier.state.lastCommand?.status, 'ACK');
      expect(notifier.state.pending, isTrue);
    });

    test('→ DONE clears pending and sets completedAt', () {
      final notifier = _SeededNotifier(_command(status: 'ACK'));
      notifier.applyStatusUpdate(_update(status: 'DONE'));
      expect(notifier.state.lastCommand?.isDone, isTrue);
      expect(notifier.state.lastCommand?.completedAt, isNotNull);
      expect(notifier.state.pending, isFalse);
    });

    test('→ TIMEOUT marks timeout and carries error', () {
      final notifier = _SeededNotifier(_command(status: 'SENT'));
      notifier.applyStatusUpdate(
        _update(status: 'TIMEOUT', error: 'Device did not respond'),
      );
      expect(notifier.state.lastCommand?.isTimeout, isTrue);
      expect(notifier.state.lastCommand?.lastError, 'Device did not respond');
      expect(notifier.state.pending, isFalse);
    });

    test('ignores updates for a different commandId', () {
      final notifier = _SeededNotifier(_command(id: 'c1', status: 'SENT'));
      notifier.applyStatusUpdate(_update(commandId: 'other', status: 'DONE'));
      expect(notifier.state.lastCommand?.status, 'SENT');
      expect(notifier.state.pending, isTrue);
    });

    test('ignores updates when no command has been sent', () {
      final notifier = _SeededNotifier(null);
      notifier.applyStatusUpdate(_update(status: 'DONE'));
      expect(notifier.state.lastCommand, isNull);
      expect(notifier.state.pending, isFalse);
    });
  });
}
