import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/widgets/app_action_button.dart';
import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/profile/providers/profile_provider.dart';

/// Profile page — shows user profile and logout button.
class ProfilePage extends ConsumerWidget {
  const ProfilePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
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

            // Account details
            const AppSectionHeader(title: 'Account'),
            AppSurfaceCard(
              child: Column(
                children: <Widget>[
                  AppMetadataRow(label: 'Role', value: user.role),
                  AppMetadataRow(
                    label: 'Organization',
                    value: user.organizationName ?? user.organizationId ?? 'N/A',
                  ),
                  if (user.phoneNumber != null)
                    AppMetadataRow(label: 'Phone', value: user.phoneNumber),
                  AppMetadataRow(
                    label: 'Status',
                    value: user.active ? 'Active' : 'Inactive',
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Notification preferences
            AppSurfaceCard(
              onTap: () => context.push('/app/notifications/preferences'),
              child: Row(
                children: <Widget>[
                  Icon(Icons.notifications_outlined, color: colors.primary),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Notification Preferences',
                      style: text.bodyLarge?.copyWith(
                        color: colors.primary,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                  Icon(Icons.chevron_right, color: colors.onSurfaceVariant),
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
