import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/widgets/app_action_button.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/profile/providers/profile_provider.dart';

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

    if (user == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Profile')),
        body: AppStatePanel.empty(
          title: 'Not signed in',
          message: 'Please log in to view your profile.',
        ),
      );
    }

    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final String initials = user.fullName.isNotEmpty
        ? user.fullName[0].toUpperCase()
        : '?';

    return Scaffold(
      appBar: AppBar(title: const Text('Profile')),
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
            const AppSectionHeader(title: 'Settings'),
            AppSurfaceCard(
              child: Column(
                children: <Widget>[
                  _SettingsTile(
                    icon: Icons.person_outline,
                    label: 'Account Details',
                    onTap: () => context.push('/app/profile/account'),
                  ),
                  _SettingsDivider(),
                  _SettingsTile(
                    icon: Icons.notifications_outlined,
                    label: 'Notification',
                    onTap: () => context.push('/app/notifications/preferences'),
                  ),
                  _SettingsDivider(),
                  _SettingsTile(
                    icon: Icons.language,
                    label: 'Language Settings',
                    onTap: () => context.push('/app/profile/language'),
                  ),
                  _SettingsDivider(),
                  _SettingsTile(
                    icon: Icons.shield_outlined,
                    label: 'Theft Protection Settings',
                    onTap: () => context.push('/app/profile/theft-protection'),
                  ),
                  _SettingsDivider(),
                  _SettingsTile(
                    icon: Icons.history,
                    label: 'Command History',
                    onTap: () => context.push('/app/profile/commands'),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Logout
            AppActionButton(
              label: 'Log out',
              style: AppActionButtonStyle.destructive,
              icon: Icons.logout,
              onPressed: () => ref.read(authStateProvider.notifier).logout(),
            ),
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
