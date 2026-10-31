import 'package:flutter/material.dart';

/// Decorative machine-card colors, independent of alarm/connectivity status.
/// Other surfaces continue to use the global CardTheme.
@immutable
class MachineCardTheme extends ThemeExtension<MachineCardTheme> {
  final Color background;
  final Color border;
  final Color accent;
  final Color shadow;

  const MachineCardTheme({
    required this.background,
    required this.border,
    required this.accent,
    required this.shadow,
  });

  static const light = MachineCardTheme(
    background: Color(0xFFFFF5F5),
    border: Color(0xFFF5B5B8),
    accent: Color(0xFFEF4444),
    shadow: Color(0x1AEF4444),
  );

  static const dark = MachineCardTheme(
    background: Color(0xFF30232B),
    border: Color(0xFF88525C),
    accent: Color(0xFFF87171),
    shadow: Color(0x33000000),
  );

  static MachineCardTheme of(BuildContext context) {
    final theme = Theme.of(context);
    return theme.extension<MachineCardTheme>() ??
        (theme.brightness == Brightness.dark ? dark : light);
  }

  @override
  MachineCardTheme copyWith({
    Color? background,
    Color? border,
    Color? accent,
    Color? shadow,
  }) => MachineCardTheme(
    background: background ?? this.background,
    border: border ?? this.border,
    accent: accent ?? this.accent,
    shadow: shadow ?? this.shadow,
  );

  @override
  MachineCardTheme lerp(covariant MachineCardTheme? other, double t) {
    if (other == null) return this;
    return MachineCardTheme(
      background: Color.lerp(background, other.background, t)!,
      border: Color.lerp(border, other.border, t)!,
      accent: Color.lerp(accent, other.accent, t)!,
      shadow: Color.lerp(shadow, other.shadow, t)!,
    );
  }
}
