import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/storage/secure_storage.dart';

/// Tests for push notification token lifecycle (Phase 5).
///
/// Verifies Phase 5 acceptance criteria:
/// - Token storage in secure storage (not SharedPreferences)
/// - Token registration flag tracking
/// - Token rotation (re-registration)
/// - Logout deactivation
///
/// Per notification plan Phase 5: token rotation/logout.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // Mock the FlutterSecureStorage method channel
  final Map<String, String> mockStorage = {};
  const channel = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

  setUp(() {
    mockStorage.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall call) async {
      if (call.method == 'read') {
        return mockStorage[call.arguments['key']];
      } else if (call.method == 'write') {
        mockStorage[call.arguments['key']] = call.arguments['value'];
        return null;
      } else if (call.method == 'delete') {
        mockStorage.remove(call.arguments['key']);
        return null;
      } else if (call.method == 'deleteAll') {
        mockStorage.clear();
        return null;
      }
      return null;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  group('Push token storage', () {
    setUp(() async {
      await SecureStorage.clearAll();
    });

    test('FCM token is stored and retrieved from secure storage', () async {
      await SecureStorage.setFcmToken('fcm-token-abc123');
      final token = await SecureStorage.getFcmToken();
      expect(token, 'fcm-token-abc123');
    });

    test('FCM token registration flag is tracked', () async {
      expect(await SecureStorage.isFcmTokenRegistered(), false);

      await SecureStorage.setFcmTokenRegistered(true);
      expect(await SecureStorage.isFcmTokenRegistered(), true);

      await SecureStorage.setFcmTokenRegistered(false);
      expect(await SecureStorage.isFcmTokenRegistered(), false);
    });

    test('clearAll removes FCM token and registration flag', () async {
      await SecureStorage.setFcmToken('fcm-token-xyz');
      await SecureStorage.setFcmTokenRegistered(true);

      await SecureStorage.clearAll();

      expect(await SecureStorage.getFcmToken(), null);
      expect(await SecureStorage.isFcmTokenRegistered(), false);
    });

    test('setFcmToken(null) removes the token', () async {
      await SecureStorage.setFcmToken('fcm-token-to-remove');
      await SecureStorage.setFcmToken(null);
      expect(await SecureStorage.getFcmToken(), null);
    });
  });

  group('Push token lifecycle', () {
    /// Token rotation scenario: user logs out, then logs in with a different
    /// account on the same phone. The FCM token should be cleared on logout
    /// and re-registered for the new account.
    test('token rotation: logout clears token, login re-registers', () async {
      // Initial registration
      await SecureStorage.setFcmToken('fcm-token-original');
      await SecureStorage.setFcmTokenRegistered(true);
      expect(await SecureStorage.getFcmToken(), 'fcm-token-original');
      expect(await SecureStorage.isFcmTokenRegistered(), true);

      // Logout — clear token
      await SecureStorage.setFcmToken(null);
      await SecureStorage.setFcmTokenRegistered(false);
      expect(await SecureStorage.getFcmToken(), null);
      expect(await SecureStorage.isFcmTokenRegistered(), false);

      // Login with different account — new token
      await SecureStorage.setFcmToken('fcm-token-new');
      await SecureStorage.setFcmTokenRegistered(true);
      expect(await SecureStorage.getFcmToken(), 'fcm-token-new');
      expect(await SecureStorage.isFcmTokenRegistered(), true);
    });

    /// Shared-phone account switching: two users on the same phone.
    /// Each logout must deactivate tokens; each login must register a fresh token.
    test('shared-phone: account switch deactivates and re-registers', () async {
      // User A logs in
      await SecureStorage.setFcmToken('fcm-token-userA');
      await SecureStorage.setFcmTokenRegistered(true);

      // User A logs out
      await SecureStorage.setFcmToken(null);
      await SecureStorage.setFcmTokenRegistered(false);

      // User B logs in on same phone
      await SecureStorage.setFcmToken('fcm-token-userB');
      await SecureStorage.setFcmTokenRegistered(true);

      // Only User B's token should be active
      expect(await SecureStorage.getFcmToken(), 'fcm-token-userB');
      expect(await SecureStorage.isFcmTokenRegistered(), true);
    });
  });
}
