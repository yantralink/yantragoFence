import 'package:yantrago/core/storage/secure_storage.dart';

/// Token manager — manages JWT token storage and retrieval.
///
/// Per AGENTS.md rule 20: tokens are stored in the platform keychain,
/// never in shared preferences or plain text.
class TokenManager {
  TokenManager._();

  static Future<String?> getAccessToken() => SecureStorage.getAccessToken();
  static Future<String?> getRefreshToken() => SecureStorage.getRefreshToken();

  static Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await SecureStorage.setAccessToken(accessToken);
    await SecureStorage.setRefreshToken(refreshToken);
  }

  static Future<void> clearTokens() async {
    await SecureStorage.setAccessToken(null);
    await SecureStorage.setRefreshToken(null);
  }

  static Future<bool> hasTokens() async {
    final token = await getAccessToken();
    return token != null && token.isNotEmpty;
  }
}
