// Theme foundation tests for the YantraGO design system.
//
// Verifies token values, semantic-theme availability, and selected
// foreground/background contrast pairs from docs/YANTRAGO_Mobile_UI.md
// section 3.2. Contrast is computed from the sRGB relative luminance.

import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/core/theme/app_palette.dart';
import 'package:yantrago/core/theme/app_radii.dart';
import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/theme/app_spacing.dart';

void main() {
  group('AppPalette tokens', () {
    test('light palette matches the SVG reference values', () {
      expect(AppPalette.lightCanvas, const Color(0xFFF8FAFC));
      expect(AppPalette.lightSurface, const Color(0xFFFFFFFF));
      expect(AppPalette.lightTextPrimary, const Color(0xFF0F172A));
      expect(AppPalette.lightTextSecondary, const Color(0xFF64748B));
      expect(AppPalette.lightSubtleBorder, const Color(0xFFE2E8F0));
      expect(AppPalette.lightDivider, const Color(0xFFF1F5F9));
      expect(AppPalette.lightPrimary, const Color(0xFF2563EB));
      expect(AppPalette.lightSuccess, const Color(0xFF15803D));
      expect(AppPalette.lightWarning, const Color(0xFFB45309));
      expect(AppPalette.lightDanger, const Color(0xFFDC2626));
    });

    test('dark palette is an intentional counterpart, not a mechanical invert', () {
      expect(AppPalette.darkCanvas, const Color(0xFF0F172A));
      expect(AppPalette.darkSurface, const Color(0xFF1E293B));
      expect(AppPalette.darkTextPrimary, const Color(0xFFF8FAFC));
      expect(AppPalette.darkSubtleBorder, const Color(0xFF475569));
    });
  });

  group('AppSpacing tokens', () {
    test('scale uses the documented values', () {
      expect(AppSpacing.xs, 4);
      expect(AppSpacing.sm, 8);
      expect(AppSpacing.md, 12);
      expect(AppSpacing.lg, 16);
      expect(AppSpacing.xl, 24);
      expect(AppSpacing.xxl, 32);
      expect(AppSpacing.pagePadding, AppSpacing.lg);
      expect(AppSpacing.buttonMinHeight, 50);
      expect(AppSpacing.minTouchTarget, 48);
    });
  });

  group('AppRadii tokens', () {
    test('radii match the documented values', () {
      expect(AppRadii.card, 16);
      expect(AppRadii.metric, 14);
      expect(AppRadii.button, 12);
      expect(AppRadii.chip, 8);
    });
  });

  group('AppTheme', () {
    test('light and dark themes are distinct entries with correct brightness', () {
      expect(AppTheme.lightTheme.brightness, Brightness.light);
      expect(AppTheme.darkTheme.brightness, Brightness.dark);
      expect(AppTheme.lightTheme, isNot(same(AppTheme.darkTheme)));
    });

    test('scaffold background uses the SVG canvas color in light mode', () {
      expect(AppTheme.lightTheme.scaffoldBackgroundColor, AppPalette.lightCanvas);
      expect(AppTheme.darkTheme.scaffoldBackgroundColor, AppPalette.darkCanvas);
    });

    test('app bars are flat, canvas-colored, and left-aligned', () {
      final AppBarThemeData light = AppTheme.lightTheme.appBarTheme;
      final AppBarThemeData dark = AppTheme.darkTheme.appBarTheme;
      expect(light.backgroundColor, AppPalette.lightCanvas);
      expect(light.elevation, 0);
      expect(light.centerTitle, false);
      expect(dark.backgroundColor, AppPalette.darkCanvas);
      expect(dark.elevation, 0);
    });

    test('cards are bordered with the documented radius and no elevation', () {
      final CardThemeData card = AppTheme.lightTheme.cardTheme;
      expect(card.elevation, 0);
      expect(card.margin, EdgeInsets.zero);
      final shape = card.shape;
      expect(shape, isA<RoundedRectangleBorder>());
      expect((shape as RoundedRectangleBorder).borderRadius, AppRadii.cardRadius);
    });

    test('AppSemanticColors extension is available in both themes', () {
      final AppSemanticColors light =
          AppTheme.lightTheme.extension<AppSemanticColors>()!;
      final AppSemanticColors dark =
          AppTheme.darkTheme.extension<AppSemanticColors>()!;
      expect(light.primary, AppPalette.lightPrimary);
      expect(light.success, AppPalette.lightSuccess);
      expect(dark.primary, AppPalette.darkPrimary);
      expect(dark.success, AppPalette.darkSuccess);
    });

    test('StatusTone resolves to the matching semantic color', () {
      final AppSemanticColors s =
          AppTheme.lightTheme.extension<AppSemanticColors>()!;
      expect(s.tone(StatusTone.neutral), s.neutral);
      expect(s.tone(StatusTone.info), s.primary);
      expect(s.tone(StatusTone.success), s.success);
      expect(s.tone(StatusTone.warning), s.warning);
      expect(s.tone(StatusTone.danger), s.danger);
      expect(s.toneSoft(StatusTone.success), s.successSoft);
    });

    test('lerp interpolates between light and dark semantic colors', () {
      final AppSemanticColors light =
          AppTheme.lightTheme.extension<AppSemanticColors>()!;
      final AppSemanticColors dark =
          AppTheme.darkTheme.extension<AppSemanticColors>()!;
      final AppSemanticColors mid = light.lerp(dark, 0.5);
      expect(mid.primary, Color.lerp(light.primary, dark.primary, 0.5));
    });
  });

  group('Accessibility contrast (light mode on white surface)', () {
    // Target: at least 4.5:1 for normal text on white.
    const Color white = AppPalette.lightSurface;

    test('primary action blue meets 4.5:1 on white', () {
      expect(_contrast(AppPalette.lightPrimary, white), greaterThanOrEqualTo(4.5));
    });

    test('success green meets 4.5:1 on white', () {
      expect(_contrast(AppPalette.lightSuccess, white), greaterThanOrEqualTo(4.5));
    });

    test('warning amber meets 4.5:1 on white', () {
      expect(_contrast(AppPalette.lightWarning, white), greaterThanOrEqualTo(4.5));
    });

    test('danger red meets 4.5:1 on white', () {
      expect(_contrast(AppPalette.lightDanger, white), greaterThanOrEqualTo(4.5));
    });

    test('secondary text meets 4.5:1 on white', () {
      expect(_contrast(AppPalette.lightTextSecondary, white), greaterThanOrEqualTo(4.5));
    });

    test('muted decoration is not used as essential text on white', () {
      // Documented as unsuitable for small essential text.
      expect(_contrast(AppPalette.lightTextMuted, white), lessThan(4.5));
    });
  });
}

/// WCAG-style contrast ratio between two sRGB colors.
double _contrast(Color foreground, Color background) {
  final double l1 = _luminance(foreground);
  final double l2 = _luminance(background);
  final double lighter = l1 > l2 ? l1 : l2;
  final double darker = l1 > l2 ? l2 : l1;
  return (lighter + 0.05) / (darker + 0.05);
}

double _luminance(Color c) {
  double channel(double v) {
    final double s = v / 255;
    return s <= 0.03928 ? s / 12.92 : pow((s + 0.055) / 1.055, 2.4).toDouble();
  }

  return 0.2126 * channel(c.r * 255) +
      0.7152 * channel(c.g * 255) +
      0.0722 * channel(c.b * 255);
}
