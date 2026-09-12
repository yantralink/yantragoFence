import 'package:flutter/material.dart';

import '../theme/app_spacing.dart';

/// Shared section header — uppercase title with an optional trailing action.
///
/// Presentation-only. Matches the SVG's uppercase section labels with letter
/// spacing. Long titles wrap; the optional [action] is kept on the trailing
/// side and remains reachable.
class AppSectionHeader extends StatelessWidget {
  final String title;
  final String? aside;
  final Widget? action;
  final bool compact;

  const AppSectionHeader({
    super.key,
    required this.title,
    this.aside,
    this.action,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;

    return Padding(
      padding: EdgeInsets.only(
        top: compact ? AppSpacing.sm : AppSpacing.xl,
        bottom: AppSpacing.sm,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          Expanded(
            child: Text(
              title.toUpperCase(),
              style: text.labelSmall?.copyWith(
                letterSpacing: 0.8,
                color: colors.onSurfaceVariant,
              ),
            ),
          ),
          if (aside != null) ...<Widget>[
            const SizedBox(width: AppSpacing.sm),
            Text(
              aside!,
              style: text.labelSmall?.copyWith(color: colors.onSurfaceVariant),
            ),
          ],
          if (action != null) ...<Widget>[
            const SizedBox(width: AppSpacing.sm),
            action!,
          ],
        ],
      ),
    );
  }
}
