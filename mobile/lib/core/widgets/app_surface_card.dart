import 'package:flutter/material.dart';

import '../theme/app_radii.dart';
import '../theme/app_spacing.dart';

/// Shared surface card — white bordered surface with the documented radius.
///
/// Presentation-only. Wraps Material [Card] with the centralized theme
/// (zero elevation, subtle border, [AppRadii.card]). Use this instead of
/// raw [Card] + [Padding] duplication across features.
///
/// Pass [onTap] for a tappable card (Material ink + accessibility semantics
/// are preserved). Leave [onTap] null for a static surface.
class AppSurfaceCard extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry padding;
  final CrossAxisAlignment crossAxisAlignment;

  const AppSurfaceCard({
    super.key,
    required this.child,
    this.onTap,
    this.padding = const EdgeInsets.all(AppSpacing.cardPadding),
    this.crossAxisAlignment = CrossAxisAlignment.start,
  });

  @override
  Widget build(BuildContext context) {
    final CardThemeData cardTheme = Theme.of(context).cardTheme;
    final ShapeBorder shape = cardTheme.shape ?? const RoundedRectangleBorder(borderRadius: AppRadii.cardRadius);

    final Widget content = Padding(
      padding: padding,
      child: Column(
        crossAxisAlignment: crossAxisAlignment,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[child],
      ),
    );

    if (onTap == null) {
      return Card(shape: shape, child: content);
    }

    return Card(
      shape: shape,
      child: InkWell(
        onTap: onTap,
        borderRadius: AppRadii.cardRadius,
        child: content,
      ),
    );
  }
}
