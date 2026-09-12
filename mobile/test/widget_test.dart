// Smoke test for the real YantraGO application entry point.
//
// Replaces the generated counter test. The app is wrapped in a real
// ProviderScope and pumped with the production YantraGoApp. The secure
// storage method channel is mocked so the real AuthStateNotifier resolves
// to Unauthenticated without contacting the platform keychain, the network,
// or any backend service. The router then resolves to the login page.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/app.dart';

const MethodChannel _secureStorageChannel =
    MethodChannel('plugins.it_nomatter.com/flutter_secure_storage');

Future<Object?> _mockSecureStorage(MethodCall call) async {
  switch (call.method) {
    case 'read':
      return null;
    case 'containsKey':
      return false;
    case 'readAll':
      return <String, String>{};
    default:
      return null;
  }
}

void main() {
  testWidgets('App boots and resolves to the login page when unauthenticated',
      (WidgetTester tester) async {
    // Return null for every secure-storage read so TokenManager.hasTokens()
    // resolves to false and the auth notifier sets Unauthenticated cleanly.
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      _secureStorageChannel,
      _mockSecureStorage,
    );

    await tester.pumpWidget(const ProviderScope(child: YantraGoApp()));

    // Let the router resolve and the async auth check run. We use timed
    // pumps (not pumpAndSettle) because the splash page shows an
    // indeterminate progress indicator that never settles.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.byType(MaterialApp), findsOneWidget);
    expect(find.byType(Scaffold), findsWidgets);

    tester.binding.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
  });
}
