import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/models/telemetry.dart';

void main() {
  group('Telemetry.fromJson', () {
    test('maps batteryPct from backend TelemetryLatestDto', () {
      final json = <String, dynamic>{
        'batteryPct': 60.0,
        'charging': true,
        'gsmSignal': 3,
        'voltage': null,
        'lastTelemetryAt': '2025-09-11T12:00:00',
      };

      final telemetry = Telemetry.fromJson(json);

      expect(telemetry.battery, 60);
      expect(telemetry.charging, true);
      expect(telemetry.gsmSignal, 3);
      expect(telemetry.voltage, isNull);
      expect(telemetry.timestamp, DateTime.parse('2025-09-11T12:00:00'));
    });

    test('handles all-null response (no heartbeat received)', () {
      final json = <String, dynamic>{
        'batteryPct': null,
        'charging': null,
        'gsmSignal': null,
        'voltage': null,
        'lastTelemetryAt': null,
      };

      final telemetry = Telemetry.fromJson(json);

      expect(telemetry.battery, isNull);
      expect(telemetry.charging, isNull);
      expect(telemetry.gsmSignal, isNull);
      expect(telemetry.voltage, isNull);
      expect(telemetry.timestamp, isNull);
    });

    test('converts batteryPct double to int for UI widget', () {
      final json = <String, dynamic>{
        'batteryPct': 80.0,
        'charging': false,
        'gsmSignal': 2,
      };

      final telemetry = Telemetry.fromJson(json);

      expect(telemetry.battery, 80);
      expect(telemetry.charging, false);
      expect(telemetry.gsmSignal, 2);
    });

    test('parses external voltage from 0x94 info packet', () {
      final json = <String, dynamic>{
        'batteryPct': 100.0,
        'charging': true,
        'gsmSignal': 4,
        'voltage': 12.22,
        'lastTelemetryAt': '2026-09-12T09:09:53',
      };

      final telemetry = Telemetry.fromJson(json);

      expect(telemetry.voltage, 12.22);
      expect(telemetry.battery, 100);
      expect(telemetry.charging, true);
      expect(telemetry.gsmSignal, 4);
    });

    test('falls back to legacy "battery" field if batteryPct is absent', () {
      final json = <String, dynamic>{
        'battery': 40,
        'charging': null,
        'gsmSignal': null,
      };

      final telemetry = Telemetry.fromJson(json);

      expect(telemetry.battery, 40);
    });

    test('empty constant has all null fields', () {
      const telemetry = Telemetry.empty;

      expect(telemetry.battery, isNull);
      expect(telemetry.charging, isNull);
      expect(telemetry.gsmSignal, isNull);
      expect(telemetry.voltage, isNull);
      expect(telemetry.timestamp, isNull);
    });
  });
}
