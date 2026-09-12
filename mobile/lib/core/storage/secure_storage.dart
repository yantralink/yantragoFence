import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Secure storage wrapper — stores JWT tokens and sensitive data.
///
/// Per AGENTS.md rule 20: never commit secrets. Tokens are stored
/// in the platform keychain (iOS Keychain / Android EncryptedSharedPreferences).
class SecureStorage {
  SecureStorage._();

  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  static const _keyAccessToken = 'access_token';
  static const _keyRefreshToken = 'refresh_token';
  static const _keyUserId = 'user_id';
  static const _keyOrgId = 'org_id';
  static const _keyOrgName = 'org_name';
  static const _keyFcmToken = 'fcm_token';
  static const _keyFcmTokenRegistered = 'fcm_token_registered';

  static Future<String?> getAccessToken() => _storage.read(key: _keyAccessToken);
  static Future<void> setAccessToken(String? token) async {
    if (token == null) {
      await _storage.delete(key: _keyAccessToken);
    } else {
      await _storage.write(key: _keyAccessToken, value: token);
    }
  }

  static Future<String?> getRefreshToken() => _storage.read(key: _keyRefreshToken);
  static Future<void> setRefreshToken(String? token) async {
    if (token == null) {
      await _storage.delete(key: _keyRefreshToken);
    } else {
      await _storage.write(key: _keyRefreshToken, value: token);
    }
  }

  static Future<String?> getUserId() => _storage.read(key: _keyUserId);
  static Future<void> setUserId(String? id) async {
    if (id == null) {
      await _storage.delete(key: _keyUserId);
    } else {
      await _storage.write(key: _keyUserId, value: id);
    }
  }

  static Future<String?> getOrgId() => _storage.read(key: _keyOrgId);
  static Future<void> setOrgId(String? id) async {
    if (id == null) {
      await _storage.delete(key: _keyOrgId);
    } else {
      await _storage.write(key: _keyOrgId, value: id);
    }
  }

  static Future<String?> getOrgName() => _storage.read(key: _keyOrgName);
  static Future<void> setOrgName(String? name) async {
    if (name == null) {
      await _storage.delete(key: _keyOrgName);
    } else {
      await _storage.write(key: _keyOrgName, value: name);
    }
  }

  static Future<void> clearAll() async {
    await _storage.deleteAll();
  }

  // FCM token storage (Phase 5)
  static Future<String?> getFcmToken() => _storage.read(key: _keyFcmToken);
  static Future<void> setFcmToken(String? token) async {
    if (token == null) {
      await _storage.delete(key: _keyFcmToken);
    } else {
      await _storage.write(key: _keyFcmToken, value: token);
    }
  }

  static Future<bool> isFcmTokenRegistered() async {
    final val = await _storage.read(key: _keyFcmTokenRegistered);
    return val == 'true';
  }

  static Future<void> setFcmTokenRegistered(bool registered) async {
    await _storage.write(
        key: _keyFcmTokenRegistered, value: registered ? 'true' : 'false');
  }
}
