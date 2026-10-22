import 'package:url_launcher/url_launcher.dart';

/// Safe external-link helpers used by login/profile privacy links.
///
/// Both functions return false instead of throwing when the device has no
/// handler for the link (no browser, no mail app, emulator), so callers can
/// show a localized fallback message.
Future<bool> launchExternalUrl(String url) async {
  try {
    return await launchUrl(
      Uri.parse(url),
      mode: LaunchMode.externalApplication,
    );
  } catch (_) {
    return false;
  }
}

/// Opens the user's email composer addressed to [email].
Future<bool> launchEmail(String email) async {
  try {
    return await launchUrl(Uri.parse('mailto:$email'));
  } catch (_) {
    return false;
  }
}
