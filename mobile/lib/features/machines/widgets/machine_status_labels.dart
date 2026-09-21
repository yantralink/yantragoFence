import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/models/machine.dart';

/// Localized machine status/state labels shared by the machine list card
/// and the machine info card on the detail page.
///
/// Widget layer helper — the [Machine] model keeps the raw status code
/// (e.g. 'FENCING_ON'); only the user-facing badge/label is translated.

/// Badge label for the machine's connectivity/status.
String machineStatusLabel(AppLocalizations l10n, Machine m) {
  if (m.isFault) return l10n.statusFault;
  if (m.isOnlineStatus) return l10n.statusOnline;
  if (m.isInStock) return l10n.statusInStock;
  return l10n.statusOffline;
}

/// Descriptive state line under the machine card header.
String machineStateLabel(AppLocalizations l10n, Machine m) {
  if (m.isFault) return l10n.stateFaultReported;
  if (m.isFencingOn) return l10n.stateFenceOnConfirmed;
  if (m.isOnlineStatus) return l10n.stateFenceOffConfirmed;
  if (m.isInStock) return l10n.statusInStock;
  return l10n.stateUnknown;
}
