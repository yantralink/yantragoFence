import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Settings provider — manages app and machine settings.
class SettingsProvider extends StateNotifier<SettingsState> {
  SettingsProvider() : super(const SettingsState());

  void setApiUrl(String url) {
    state = state.copyWith(apiUrl: url);
  }

  void setNotificationsEnabled(bool enabled) {
    state = state.copyWith(notificationsEnabled: enabled);
  }
}

class SettingsState {
  final String apiUrl;
  final bool notificationsEnabled;

  const SettingsState({
    this.apiUrl = 'https://yantrago.com',
    this.notificationsEnabled = true,
  });

  SettingsState copyWith({
    String? apiUrl,
    bool? notificationsEnabled,
  }) {
    return SettingsState(
      apiUrl: apiUrl ?? this.apiUrl,
      notificationsEnabled: notificationsEnabled ?? this.notificationsEnabled,
    );
  }
}

final settingsProvider =
    StateNotifierProvider<SettingsProvider, SettingsState>((ref) {
  return SettingsProvider();
});
