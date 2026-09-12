import 'package:flutter/material.dart';

import '../theme/app_semantic_colors.dart';
import '../theme/app_spacing.dart';

/// Shared action button — primary, secondary, destructive, and the
/// domain-specific ON/OFF machine actions, with busy and disabled states.
///
/// Presentation-only. ON/OFF colors describe the action being requested,
/// not proof of the resulting machine state (per AGENTS.md rule 5: command
/// success requires acknowledgement).
///
/// The button preserves Material ink, focus, accessibility semantics,
/// disabled behavior, and the [AppSpacing.buttonMinHeight] minimum height.
class AppActionButton extends StatelessWidget {
  final String label;
  final AppActionButtonStyle style;
  final IconData? icon;
  final VoidCallback? onPressed;
  final bool busy;

  const AppActionButton({
    super.key,
    required this.label,
    required this.style,
    this.icon,
    this.onPressed,
    this.busy = false,
  });

  @override
  Widget build(BuildContext context) {
    final bool disabled = onPressed == null || busy;
    final Widget content = _buildContent(context);

    switch (style) {
      case AppActionButtonStyle.primary:
        return FilledButton(
          onPressed: disabled ? null : onPressed,
          style: _filledStyle(context, _fillColor(context, StatusTone.info), _onColor(context, StatusTone.info)),
          child: content,
        );
      case AppActionButtonStyle.secondary:
        return OutlinedButton(
          onPressed: disabled ? null : onPressed,
          child: content,
        );
      case AppActionButtonStyle.destructive:
        return FilledButton(
          onPressed: disabled ? null : onPressed,
          style: _filledStyle(context, _fillColor(context, StatusTone.danger), _onColor(context, StatusTone.danger)),
          child: content,
        );
      case AppActionButtonStyle.on:
        return FilledButton(
          onPressed: disabled ? null : onPressed,
          style: _filledStyle(context, _fillColor(context, StatusTone.success), _onColor(context, StatusTone.success)),
          child: content,
        );
      case AppActionButtonStyle.off:
        return FilledButton(
          onPressed: disabled ? null : onPressed,
          style: _filledStyle(context, _fillColor(context, StatusTone.danger), _onColor(context, StatusTone.danger)),
          child: content,
        );
    }
  }

  Widget _buildContent(BuildContext context) {
    if (busy) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.center,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: _onColor(context, _toneFor(style)),
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          Flexible(
            child: Text(
              label,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      );
    }
    if (icon != null) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.center,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Icon(icon, size: 18),
          const SizedBox(width: AppSpacing.sm),
          Flexible(
            child: Text(
              label,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      );
    }
    return Text(label, overflow: TextOverflow.ellipsis);
  }

  ButtonStyle _filledStyle(BuildContext context, Color fill, Color onColor) {
    return FilledButton.styleFrom(
      backgroundColor: fill,
      foregroundColor: onColor,
      disabledBackgroundColor: fill.withValues(alpha: 0.4),
      disabledForegroundColor: onColor.withValues(alpha: 0.6),
      minimumSize: const Size.fromHeight(AppSpacing.buttonMinHeight),
    );
  }

  Color _fillColor(BuildContext context, StatusTone tone) {
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    switch (tone) {
      case StatusTone.info:
        return semantic?.primary ?? Theme.of(context).colorScheme.primary;
      case StatusTone.success:
        return semantic?.success ?? Theme.of(context).colorScheme.secondary;
      case StatusTone.danger:
        return semantic?.danger ?? Theme.of(context).colorScheme.error;
      default:
        return semantic?.primary ?? Theme.of(context).colorScheme.primary;
    }
  }

  Color _onColor(BuildContext context, StatusTone tone) {
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    switch (tone) {
      case StatusTone.info:
        return semantic?.onActionBlue ?? Colors.white;
      case StatusTone.success:
        return semantic?.onActionGreen ?? Colors.white;
      case StatusTone.danger:
        return semantic?.onActionRed ?? Colors.white;
      default:
        return semantic?.onActionBlue ?? Colors.white;
    }
  }

  StatusTone _toneFor(AppActionButtonStyle s) {
    switch (s) {
      case AppActionButtonStyle.primary:
        return StatusTone.info;
      case AppActionButtonStyle.destructive:
      case AppActionButtonStyle.off:
        return StatusTone.danger;
      case AppActionButtonStyle.on:
        return StatusTone.success;
      case AppActionButtonStyle.secondary:
        return StatusTone.info;
    }
  }
}

/// Action button visual style.
enum AppActionButtonStyle {
  /// General primary action (blue).
  primary,

  /// Outlined secondary action.
  secondary,

  /// Destructive action (red).
  destructive,

  /// Machine ON action (green). Color describes the request, not the result.
  on,

  /// Machine OFF action (red). Color describes the request, not the result.
  off,
}
