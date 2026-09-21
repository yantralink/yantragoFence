import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yantrago/app.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/features/push/firebase_background_handler.dart';

/// Entry point — initializes Firebase and runs the app.
///
/// Per notification plan Phase 5: Flutter Firebase initialization.
/// The background message handler must be registered before runApp()
/// to handle notifications when the app is terminated.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Firebase (best-effort — app works without it)
  try {
    await Firebase.initializeApp();
    // Register the background isolate message handler for terminated-app
    // push notifications (Phase 5).
    FirebaseMessaging.onBackgroundMessage(firebaseBackgroundMessageHandler);
  } catch (_) {
    // Firebase configuration is optional — app runs without push.
  }

  // Phase 3: preload device preferences BEFORE runApp so MaterialApp never
  // renders with an unresolved locale. Bounded — a hung platform plugin must
  // not block startup; on failure/timeout the app runs with the provider
  // unoverridden (null), which falls back to device-locale resolution and
  // reports language-save failures instead of crashing.
  SharedPreferences? prefs;
  try {
    prefs = await SharedPreferences.getInstance()
        .timeout(const Duration(seconds: 2));
  } catch (_) {
    prefs = null;
  }

  runApp(
    ProviderScope(
      overrides: <Override>[
        if (prefs != null)
          sharedPreferencesProvider.overrideWithValue(prefs),
      ],
      child: const YantraGoApp(),
    ),
  );
}
