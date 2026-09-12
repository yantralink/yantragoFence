/// Centralized YantraGO spacing scale.
///
/// Use these tokens instead of magic numbers. Values are logical pixels.
/// Source: docs/YANTRAGO_Mobile_UI.md section 3.4.
class AppSpacing {
  AppSpacing._();

  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;

  /// Standard page horizontal padding.
  static const double pagePadding = lg;

  /// Standard card internal padding.
  static const double cardPadding = lg;

  /// Compact tile internal padding.
  static const double tilePadding = md;

  /// Gap between stacked cards.
  static const double cardGap = lg;

  /// Gap between grid cells.
  static const double gridGap = md;

  /// Minimum height for primary action buttons.
  static const double buttonMinHeight = 50;

  /// Minimum interactive target size (width and height).
  static const double minTouchTarget = 48;
}
