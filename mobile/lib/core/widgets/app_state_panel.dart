import 'package:flutter/material.dart';

import '../theme/app_spacing.dart';
import 'app_action_button.dart';

/// Shared async-state panel — loading, error, and empty presentation.
///
/// Presentation-only. Use instead of leaving blank screens or showing raw
/// exception strings. The [AppStatePanel.error] variant shows a friendly
/// message and an optional retry action; raw exceptions are never displayed.
class AppStatePanel extends StatelessWidget {
  final Widget child;

  AppStatePanel.loading({super.key, String? message})
      : child = _Loading(message: message);

  AppStatePanel.error({
    super.key,
    required String message,
    VoidCallback? onRetry,
  }) : child = _Error(message: message, onRetry: onRetry);

  AppStatePanel.empty({
    super.key,
    required String title,
    String? message,
    IconData icon = Icons.inbox_outlined,
    VoidCallback? onRetry,
  }) : child = _Empty(
          title: title,
          message: message,
          icon: icon,
          onRetry: onRetry,
        );

  @override
  Widget build(BuildContext context) => child;
}

class _Loading extends StatelessWidget {
  final String? message;
  const _Loading({this.message});

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          const CircularProgressIndicator(),
          if (message != null) ...<Widget>[
            const SizedBox(height: AppSpacing.md),
            Text(
              message!,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
            ),
          ],
        ],
      ),
    );
  }
}

class _Error extends StatelessWidget {
  final String message;
  final VoidCallback? onRetry;
  const _Error({required this.message, this.onRetry});

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Icon(
              Icons.cloud_off_outlined,
              size: 48,
              color: colors.onSurfaceVariant,
            ),
            const SizedBox(height: AppSpacing.md),
            Text(
              'Something went wrong',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              message,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
            ),
            if (onRetry != null) ...<Widget>[
              const SizedBox(height: AppSpacing.lg),
              AppActionButton(
                label: 'Try again',
                style: AppActionButtonStyle.primary,
                icon: Icons.refresh,
                onPressed: onRetry,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  final String title;
  final String? message;
  final IconData icon;
  final VoidCallback? onRetry;
  const _Empty({
    required this.title,
    this.message,
    required this.icon,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Icon(icon, size: 48, color: colors.onSurfaceVariant),
            const SizedBox(height: AppSpacing.md),
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            if (message != null) ...<Widget>[
              const SizedBox(height: AppSpacing.xs),
              Text(
                message!,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: colors.onSurfaceVariant,
                    ),
              ),
            ],
            if (onRetry != null) ...<Widget>[
              const SizedBox(height: AppSpacing.lg),
              AppActionButton(
                label: 'Refresh',
                style: AppActionButtonStyle.secondary,
                icon: Icons.refresh,
                onPressed: onRetry,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
