import 'package:flutter/material.dart';

/// Centralized YantraGO color palette.
///
/// Derived from docs/UI.svg and docs/mobile-ui-mockups/gallery.html.
/// These are raw tokens. Prefer [AppSemanticColors] and the Material
/// [ColorScheme] for component styling. Do not import this file from
/// feature widgets; access semantic colors through the active theme.
///
/// Light palette contrast was checked against white surfaces in
/// docs/YANTRAGO_Mobile_UI.md section 3.2. Dark counterparts are
/// intentional extensions (not SVG-derived) and must be validated
/// against dark surfaces before use in essential text.
@immutable
class AppPalette {
  const AppPalette._();

  // ---- Light surfaces -------------------------------------------------
  static const Color lightCanvas = Color(0xFFF8FAFC);
  static const Color lightSurface = Color(0xFFFFFFFF);
  static const Color lightSubtleBorder = Color(0xFFE2E8F0);
  static const Color lightDivider = Color(0xFFF1F5F9);

  // ---- Light text ----------------------------------------------------
  static const Color lightTextPrimary = Color(0xFF0F172A);
  static const Color lightTextBody = Color(0xFF334155);
  static const Color lightTextSecondary = Color(0xFF64748B);
  static const Color lightTextMuted = Color(0xFF94A3B8);

  // ---- Light semantic foregrounds (accessibility-adjusted) -----------
  static const Color lightPrimary = Color(0xFF2563EB);
  static const Color lightSuccess = Color(0xFF15803D);
  static const Color lightWarning = Color(0xFFB45309);
  static const Color lightDanger = Color(0xFFDC2626);
  static const Color lightNeutral = Color(0xFF64748B);

  // ---- Light semantic soft backgrounds -------------------------------
  static const Color lightPrimarySoft = Color(0xFFEFF6FF);
  static const Color lightSuccessSoft = Color(0xFFF0FDF4);
  static const Color lightWarningSoft = Color(0xFFFFFBEB);
  static const Color lightDangerSoft = Color(0xFFFEF2F2);
  static const Color lightNeutralSoft = Color(0xFFF1F5F9);

  // ---- Dark surfaces -------------------------------------------------
  static const Color darkCanvas = Color(0xFF0F172A);
  static const Color darkSurface = Color(0xFF1E293B);
  static const Color darkSubtleBorder = Color(0xFF475569);
  static const Color darkDivider = Color(0xFF334155);

  // ---- Dark text -----------------------------------------------------
  static const Color darkTextPrimary = Color(0xFFF8FAFC);
  static const Color darkTextBody = Color(0xFFE2E8F0);
  static const Color darkTextSecondary = Color(0xFFCBD5E1);
  static const Color darkTextMuted = Color(0xFF94A3B8);

  // ---- Dark semantic foregrounds ------------------------------------
  static const Color darkPrimary = Color(0xFF93C5FD);
  static const Color darkSuccess = Color(0xFF86EFAC);
  static const Color darkWarning = Color(0xFFFCD34D);
  static const Color darkDanger = Color(0xFFFCA5A5);
  static const Color darkNeutral = Color(0xFFCBD5E1);

  // ---- Dark semantic soft backgrounds --------------------------------
  static const Color darkPrimarySoft = Color(0xFF172C49);
  static const Color darkSuccessSoft = Color(0xFF163A2B);
  static const Color darkWarningSoft = Color(0xFF3B2E1D);
  static const Color darkDangerSoft = Color(0xFF40242E);
  static const Color darkNeutralSoft = Color(0xFF334155);
}
