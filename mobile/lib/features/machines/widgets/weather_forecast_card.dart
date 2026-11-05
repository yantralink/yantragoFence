import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/daily_forecast.dart';
import 'package:yantrago/models/weather.dart';

/// Data state for the forecast card — loading, ready rows, or an
/// honest unavailable state. No fabricated data is ever rendered.
enum WeatherForecastStatus { loading, ready, unavailable }

/// Expandable 5-day weather forecast card — one card, collapsed by
/// default, expanding into up to five vertical rows inside the same
/// card (docs/EXPANDABLE_5_DAY_WEATHER_FORECAST_PLAN.md §4).
///
/// Expansion is local widget state; toggling never triggers a fetch.
/// [today] is injectable so tests/goldens stay deterministic.
class WeatherForecastCard extends StatefulWidget {
  final WeatherForecastStatus status;
  final List<DailyForecast> days;
  final DateTime? today;

  const WeatherForecastCard({
    super.key,
    required this.status,
    this.days = const <DailyForecast>[],
    this.today,
  });

  @override
  State<WeatherForecastCard> createState() => _WeatherForecastCardState();
}

class _WeatherForecastCardState extends State<WeatherForecastCard> {
  static const Duration _animDuration = Duration(milliseconds: 275);
  bool _expanded = false;

  bool _isToday(DateTime date) {
    final now = widget.today ?? DateTime.now();
    return date.year == now.year &&
        date.month == now.month &&
        date.day == now.day;
  }

  String _dayLabel(BuildContext context, DailyForecast d) {
    final l10n = context.l10n;
    final locale = Localizations.localeOf(context).toString();
    if (_isToday(d.date)) return l10n.weatherToday;
    return DateFormat.EEEE(locale).format(d.date);
  }

  String _dateLabel(DailyForecast d) {
    final locale = Localizations.localeOf(context).toString();
    return DateFormat('d MMM', locale).format(d.date);
  }

  /// Header range derived from the actual visible rows — never a
  /// hardcoded "Today – Mon".
  String _rangeLabel(BuildContext context) {
    final days = widget.days;
    if (days.isEmpty) return '';
    final locale = Localizations.localeOf(context).toString();
    final l10n = context.l10n;
    String label(DailyForecast d) =>
        _isToday(d.date) ? l10n.weatherToday : DateFormat.E(locale).format(d.date);
    if (days.length == 1) return label(days.first);
    return '${label(days.first)} – ${label(days.last)}';
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final hasRows = widget.status == WeatherForecastStatus.ready &&
        widget.days.isNotEmpty;

    return AppSurfaceCard(
      padding: EdgeInsets.zero,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          // Full-width tappable header — the arrow is part of the same
          // hit area, never a separate target.
          Semantics(
            button: true,
            expanded: _expanded,
            label: l10n.weatherForecastTitle,
            child: InkWell(
              onTap: hasRows || widget.status == WeatherForecastStatus.loading
                  ? () => setState(() => _expanded = !_expanded)
                  : null,
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: AppSpacing.lg, vertical: AppSpacing.sm),
                  child: Row(
                    children: <Widget>[
                      Icon(Icons.calendar_month,
                          size: 20, color: colors.primary),
                      const SizedBox(width: AppSpacing.sm),
                      Expanded(
                        child: Text(
                          l10n.weatherForecastTitle,
                          style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.w700),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Flexible(child: _headerTrailing(context)),
                      const SizedBox(width: AppSpacing.xs),
                      AnimatedRotation(
                        turns: _expanded ? 0.5 : 0,
                        duration: _animDuration,
                        curve: Curves.easeInOut,
                        child: Icon(Icons.expand_more,
                            color: colors.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          ClipRect(
            child: AnimatedSize(
              duration: _animDuration,
              curve: Curves.easeInOut,
              alignment: Alignment.topCenter,
              child: !_expanded
                  ? const SizedBox(width: double.infinity)
                  : _expandedBody(context),
            ),
          ),
        ],
      ),
    );
  }

  Widget _headerTrailing(BuildContext context) {
    final l10n = context.l10n;
    final colors = Theme.of(context).colorScheme;
    switch (widget.status) {
      case WeatherForecastStatus.loading:
        return const SizedBox(
          width: 16,
          height: 16,
          child: CircularProgressIndicator(strokeWidth: 2),
        );
      case WeatherForecastStatus.unavailable:
        return Text(
          l10n.weatherForecastUnavailable,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: colors.onSurfaceVariant,
              ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        );
      case WeatherForecastStatus.ready:
        return Text(
          _rangeLabel(context),
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: colors.onSurfaceVariant,
              ),
        );
    }
  }

  Widget _expandedBody(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    if (widget.status == WeatherForecastStatus.loading) {
      return Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Text(
          l10n.weatherForecastLoading,
          style: theme.textTheme.bodySmall,
        ),
      );
    }
    if (widget.status == WeatherForecastStatus.unavailable ||
        widget.days.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Text(
          l10n.weatherForecastUnavailable,
          style: theme.textTheme.bodySmall,
        ),
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        for (var i = 0; i < widget.days.length; i++) ...<Widget>[
          if (i > 0)
            Divider(
              height: 1,
              color: theme.colorScheme.outlineVariant,
            ),
          _ForecastRow(
            day: widget.days[i],
            isToday: _isToday(widget.days[i].date),
            dayLabel: _dayLabel(context, widget.days[i]),
            dateLabel: _dateLabel(widget.days[i]),
          ),
        ],
      ],
    );
  }
}

class _ForecastRow extends StatelessWidget {
  final DailyForecast day;
  final bool isToday;
  final String dayLabel;
  final String dateLabel;

  const _ForecastRow({
    required this.day,
    required this.isToday,
    required this.dayLabel,
    required this.dateLabel,
  });

  String? _tempRange() {
    final min = day.tempMinCelsius;
    final max = day.tempMaxCelsius;
    if (min == null && max == null) return null;
    if (min == null) return '${max!.round()}°';
    if (max == null) return '${min.round()}°';
    return '${max.round()}° / ${min.round()}°'; // high / low
  }

  String _semanticsLabel(BuildContext context) {
    final l10n = context.l10n;
    final parts = <String>[
      '$dayLabel, $dateLabel',
      weatherForecastConditionLabel(l10n, day.conditionCode),
      if (_tempRange() != null)
        '${l10n.weatherForecastHigh} ${day.tempMaxCelsius?.round()}°C, '
            '${l10n.weatherForecastLow} ${day.tempMinCelsius?.round()}°C',
      if (day.isPartial) l10n.weatherForecastPartial,
    ];
    return parts.join('. ');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    final l10n = context.l10n;

    final row = Padding(
      padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.lg, vertical: AppSpacing.sm),
      child: Row(
        children: <Widget>[
          // Day + date (+ partial caption) on the left.
          SizedBox(
            width: 92,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  dayLabel,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  dateLabel,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
                ),
                if (day.isPartial)
                  Text(
                    l10n.weatherForecastPartial,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: colors.onSurfaceVariant,
                      fontStyle: FontStyle.italic,
                    ),
                  ),
              ],
            ),
          ),
          // Condition icon + text in the middle.
          Icon(
            weatherConditionIcon(day.conditionCode, isDay: day.isDay),
            size: 24,
            color: colors.primary,
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              weatherForecastConditionLabel(l10n, day.conditionCode),
              style: theme.textTheme.bodyMedium,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          // High / low on the right.
          if (_tempRange() != null)
            Text(
              _tempRange()!,
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w700,
              ),
            ),
        ],
      ),
    );

    return Semantics(
      label: _semanticsLabel(context),
      child: Container(
        color: isToday ? semantic?.tone(StatusTone.info) : null,
        child: row,
      ),
    );
  }
}
