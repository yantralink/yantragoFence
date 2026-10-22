import 'package:flutter/material.dart';

import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/utils/link_launcher.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Tappable "Privacy Policy" link — opens the public policy page in the
/// device browser. Shown before login (Play requires the policy to be
/// reachable pre-authentication) and from the profile page.
class PrivacyPolicyLink extends StatelessWidget {
  const PrivacyPolicyLink({super.key});

  static Future<bool> open() =>
      launchExternalUrl(AppConfig.privacyPolicyUrl);

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final l10n = context.l10n;

    return InkWell(
      onTap: () async {
        final opened = await open();
        if (!opened && context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(l10n.couldNotOpenLink),
              behavior: SnackBarBehavior.floating,
            ),
          );
        }
      },
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
        child: Text(
          l10n.privacyPolicy,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: colors.primary,
                decoration: TextDecoration.underline,
              ),
        ),
      ),
    );
  }
}
