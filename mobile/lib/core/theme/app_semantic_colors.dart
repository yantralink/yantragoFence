import 'package:flutter/material.dart';

import 'app_palette.dart';

/// Semantic status colors and domain action appearance.
///
/// Exposed to widgets via `Theme.of(context).extension<AppSemanticColors>()`.
/// Feature logic chooses a [StatusTone]; generic widgets must not decide
/// hardware thresholds. "Not failed" is never automatically "successful".
///
/// Foreground colors are accessibility-adjusted for the matching surface.
/// Soft backgrounds pair with the foreground of the same tone for badges,
/// icon containers, and tinted panels.
@immutable
class AppSemanticColors extends ThemeExtension<AppSemanticColors> {
  const AppSemanticColors({
    required this.primary,
    required this.primarySoft,
    required this.success,
    required this.successSoft,
    required this.warning,
    required this.warningSoft,
    required this.danger,
    required this.dangerSoft,
    required this.neutral,
    required this.neutralSoft,
    required this.onActionBlue,
    required this.onActionGreen,
    required this.onActionRed,
  });

  /// Builds the light-mode semantic colors.
  const AppSemanticColors.light()
      : primary = AppPalette.lightPrimary,
        primarySoft = AppPalette.lightPrimarySoft,
        success = AppPalette.lightSuccess,
        successSoft = AppPalette.lightSuccessSoft,
        warning = AppPalette.lightWarning,
        warningSoft = AppPalette.lightWarningSoft,
        danger = AppPalette.lightDanger,
        dangerSoft = AppPalette.lightDangerSoft,
        neutral = AppPalette.lightNeutral,
        neutralSoft = AppPalette.lightNeutralSoft,
        onActionBlue = Colors.white,
        onActionGreen = Colors.white,
        onActionRed = Colors.white;

  /// Builds the dark-mode semantic colors.
  const AppSemanticColors.dark()
      : primary = AppPalette.darkPrimary,
        primarySoft = AppPalette.darkPrimarySoft,
        success = AppPalette.darkSuccess,
        successSoft = AppPalette.darkSuccessSoft,
        warning = AppPalette.darkWarning,
        warningSoft = AppPalette.darkWarningSoft,
        danger = AppPalette.darkDanger,
        dangerSoft = AppPalette.darkDangerSoft,
        neutral = AppPalette.darkNeutral,
        neutralSoft = AppPalette.darkNeutralSoft,
        onActionBlue = AppPalette.darkCanvas,
        onActionGreen = AppPalette.darkCanvas,
        onActionRed = AppPalette.darkCanvas;

  /// General primary action / link color.
  final Color primary;

  /// Tinted background pairing with [primary].
  final Color primarySoft;

  /// Healthy / confirmed-positive state.
  final Color success;

  /// Tinted background pairing with [success].
  final Color successSoft;

  /// Caution / degraded-but-not-failed state.
  final Color warning;

  /// Tinted background pairing with [warning].
  final Color warningSoft;

  /// Error / destructive state and OFF action.
  final Color danger;

  /// Tinted background pairing with [danger].
  final Color dangerSoft;

  /// Unknown / offline / unavailable state.
  final Color neutral;

  /// Tinted background pairing with [neutral].
  final Color neutralSoft;

  /// Foreground for a filled blue action button.
  final Color onActionBlue;

  /// Foreground for a filled green ON action button.
  final Color onActionGreen;

  /// Foreground for a filled red OFF action button.
  final Color onActionRed;

  /// Resolves the color for a [StatusTone].
  Color tone(StatusTone t) {
    switch (t) {
      case StatusTone.neutral:
        return neutral;
      case StatusTone.info:
        return primary;
      case StatusTone.success:
        return success;
      case StatusTone.warning:
        return warning;
      case StatusTone.danger:
        return danger;
    }
  }

  /// Resolves the soft background for a [StatusTone].
  Color toneSoft(StatusTone t) {
    switch (t) {
      case StatusTone.neutral:
        return neutralSoft;
      case StatusTone.info:
        return primarySoft;
      case StatusTone.success:
        return successSoft;
      case StatusTone.warning:
        return warningSoft;
      case StatusTone.danger:
        return dangerSoft;
    }
  }

  @override
  AppSemanticColors copyWith({
    Color? primary,
    Color? primarySoft,
    Color? success,
    Color? successSoft,
    Color? warning,
    Color? warningSoft,
    Color? danger,
    Color? dangerSoft,
    Color? neutral,
    Color? neutralSoft,
    Color? onActionBlue,
    Color? onActionGreen,
    Color? onActionRed,
  }) {
    return AppSemanticColors(
      primary: primary ?? this.primary,
      primarySoft: primarySoft ?? this.primarySoft,
      success: success ?? this.success,
      successSoft: successSoft ?? this.successSoft,
      warning: warning ?? this.warning,
      warningSoft: warningSoft ?? this.warningSoft,
      danger: danger ?? this.danger,
      dangerSoft: dangerSoft ?? this.dangerSoft,
      neutral: neutral ?? this.neutral,
      neutralSoft: neutralSoft ?? this.neutralSoft,
      onActionBlue: onActionBlue ?? this.onActionBlue,
      onActionGreen: onActionGreen ?? this.onActionGreen,
      onActionRed: onActionRed ?? this.onActionRed,
    );
  }

  @override
  AppSemanticColors lerp(ThemeExtension<AppSemanticColors>? other, double t) {
    if (other is! AppSemanticColors) return this;
    return AppSemanticColors(
      primary: Color.lerp(primary, other.primary, t)!,
      primarySoft: Color.lerp(primarySoft, other.primarySoft, t)!,
      success: Color.lerp(success, other.success, t)!,
      successSoft: Color.lerp(successSoft, other.successSoft, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      warningSoft: Color.lerp(warningSoft, other.warningSoft, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
      dangerSoft: Color.lerp(dangerSoft, other.dangerSoft, t)!,
      neutral: Color.lerp(neutral, other.neutral, t)!,
      neutralSoft: Color.lerp(neutralSoft, other.neutralSoft, t)!,
      onActionBlue: Color.lerp(onActionBlue, other.onActionBlue, t)!,
      onActionGreen: Color.lerp(onActionGreen, other.onActionGreen, t)!,
      onActionRed: Color.lerp(onActionRed, other.onActionRed, t)!,
    );
  }
}

/// Presentation tones chosen by feature logic.
///
/// A generic widget receives a tone and resolves appearance through
/// [AppSemanticColors]. Domain thresholds stay in feature code.
enum StatusTone { neutral, info, success, warning, danger }
