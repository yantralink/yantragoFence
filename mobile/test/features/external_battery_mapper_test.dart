import 'package:flutter_test/flutter_test.dart';

import 'package:yantrago/features/dashboard/widgets/external_battery_mapper.dart';

void main() {
  group('voltageToExternalBatteryPct', () {
    test('returns null for null voltage (no 0x94 report)', () {
      expect(voltageToExternalBatteryPct(null), isNull);
    });

    test('returns null for non-positive voltage (bad sensor frame)', () {
      expect(voltageToExternalBatteryPct(0), isNull);
      expect(voltageToExternalBatteryPct(-1.0), isNull);
    });

    test('clamps below 10.5 V to 0% (deep discharge)', () {
      expect(voltageToExternalBatteryPct(10.4), 0);
      expect(voltageToExternalBatteryPct(9.8), 0);
    });

    test('maps exactly 10.5 V to 0%', () {
      expect(voltageToExternalBatteryPct(10.5), 0);
    });

    test('maps exactly 13.5 V to 100%', () {
      expect(voltageToExternalBatteryPct(13.5), 100);
    });

    test('clamps above 13.5 V to 100%', () {
      expect(voltageToExternalBatteryPct(13.6), 100);
      expect(voltageToExternalBatteryPct(15.0), 100);
    });

    test('maps midpoint 12.0 V to 50%', () {
      expect(voltageToExternalBatteryPct(12.0), 50);
    });

    test('maps 10.95 V to 15% (linear interpolation)', () {
      expect(voltageToExternalBatteryPct(10.95), 15);
    });

    test('maps 13.05 V to 85%', () {
      expect(voltageToExternalBatteryPct(13.05), 85);
    });

    test('floors to int — never overstates charge', () {
      // 11.0 V → 16.66…% → floor 16, not 17.
      expect(voltageToExternalBatteryPct(11.0), 16);
      // 12.3299 V → 60.996…% → floor 60, not 61.
      expect(voltageToExternalBatteryPct(12.3299), 60);
    });

    test('boundary 10.8 V maps to exactly 10% (critical/low edge)', () {
      expect(voltageToExternalBatteryPct(10.8), 10);
    });
  });

  group('externalBatteryZone', () {
    test('0% is critical', () {
      expect(externalBatteryZone(0), BatteryZone.critical);
    });

    test('9% is critical (below 10 threshold)', () {
      expect(externalBatteryZone(9), BatteryZone.critical);
    });

    test('10% is low (boundary — not below threshold)', () {
      expect(externalBatteryZone(10), BatteryZone.low);
    });

    test('29% is low', () {
      expect(externalBatteryZone(29), BatteryZone.low);
    });

    test('30% is normal (boundary)', () {
      expect(externalBatteryZone(30), BatteryZone.normal);
    });

    test('94% is normal', () {
      expect(externalBatteryZone(94), BatteryZone.normal);
    });

    test('95% is full (boundary)', () {
      expect(externalBatteryZone(95), BatteryZone.full);
    });

    test('100% is full', () {
      expect(externalBatteryZone(100), BatteryZone.full);
    });
  });
}
