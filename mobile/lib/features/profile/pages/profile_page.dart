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
import 'package:yantrago/features/profile/providers/customer_settings_provider.dart';
import 'package:yantrago/features/profile/providers/profile_provider.dart';

/// Profile page — shows user profile and logout button.
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

            // Language selection (Phase 8)
            const AppSectionHeader(title: 'Language'),
            AppSurfaceCard(
              child: Column(
                children: <Widget>[
                  _LanguageTile(
                    label: 'English',
    code: 'en',
    selected: user.preferredLocale == 'en',
    onTap: () => ref.read(authStateProvider.notifier).updatePreferredLocale('en'),
                  ),
                  _LanguageTile(
                    label: 'हिंदी (Hindi)',
    code: 'hi',
    selected: user.preferredLocale == 'hi',
    onTap: () => ref.read(authStateProvider.notifier).updatePreferredLocale('hi'),
                  ),
                  _LanguageTile(
                    label: 'मराठी (Marathi)',
    code: 'mr',
    selected: user.preferredLocale == 'mr',
    onTap: () => ref.read(authStateProvider.notifier).updatePreferredLocale('mr'),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                'Notifications will be delivered in your selected language.',
                style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
              ),
            ),
            const SizedBox(height: 24),

            // Theft Protection Defaults (Phase 11)
            const AppSectionHeader(title: 'Theft Protection Defaults'),
            _TheftProtectionDefaultsCard(),
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                'These defaults apply when you enable theft protection on a new machine. You can still adjust each machine individually.',
                style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
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

/// Language selection tile for the profile page.
class _LanguageTile extends StatelessWidget {
  final String label;
  final String code;
  final bool selected;
  final VoidCallback onTap;

  const _LanguageTile({
    required this.label,
    required this.code,
    required this.selected,
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
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 4),
        child: Row(
          children: <Widget>[
            Expanded(
              child: Text(label, style: text.bodyLarge),
            ),
            if (selected)
              Icon(Icons.check_circle, color: colors.primary, size: 22)
            else
              Icon(Icons.radio_button_unchecked,
                  color: colors.onSurfaceVariant, size: 22),
          ],
        ),
      ),
    );
  }
}

/// Theft Protection Defaults card — lets the customer set their preferred
/// geofence radius and speed threshold for new machines (Phase 11).
class _TheftProtectionDefaultsCard extends ConsumerStatefulWidget {
  @override
  ConsumerState<_TheftProtectionDefaultsCard> createState() =>
      _TheftProtectionDefaultsCardState();
}

class _TheftProtectionDefaultsCardState
    extends ConsumerState<_TheftProtectionDefaultsCard> {
  late double _radiusSlider;
  late double _speedSlider;
  bool _initialized = false;
  bool _saving = false;

  @override
  Widget build(BuildContext context) {
    final settingsAsync = ref.watch(customerSettingsNotifierProvider);

    return settingsAsync.when(
      loading: () => AppSurfaceCard(
        child: const Padding(
          padding: EdgeInsets.symmetric(vertical: 24),
          child: Center(child: CircularProgressIndicator()),
        ),
      ),
      error: (error, _) => AppSurfaceCard(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 16),
          child: Column(
            children: <Widget>[
              Icon(Icons.error_outline,
                  color: Theme.of(context).colorScheme.error, size: 32),
              const SizedBox(height: 8),
              Text('Could not load defaults',
                  style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref
                    .read(customerSettingsNotifierProvider.notifier)
                    .load(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
      data: (settings) {
        if (!_initialized) {
          _radiusSlider = settings.defaultGeofenceRadiusMeters.toDouble();
          _speedSlider = settings.defaultSpeedThresholdKmh.toDouble();
          _initialized = true;
        }

        final ColorScheme colors = Theme.of(context).colorScheme;
        final TextTheme text = Theme.of(context).textTheme;

        return AppSurfaceCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              // Geofence radius row
              Row(
                children: <Widget>[
                  Icon(Icons.location_searching, color: colors.primary, size: 22),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text('Geofence Radius', style: text.bodyLarge),
                  ),
                  Text(
                    '${_radiusSlider.round()} m',
                    style: text.bodyLarge?.copyWith(
                      fontWeight: FontWeight.w600,
                      color: colors.primary,
                    ),
                  ),
                ],
              ),
              Slider(
                value: _radiusSlider,
                min: 50,
                max: 1000,
                divisions: 19, // 50m steps
                label: '${_radiusSlider.round()} m',
                onChanged: (v) => setState(() => _radiusSlider = v),
              ),
              const SizedBox(height: 8),

              // Speed threshold row
              Row(
                children: <Widget>[
                  Icon(Icons.speed, color: colors.primary, size: 22),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text('Speed Alert', style: text.bodyLarge),
                  ),
                  Text(
                    '${_speedSlider.round()} km/h',
                    style: text.bodyLarge?.copyWith(
                      fontWeight: FontWeight.w600,
                      color: colors.primary,
                    ),
                  ),
                ],
              ),
              Slider(
                value: _speedSlider,
                min: 1,
                max: 30,
                divisions: 29, // 1 km/h steps
                label: '${_speedSlider.round()} km/h',
                onChanged: (v) => setState(() => _speedSlider = v),
              ),
              const SizedBox(height: 4),
              Text(
                'Alert when machine moves faster than ${_speedSlider.round()} km/h',
                style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
              ),
              const SizedBox(height: 16),

              // Save button
              AppActionButton(
                label: 'Save Defaults',
                style: AppActionButtonStyle.primary,
                icon: Icons.save_outlined,
                busy: _saving,
                onPressed: _saving
                    ? null
                    : () async {
                        setState(() => _saving = true);
                        await ref
                            .read(customerSettingsNotifierProvider.notifier)
                            .update(
                              _radiusSlider.round(),
                              _speedSlider.round(),
                            );
                        if (mounted) {
                          setState(() => _saving = false);
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text('Defaults saved'),
                              duration: Duration(seconds: 2),
                            ),
                          );
                        }
                      },
              ),
            ],
          ),
        );
      },
    );
  }
}
