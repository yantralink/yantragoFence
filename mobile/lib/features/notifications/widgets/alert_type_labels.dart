import 'package:yantrago/l10n/generated/app_localizations.dart';

/// Localized alert type label for a raw backend alert type code.
///
/// Widget layer helper — the model keeps the raw code (e.g. 'LOW_BATTERY');
/// this maps it to the user-facing label. Unknown codes fall back to the
/// de-underscored raw code, which is treated as a technical display
/// (always-English carve-out).
String alertTypeLabel(AppLocalizations l10n, String alertType) {
  switch (alertType) {
    case 'LOW_BATTERY':
      return l10n.alertLowBattery;
    case 'VOLTAGE_DROP':
      return l10n.alertVoltageDrop;
    case 'GSM_SIGNAL_LOW':
      return l10n.alertGsmSignalLow;
    case 'DEVICE_OFFLINE':
      return l10n.alertDeviceOffline;
    case 'SIM_EXPIRY':
      return l10n.alertSimExpiry;
    case 'EXTERNAL_POWER_LOW':
      return l10n.alertExternalPowerLow;
    case 'EXTERNAL_POWER_CUT':
      return l10n.alertExternalPowerCut;
    case 'LOW_POWER_SHUTDOWN':
      return l10n.alertLowPowerShutdown;
    case 'INTERNAL_BATTERY_LOW':
      return l10n.alertInternalBatteryLow;
    case 'COMMAND_ACK':
      return l10n.alertCommandAck;
    case 'MACHINE_ON':
      return l10n.alertMachineOn;
    case 'MACHINE_OFF':
      return l10n.alertMachineOff;
    case 'COMMAND_FAILED':
      return l10n.alertCommandFailed;
    case 'MACHINE_MOVING':
      return l10n.alertMachineMoving;
    case 'GEOFENCE_BREACH':
      return l10n.alertGeofenceBreach;
    default:
      return alertType.replaceAll('_', ' ');
  }
}
