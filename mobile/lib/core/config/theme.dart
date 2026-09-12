import 'package:flutter/material.dart';

import '../theme/app_palette.dart';
import '../theme/app_radii.dart';
import '../theme/app_semantic_colors.dart';
import '../theme/app_spacing.dart';

/// App theme — colors, typography, and component styles for YantraGO.
///
/// Evolved from docs/UI.svg and docs/mobile-ui-mockups. One centralized
/// theme entry point for both brightness modes. Feature widgets should
/// read colors from `Theme.of(context)` and `AppSemanticColors` rather
/// than referencing [AppTheme] constants directly.
class AppTheme {
  AppTheme._();

  // ---- Legacy brand/status constants (kept for compatibility) ---------
  // These are no longer the source of truth for component styling.
  // Prefer the Material ColorScheme and [AppSemanticColors].
  static const Color primary = AppPalette.lightPrimary;
  static const Color primaryDark = Color(0xFF1D4ED8);
  static const Color secondary = AppPalette.lightSuccess;
  static const Color accent = AppPalette.lightWarning;

  static const Color online = AppPalette.lightSuccess;
  static const Color offline = AppPalette.lightNeutral;
  static const Color warning = AppPalette.lightWarning;
  static const Color error = AppPalette.lightDanger;
  static const Color fault = AppPalette.lightDanger;

  static const Color background = AppPalette.lightCanvas;
  static const Color surface = AppPalette.lightSurface;
  static const Color cardBackground = AppPalette.lightSurface;

  static const Color textPrimary = AppPalette.lightTextPrimary;
  static const Color textSecondary = AppPalette.lightTextSecondary;

  static ThemeData get lightTheme => _buildTheme(Brightness.light);
  static ThemeData get darkTheme => _buildTheme(Brightness.dark);

  static ThemeData _buildTheme(Brightness brightness) {
    final bool isLight = brightness == Brightness.light;
    final ColorScheme colorScheme = isLight
        ? _lightColorScheme
        : _darkColorScheme;

    final AppSemanticColors semantic = isLight
        ? const AppSemanticColors.light()
        : const AppSemanticColors.dark();

    final Color canvas = isLight
        ? AppPalette.lightCanvas
        : AppPalette.darkCanvas;
    final Color surfaceColor = isLight
        ? AppPalette.lightSurface
        : AppPalette.darkSurface;
    final Color border = isLight
        ? AppPalette.lightSubtleBorder
        : AppPalette.darkSubtleBorder;
    final Color divider = isLight
        ? AppPalette.lightDivider
        : AppPalette.darkDivider;
    final Color textPrimary = isLight
        ? AppPalette.lightTextPrimary
        : AppPalette.darkTextPrimary;
    final Color textBody = isLight
        ? AppPalette.lightTextBody
        : AppPalette.darkTextBody;
    final Color textSecondary = isLight
        ? AppPalette.lightTextSecondary
        : AppPalette.darkTextSecondary;

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: canvas,
      canvasColor: canvas,
      dividerColor: divider,
      extensions: <ThemeExtension<dynamic>>[semantic],
      textTheme: _buildTextTheme(colorScheme, textPrimary, textBody, textSecondary),
      appBarTheme: AppBarTheme(
        backgroundColor: canvas,
        surfaceTintColor: Colors.transparent,
        foregroundColor: textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleSpacing: AppSpacing.pagePadding,
      ),
      cardTheme: CardThemeData(
        color: surfaceColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: AppRadii.cardRadius,
          side: BorderSide(color: border),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: semantic.primary,
          foregroundColor: semantic.onActionBlue,
          minimumSize: const Size.fromHeight(AppSpacing.buttonMinHeight),
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
          shape: const RoundedRectangleBorder(borderRadius: AppRadii.buttonRadius),
          elevation: 0,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(AppSpacing.buttonMinHeight),
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
          shape: const RoundedRectangleBorder(borderRadius: AppRadii.buttonRadius),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: semantic.primary,
          minimumSize: const Size.fromHeight(AppSpacing.buttonMinHeight),
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
          shape: const RoundedRectangleBorder(borderRadius: AppRadii.buttonRadius),
          side: BorderSide(color: border),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: semantic.primary,
          shape: const RoundedRectangleBorder(borderRadius: AppRadii.buttonRadius),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceColor,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.lg,
          vertical: AppSpacing.md,
        ),
        border: OutlineInputBorder(
          borderRadius: AppRadii.buttonRadius,
          borderSide: BorderSide(color: border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: AppRadii.buttonRadius,
          borderSide: BorderSide(color: border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: AppRadii.buttonRadius,
          borderSide: BorderSide(color: semantic.primary, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: AppRadii.buttonRadius,
          borderSide: BorderSide(color: semantic.danger),
        ),
        labelStyle: TextStyle(color: textSecondary),
        hintStyle: TextStyle(color: textSecondary),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: surfaceColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        height: 72,
        indicatorColor: semantic.primarySoft,
        labelTextStyle: WidgetStatePropertyAll<TextStyle>(
          TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: textPrimary),
        ),
        iconTheme: WidgetStateProperty.resolveWith<IconThemeData>((Set<WidgetState> states) {
          final bool selected = states.contains(WidgetState.selected);
          return IconThemeData(
            color: selected ? semantic.primary : textSecondary,
            size: 24,
          );
        }),
      ),
      listTileTheme: ListTileThemeData(
        contentPadding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.lg,
          vertical: AppSpacing.xs,
        ),
        shape: const RoundedRectangleBorder(borderRadius: AppRadii.cardRadius),
        titleTextStyle: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: textPrimary),
        subtitleTextStyle: TextStyle(fontSize: 14, color: textSecondary),
      ),
      dividerTheme: DividerThemeData(
        color: divider,
        thickness: 1,
        space: 1,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: semantic.neutralSoft,
        selectedColor: semantic.primarySoft,
        labelStyle: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: textPrimary),
        side: BorderSide.none,
        shape: const RoundedRectangleBorder(borderRadius: AppRadii.chipRadius),
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm, vertical: AppSpacing.xs),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: textPrimary,
        contentTextStyle: TextStyle(color: canvas, fontSize: 14),
        behavior: SnackBarBehavior.floating,
        shape: const RoundedRectangleBorder(borderRadius: AppRadii.buttonRadius),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: surfaceColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: AppRadii.cardRadius,
          side: BorderSide(color: border),
        ),
        titleTextStyle: TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.w600,
          color: textPrimary,
        ),
        contentTextStyle: TextStyle(fontSize: 14, color: textBody),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: surfaceColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadii.card)),
        ),
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: semantic.primary,
        linearTrackColor: semantic.primarySoft,
      ),
    );
  }

  static TextTheme _buildTextTheme(
    ColorScheme scheme,
    Color textPrimary,
    Color textBody,
    Color textSecondary,
  ) {
    return TextTheme(
      displayLarge: TextStyle(fontSize: 28, fontWeight: FontWeight.w700, color: textPrimary),
      displayMedium: TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: textPrimary),
      headlineSmall: TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: textPrimary),
      titleLarge: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: textPrimary),
      titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: textPrimary),
      titleSmall: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: textPrimary),
      bodyLarge: TextStyle(fontSize: 16, fontWeight: FontWeight.w400, color: textBody),
      bodyMedium: TextStyle(fontSize: 14, fontWeight: FontWeight.w400, color: textBody),
      bodySmall: TextStyle(fontSize: 12, fontWeight: FontWeight.w400, color: textSecondary),
      labelLarge: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: textPrimary),
      labelMedium: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: textSecondary),
      labelSmall: TextStyle(
        fontSize: 11,
        fontWeight: FontWeight.w600,
        color: textSecondary,
        letterSpacing: 0.8,
      ),
    );
  }

  // ---- Color schemes -------------------------------------------------
  static const ColorScheme _lightColorScheme = ColorScheme(
    brightness: Brightness.light,
    primary: AppPalette.lightPrimary,
    onPrimary: Colors.white,
    primaryContainer: AppPalette.lightPrimarySoft,
    onPrimaryContainer: AppPalette.lightPrimary,
    secondary: AppPalette.lightSuccess,
    onSecondary: Colors.white,
    secondaryContainer: AppPalette.lightSuccessSoft,
    onSecondaryContainer: AppPalette.lightSuccess,
    tertiary: AppPalette.lightWarning,
    onTertiary: Colors.white,
    tertiaryContainer: AppPalette.lightWarningSoft,
    onTertiaryContainer: AppPalette.lightWarning,
    error: AppPalette.lightDanger,
    onError: Colors.white,
    errorContainer: AppPalette.lightDangerSoft,
    onErrorContainer: AppPalette.lightDanger,
    surface: AppPalette.lightSurface,
    onSurface: AppPalette.lightTextPrimary,
    onSurfaceVariant: AppPalette.lightTextBody,
    outline: AppPalette.lightSubtleBorder,
    outlineVariant: AppPalette.lightDivider,
    shadow: Color(0xFF0F172A),
    scrim: Color(0xFF0F172A),
    inverseSurface: AppPalette.darkSurface,
    onInverseSurface: AppPalette.darkTextPrimary,
    inversePrimary: AppPalette.darkPrimary,
  );

  static const ColorScheme _darkColorScheme = ColorScheme(
    brightness: Brightness.dark,
    primary: AppPalette.darkPrimary,
    onPrimary: AppPalette.darkCanvas,
    primaryContainer: AppPalette.darkPrimarySoft,
    onPrimaryContainer: AppPalette.darkPrimary,
    secondary: AppPalette.darkSuccess,
    onSecondary: AppPalette.darkCanvas,
    secondaryContainer: AppPalette.darkSuccessSoft,
    onSecondaryContainer: AppPalette.darkSuccess,
    tertiary: AppPalette.darkWarning,
    onTertiary: AppPalette.darkCanvas,
    tertiaryContainer: AppPalette.darkWarningSoft,
    onTertiaryContainer: AppPalette.darkWarning,
    error: AppPalette.darkDanger,
    onError: AppPalette.darkCanvas,
    errorContainer: AppPalette.darkDangerSoft,
    onErrorContainer: AppPalette.darkDanger,
    surface: AppPalette.darkSurface,
    onSurface: AppPalette.darkTextPrimary,
    onSurfaceVariant: AppPalette.darkTextBody,
    outline: AppPalette.darkSubtleBorder,
    outlineVariant: AppPalette.darkDivider,
    shadow: Colors.black,
    scrim: Colors.black,
    inverseSurface: AppPalette.lightSurface,
    onInverseSurface: AppPalette.lightTextPrimary,
    inversePrimary: AppPalette.lightPrimary,
  );
}
