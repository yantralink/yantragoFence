import 'package:flutter/material.dart';

import '../theme/app_radii.dart';
import '../theme/app_semantic_colors.dart';
import '../theme/app_spacing.dart';

/// Shared status badge — icon/dot plus status text in a semantic tone.
///
/// Presentation-only. Feature logic chooses a [StatusTone]; this widget
/// resolves the foreground and soft background through [AppSemanticColors].
/// Color is never the sole indicator: an optional [icon] and the text label
/// always accompany it.
class AppStatusBadge extends StatelessWidget {
  final String label;
  final StatusTone tone;
  final IconData? icon;
  final bool dot;

  const AppStatusBadge({
    super.key,
    required this.label,
    required this.tone,
    this.icon,
    this.dot = false,
  });

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    final TextTheme text = Theme.of(context).textTheme;
    final Color foreground = semantic?.tone(tone) ?? Theme.of(context).colorScheme.primary;
    final Color background = semantic?.toneSoft(tone) ?? foreground.withValues(alpha: 0.12);

    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.sm,
        vertical: AppSpacing.xs,
      ),
      decoration: BoxDecoration(
        color: background,
        borderRadius: AppRadii.chipRadius,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          if (dot)
            Padding(
              padding: const EdgeInsets.only(right: AppSpacing.xs),
              child: _Dot(color: foreground),
            )
          else if (icon != null) ...<Widget>[
            Icon(icon, size: 14, color: foreground),
            const SizedBox(width: AppSpacing.xs),
          ],
          Flexible(
            child: Text(
              label,
              overflow: TextOverflow.ellipsis,
              style: text.labelMedium?.copyWith(color: foreground),
            ),
          ),
        ],
      ),
    );
  }
}

class _Dot extends StatelessWidget {
  final Color color;
  const _Dot({required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 8,
      height: 8,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
      ),
    );
  }
}
