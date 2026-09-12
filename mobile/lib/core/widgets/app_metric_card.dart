import 'package:flutter/material.dart';

import '../theme/app_radii.dart';
import '../theme/app_semantic_colors.dart';
import '../theme/app_spacing.dart';
import 'app_surface_card.dart';

/// Shared metric card — label, value, unit, status, and freshness.
///
/// Presentation-only. Distinguishes five states explicitly so feature
/// logic never misrepresents data:
/// - [AppMetricState.loading] — skeleton placeholder.
/// - [AppMetricState.available] — a confirmed reading.
/// - [AppMetricState.unavailable] — no report received (not zero).
/// - [AppMetricState.stale] — a past reading not confirmed fresh.
/// - [AppMetricState.error] — a fetch failure.
///
/// "Unavailable is not zero. Stale is not confirmed healthy."
class AppMetricCard extends StatelessWidget {
  final String label;
  final IconData? icon;
  final AppMetricState state;
  final String? value;
  final String? unit;
  final String? statusText;
  final StatusTone? statusTone;
  final String? freshness;

  const AppMetricCard({
    super.key,
    required this.label,
    required this.state,
    this.icon,
    this.value,
    this.unit,
    this.statusText,
    this.statusTone,
    this.freshness,
    this.compact = false,
  });

  final bool compact;

  @override
  Widget build(BuildContext context) {
    return AppSurfaceCard(
      padding: EdgeInsets.all(compact ? AppSpacing.sm : AppSpacing.tilePadding),
      child: _buildContent(context),
    );
  }

  Widget _buildContent(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;

    final Widget header = Row(
      children: <Widget>[
        Expanded(
          child: Text(
            label.toUpperCase(),
            overflow: TextOverflow.ellipsis,
            style: text.labelSmall?.copyWith(color: colors.onSurfaceVariant),
          ),
        ),
        if (icon != null)
          Icon(icon, size: 18, color: colors.onSurfaceVariant),
      ],
    );

    switch (state) {
      case AppMetricState.loading:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            header,
            const SizedBox(height: AppSpacing.sm),
            const _Skeleton(width: 90, height: 22),
            const SizedBox(height: AppSpacing.sm),
            const _Skeleton(width: 130, height: 12),
          ],
        );
      case AppMetricState.unavailable:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            header,
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Unavailable',
              style: text.titleMedium?.copyWith(color: colors.onSurfaceVariant),
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              statusText ?? 'No report received',
              style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
            ),
          ],
        );
      case AppMetricState.stale:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            header,
            const SizedBox(height: AppSpacing.sm),
            _ValueLine(value: value, unit: unit, dimmed: true),
            const SizedBox(height: AppSpacing.xs),
            _StatusLine(
              text: statusText ?? 'Stale reading',
              tone: StatusTone.warning,
              icon: Icons.history,
            ),
            if (freshness != null) _Freshness(text: freshness!),
          ],
        );
      case AppMetricState.error:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            header,
            const SizedBox(height: AppSpacing.sm),
            _ValueLine(value: value ?? '—', unit: unit, dimmed: true),
            const SizedBox(height: AppSpacing.xs),
            _StatusLine(
              text: statusText ?? 'Unable to load',
              tone: StatusTone.danger,
              icon: Icons.error_outline,
            ),
          ],
        );
      case AppMetricState.available:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            header,
            const SizedBox(height: AppSpacing.sm),
            _ValueLine(value: value, unit: unit),
            const SizedBox(height: AppSpacing.xs),
            if (statusText != null)
              _StatusLine(
                text: statusText!,
                tone: statusTone ?? StatusTone.neutral,
                icon: _toneIcon(statusTone),
              ),
            if (freshness != null) _Freshness(text: freshness!),
          ],
        );
    }
  }

  IconData _toneIcon(StatusTone? tone) {
    switch (tone) {
      case StatusTone.success:
        return Icons.check_circle_outline;
      case StatusTone.warning:
        return Icons.warning_amber_outlined;
      case StatusTone.danger:
        return Icons.error_outline;
      case StatusTone.info:
        return Icons.info_outline;
      case StatusTone.neutral:
      case null:
        return Icons.schedule;
    }
  }
}

/// Metric data state chosen by feature logic.
enum AppMetricState { loading, available, unavailable, stale, error }

class _ValueLine extends StatelessWidget {
  final String? value;
  final String? unit;
  final bool dimmed;
  const _ValueLine({this.value, this.unit, this.dimmed = false});

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final Color color = dimmed
        ? Theme.of(context).colorScheme.onSurfaceVariant
        : Theme.of(context).colorScheme.onSurface;
    final String display = value ?? '—';
    if (unit == null || unit!.isEmpty) {
      return Text(
        display,
        style: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700, color: color),
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.baseline,
      textBaseline: TextBaseline.alphabetic,
      children: <Widget>[
        Flexible(
          child: Text(
            display,
            overflow: TextOverflow.ellipsis,
            style: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700, color: color),
          ),
        ),
        const SizedBox(width: 4),
        Text(unit!, style: text.bodyMedium?.copyWith(color: color)),
      ],
    );
  }
}

class _StatusLine extends StatelessWidget {
  final String text;
  final StatusTone tone;
  final IconData icon;
  const _StatusLine({required this.text, required this.tone, required this.icon});

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    final Color foreground = semantic?.tone(tone) ?? Theme.of(context).colorScheme.primary;
    final TextTheme textTheme = Theme.of(context).textTheme;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Icon(icon, size: 14, color: foreground),
        const SizedBox(width: AppSpacing.xs),
        Flexible(
          child: Text(
            text,
            overflow: TextOverflow.ellipsis,
            style: textTheme.bodySmall?.copyWith(color: foreground),
          ),
        ),
      ],
    );
  }
}

class _Freshness extends StatelessWidget {
  final String text;
  const _Freshness({required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.xs),
      child: Text(
        text,
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
      ),
    );
  }
}

class _Skeleton extends StatelessWidget {
  final double width;
  final double height;
  const _Skeleton({required this.width, required this.height});

  @override
  Widget build(BuildContext context) {
    final Color base = Theme.of(context).colorScheme.onSurfaceVariant;
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: base.withValues(alpha: 0.18),
        borderRadius: AppRadii.chipRadius,
      ),
    );
  }
}
