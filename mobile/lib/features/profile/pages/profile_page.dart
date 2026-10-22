import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/services/account_service.dart';
import 'package:yantrago/core/utils/link_launcher.dart';
import 'package:yantrago/core/widgets/app_action_button.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/profile/providers/profile_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Profile page — identity header plus a settings menu.
///
/// Each setting opens its own screen (drill-down via context.push):
/// Account Details, Notification, Language Settings, Theft Protection
/// Settings, and Command History.
class ProfilePage extends ConsumerStatefulWidget {
  const ProfilePage({super.key});

  @override
  ConsumerState<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends ConsumerState<ProfilePage> {
  @override
  Widget build(BuildContext context) {
    final user = ref.watch(profileProvider);
    final l10n = context.l10n;

    if (user == null) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.profileTitle)),
        body: AppStatePanel.empty(
          title: l10n.notSignedIn,
          message: l10n.pleaseLogIn,
        ),
      );
    }

    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final String initials = user.fullName.isNotEmpty
        ? user.fullName[0].toUpperCase()
        : '?';

    return Scaffold(
      appBar: AppBar(title: Text(l10n.profileTitle)),
      body: AppPageBody(
        scrollable: true,
        safeArea: false,
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            // Identity header card
            AppSurfaceCard(
              child: Column(
                children: <Widget>[
                  CircleAvatar(
                    radius: 44,
                    backgroundColor: colors.primaryContainer,
                    child: Text(
                      initials,
                      style: text.headlineMedium?.copyWith(
                        color: colors.onPrimaryContainer,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    user.fullName,
                    textAlign: TextAlign.center,
                    style: text.titleLarge,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    user.email,
                    textAlign: TextAlign.center,
                    style: text.bodyMedium?.copyWith(
                      color: colors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Settings menu
            AppSectionHeader(title: l10n.settingsSection),
            AppSurfaceCard(
              child: Column(
                children: <Widget>[
                  _SettingsTile(
                    icon: Icons.person_outline,
                    label: l10n.accountDetails,
                    onTap: () => context.push('/app/profile/account'),
                  ),
                  _SettingsDivider(),
                  _SettingsTile(
                    icon: Icons.notifications_outlined,
                    label: l10n.notificationTitle,
                    onTap: () => context.push('/app/notifications/preferences'),
                  ),
                  _SettingsDivider(),
                  _SettingsTile(
                    icon: Icons.language,
                    label: l10n.languageSettings,
                    onTap: () => context.push('/app/profile/language'),
                  ),
                  _SettingsDivider(),
                  _SettingsTile(
                    icon: Icons.shield_outlined,
                    label: l10n.theftProtectionSettings,
                    onTap: () => context.push('/app/profile/theft-protection'),
                  ),
                  _SettingsDivider(),
                  _SettingsTile(
                    icon: Icons.history,
                    label: l10n.commandHistory,
                    onTap: () => context.push('/app/profile/commands'),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Support, privacy policy & deletion request — Play requires
            // the policy and an erasure-request path to be discoverable
            // from the app.
            AppSectionHeader(title: l10n.supportPrivacySection),
            const _SupportPrivacyCard(),
            const SizedBox(height: 24),

            // Logout
            AppActionButton(
              label: l10n.logOut,
              style: AppActionButtonStyle.destructive,
              icon: Icons.logout,
              onPressed: () => ref.read(authStateProvider.notifier).logout(),
            ),
            const SizedBox(height: 12),

            // App version + build number
            const _AppVersionLabel(),
          ],
        ),
      ),
    );
  }
}

/// A tappable settings menu row: icon, label, chevron.
class _SettingsTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _SettingsTile({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 4),
        child: Row(
          children: <Widget>[
            Icon(icon, color: colors.primary, size: 24),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                label,
                style: text.bodyLarge?.copyWith(
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
            Icon(Icons.chevron_right, color: colors.onSurfaceVariant),
          ],
        ),
      ),
    );
  }
}

/// Support & privacy rows: contact support, open the privacy policy, and
/// submit an account-deletion request to the backend.
class _SupportPrivacyCard extends ConsumerStatefulWidget {
  const _SupportPrivacyCard();

  @override
  ConsumerState<_SupportPrivacyCard> createState() =>
      _SupportPrivacyCardState();
}

class _SupportPrivacyCardState extends ConsumerState<_SupportPrivacyCard> {
  bool _deletionBusy = false;

  void _snack(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), behavior: SnackBarBehavior.floating),
    );
  }

  Future<void> _contactSupport() async {
    if (!await launchEmail(AppConfig.supportEmail) && mounted) {
      _snack(context.l10n.couldNotOpenLink);
    }
  }

  Future<void> _openPrivacyPolicy() async {
    if (!await launchExternalUrl(AppConfig.privacyPolicyUrl) && mounted) {
      _snack(context.l10n.couldNotOpenLink);
    }
  }

  Future<void> _requestDeletion() async {
    if (_deletionBusy) return;
    setState(() => _deletionBusy = true);
    final ok =
        await ref.read(accountServiceProvider).requestAccountDeletion();
    if (!mounted) return;
    setState(() => _deletionBusy = false);
    final l10n = context.l10n;
    _snack(ok
        ? l10n.deletionRequestSent
        : l10n.deletionRequestFailed(AppConfig.privacyEmail));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;

    return AppSurfaceCard(
      child: Column(
        children: <Widget>[
          _SettingsTile(
            icon: Icons.support_agent,
            label: '${l10n.contactSupport} — ${AppConfig.supportEmail}',
            onTap: _contactSupport,
          ),
          _SettingsDivider(),
          _SettingsTile(
            icon: Icons.privacy_tip_outlined,
            label: l10n.privacyPolicy,
            onTap: _openPrivacyPolicy,
          ),
          _SettingsDivider(),
          _SettingsTile(
            icon: Icons.delete_outline,
            label: l10n.requestAccountDeletion,
            onTap: _requestDeletion,
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(4, 0, 4, 8),
            child: Text(
              l10n.deletionPolicyNote,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
          ),
          if (_deletionBusy)
            const Padding(
              padding: EdgeInsets.only(bottom: 8),
              child: SizedBox(
                height: 20,
                width: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
        ],
      ),
    );
  }
}

/// Thin separator between settings rows.
class _SettingsDivider extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Divider(
      height: 1,
      thickness: 1,
      color: Theme.of(context).dividerColor.withValues(alpha: 0.3),
    );
  }
}

/// Small app version + build number shown under the logout button, e.g.
/// "v1.0.0 (59)". Build number matches the CI release tag because the APK
/// workflow passes --build-number=${{ github.run_number }}.
class _AppVersionLabel extends StatefulWidget {
  const _AppVersionLabel();

  @override
  State<_AppVersionLabel> createState() => _AppVersionLabelState();
}

class _AppVersionLabelState extends State<_AppVersionLabel> {
  String? _label;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final info = await PackageInfo.fromPlatform();
      if (!mounted) return;
      setState(() => _label = 'v${info.version} (${info.buildNumber})');
    } catch (_) {
      // Platform info unavailable (tests, unsupported platforms) — stay hidden.
    }
  }

  @override
  Widget build(BuildContext context) {
    final label = _label;
    if (label == null) return const SizedBox.shrink();
    return Text(
      label,
      textAlign: TextAlign.center,
      style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
    );
  }
}
