import 'package:flutter/material.dart';

import '../theme/app_spacing.dart';

/// Shared label/value metadata row.
///
/// Presentation-only. Renders a label on the leading side and a value on the
/// trailing side. The value wraps when long; the row stacks vertically on
/// narrow widths when [stackWhenNarrow] is true (default). Missing values
/// are shown as a muted placeholder rather than blank space.
class AppMetadataRow extends StatelessWidget {
  final String label;
  final String? value;
  final String? placeholder;
  final bool stackWhenNarrow;
  final TextStyle? labelStyle;
  final TextStyle? valueStyle;

  const AppMetadataRow({
    super.key,
    required this.label,
    this.value,
    this.placeholder,
    this.stackWhenNarrow = true,
    this.labelStyle,
    this.valueStyle,
  });

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final bool hasValue = value != null && value!.isNotEmpty;
    final String display = hasValue ? value! : (placeholder ?? '—');

    final Widget labelWidget = Text(
      label,
      style: labelStyle ??
          text.labelMedium?.copyWith(color: colors.onSurfaceVariant),
    );
    final Widget valueWidget = Text(
      display,
      textAlign: TextAlign.end,
      style: valueStyle ??
          text.bodyMedium?.copyWith(
            color: hasValue ? colors.onSurface : colors.onSurfaceVariant,
          ),
    );

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final bool stack =
              stackWhenNarrow && constraints.maxWidth < 220;
          if (stack) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                labelWidget,
                const SizedBox(height: AppSpacing.xs),
                Align(alignment: Alignment.centerLeft, child: valueWidget),
              ],
            );
          }
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(child: labelWidget),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: valueWidget,
              ),
            ],
          );
        },
      ),
    );
  }
}
