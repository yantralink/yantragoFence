import 'package:flutter/material.dart';

import '../theme/app_spacing.dart';

/// Shared page body — consistent horizontal padding, safe areas, and
/// optional scrolling for screen content.
///
/// Presentation-only. Avoids duplicating `Padding(padding: EdgeInsets.all(16))`
/// across every screen. Does not introduce a navigation scaffold; screens
/// remain responsible for their own [Scaffold] and app bar.
class AppPageBody extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final bool scrollable;
  final bool safeArea;

  const AppPageBody({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.symmetric(horizontal: AppSpacing.pagePadding),
    this.scrollable = false,
    this.safeArea = true,
  });

  @override
  Widget build(BuildContext context) {
    Widget content = Padding(
      padding: padding,
      child: child,
    );
    if (scrollable) {
      content = SingleChildScrollView(child: content);
    }
    if (safeArea) {
      content = SafeArea(child: content);
    }
    return content;
  }
}
