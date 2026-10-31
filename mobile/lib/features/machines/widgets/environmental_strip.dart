import 'package:flutter/material.dart';
import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/theme/app_spacing.dart';

/// Environmental strip below the map — operating condition, weather,
/// and wind separated by thin dividers.
///
/// All values are injected by the caller. Until the backend weather
/// feature (docs/WEATHER_FEATURE_PLAN.md) lands, the map page passes
/// clearly-marked stub values — swap the source, not this widget.
class EnvironmentalStrip extends StatelessWidget {
  final String conditionLabel;
  final IconData conditionIcon;
  final String weatherLabel;
  final String windLabel;

  const EnvironmentalStrip({
    super.key,
    required this.conditionLabel,
    required this.conditionIcon,
    required this.weatherLabel,
    required this.windLabel,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();

    return Container(
      height: 44,
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(color: colors.outlineVariant),
        ),
      ),
      child: Row(
        children: <Widget>[
          Expanded(
            child: _StripItem(
              icon: conditionIcon,
              iconColor: semantic?.tone(StatusTone.success) ??
                  colors.primary,
              label: conditionLabel,
            ),
          ),
          _StripDivider(color: colors.outlineVariant),
          Expanded(
            child: _StripItem(
              icon: Icons.wb_sunny_outlined,
              iconColor: semantic?.tone(StatusTone.warning) ??
                  colors.tertiary,
              label: weatherLabel,
            ),
          ),
          _StripDivider(color: colors.outlineVariant),
          Expanded(
            child: _StripItem(
              icon: Icons.air,
              iconColor: colors.onSurfaceVariant,
              label: windLabel,
            ),
          ),
        ],
      ),
    );
  }
}

class _StripItem extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String label;

  const _StripItem({
    required this.icon,
    required this.iconColor,
    required this.label,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: <Widget>[
        Icon(icon, size: 16, color: iconColor),
        const SizedBox(width: AppSpacing.xs),
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurface,
                ),
          ),
        ),
      ],
    );
  }
}

class _StripDivider extends StatelessWidget {
  final Color color;

  const _StripDivider({required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(width: 1, height: 20, color: color);
  }
}
