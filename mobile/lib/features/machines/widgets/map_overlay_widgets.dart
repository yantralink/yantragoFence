import 'package:flutter/material.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Shared overlay widgets for the machine location map screen —
/// status chip, round control buttons, and the zoom pill.

/// Top-left status pill — colored dot + status text (e.g. Moving).
class MapStatusChip extends StatelessWidget {
  final String label;
  final Color dotColor;

  const MapStatusChip({
    super.key,
    required this.label,
    required this.dotColor,
  });

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    return _OverlaySurface(
      padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md, vertical: AppSpacing.sm),
      borderRadius: BorderRadius.circular(AppSpacing.xl),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Container(
            width: 9,
            height: 9,
            decoration:
                BoxDecoration(color: dotColor, shape: BoxShape.circle),
          ),
          const SizedBox(width: AppSpacing.sm),
          Text(
            label,
            style: text.labelLarge?.copyWith(fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }
}

/// White circular map control button (control rails).
class MapControlButton extends StatelessWidget {
  final Widget child;
  final String tooltip;
  final VoidCallback onPressed;
  final Color? backgroundColor;

  const MapControlButton({
    super.key,
    required this.child,
    required this.tooltip,
    required this.onPressed,
    this.backgroundColor,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return _OverlaySurface(
      padding: EdgeInsets.zero,
      borderRadius: BorderRadius.circular(AppSpacing.xxl),
      color: backgroundColor ?? colors.surface,
      child: IconButton(
        onPressed: onPressed,
        tooltip: tooltip,
        icon: child,
        iconSize: 22,
      ),
    );
  }
}

/// Vertical +/− zoom pill (bottom-right of the map).
class MapZoomPill extends StatelessWidget {
  final VoidCallback onZoomIn;
  final VoidCallback onZoomOut;

  const MapZoomPill({
    super.key,
    required this.onZoomIn,
    required this.onZoomOut,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return _OverlaySurface(
      padding: EdgeInsets.zero,
      borderRadius: BorderRadius.circular(AppSpacing.xl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          IconButton(
            onPressed: onZoomIn,
            tooltip: context.l10n.mapZoomIn,
            icon: const Icon(Icons.add),
            visualDensity: VisualDensity.compact,
          ),
          Container(
            width: 28,
            height: 1,
            color: colors.outlineVariant,
          ),
          IconButton(
            onPressed: onZoomOut,
            tooltip: context.l10n.mapZoomOut,
            icon: const Icon(Icons.remove),
            visualDensity: VisualDensity.compact,
          ),
        ],
      ),
    );
  }
}

/// Shared white-card surface for all map overlays.
class _OverlaySurface extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final BorderRadius borderRadius;
  final Color? color;

  const _OverlaySurface({
    required this.child,
    required this.padding,
    required this.borderRadius,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    // Material (not Container) so IconButton ink splashes render inside
    // the surface and clips to its rounded corners.
    return Material(
      color: color ?? colors.surface,
      elevation: 3,
      shadowColor: colors.shadow.withValues(alpha: 0.3),
      surfaceTintColor: Colors.transparent,
      borderRadius: borderRadius,
      clipBehavior: Clip.antiAlias,
      child: Padding(padding: padding, child: child),
    );
  }
}
